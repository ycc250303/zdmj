package com.zdmj.conversationService.service.impl;

import com.zdmj.common.context.CurrentActor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.PageRequests;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.conversationService.dto.ChatStreamRequest;
import com.zdmj.conversationService.dto.MessageResponse;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.entity.Message;
import com.zdmj.conversationService.enums.MessageRoleEnum;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.mapper.MessageMapper;
import com.zdmj.conversationService.service.MessageService;
import com.zdmj.conversationService.dto.ConversationRequest;
import com.zdmj.knowledgeService.service.KnowledgeRagService;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.aiService.api.ModelRequest.HistoryMessage;
import com.zdmj.aiService.api.ModelRequest.HistoryMessage.Role;
import com.zdmj.conversationService.enums.MessageGenerationStatusEnum;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import reactor.core.publisher.Mono;

import lombok.RequiredArgsConstructor;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 消息 Service 实现类
 */
@RequiredArgsConstructor
@Service
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final ConversationServiceImpl conversationService;
    private final ConversationMapper conversationMapper;
    private final KnowledgeRagService knowledgeRagService;
    private final TransactionTemplate transactionTemplate;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 校验归属，在会话行锁保护的短事务内读取最近20轮历史并建立本轮问答。
     * 模型调用在事务外执行；完成、失败和取消分别保存终态及已接收内容。
     *
     * @param actor 已认证操作者
     * @param request 会话编号和当前问题，当前问题仅由模型网关追加一次
     * @return 保持现有格式的 SSE 增量事件
     * @throws BusinessException 会话不存在、无权访问、正在生成或写入失败
     */
    @Override
    public Flux<ServerSentEvent<String>> createStream(CurrentActor actor, ChatStreamRequest request) {
        conversationService.requireOwned(actor, request.conversationId());
        PreparedMessages prepared = transactionTemplate.execute(status -> insertMessages(request, actor.userId()));
        if (prepared == null) {
            throw new IllegalStateException("消息写入未返回结果");
        }
        subscribeAnswer(actor, prepared);
        return prepared.sink().asFlux()
                .doOnCancel(() -> {
                    Disposable upstream = prepared.upstream().get();
                    if (upstream != null) {
                        upstream.dispose();
                    }
                    finishAssistant(prepared, MessageGenerationStatusEnum.CANCELLED);
                })
                .index()
                .map(tp -> ServerSentEvent.<String>builder()
                        .event("delta")
                        .id(String.valueOf(tp.getT1() + 1))
                        .data(toOpenAiDeltaJson(tp.getT2()))
                        .build());
    }

    /** 锁定会话后读取历史并建立关联问答，所有写入共同提交或回滚。 */
    private PreparedMessages insertMessages(ChatStreamRequest request, Long userId) {
        // 1.获取会话
        Conversation conversation = conversationMapper.selectOwnedForUpdate(request.conversationId(), userId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (messageMapper.hasProcessing(userId, request.conversationId())) {
            throw new BusinessException(ErrorCode.CONVERSATION_GENERATING);
        }
        // 2.获取最近20条对话消息记录
        List<Message> history = messageMapper.selectContextHistory(userId, request.conversationId(), 20);
        List<HistoryMessage> input = new ArrayList<>();
        StringBuilder notice = new StringBuilder();
        // 3.遍历历史消息记录，构建输入和提示
        for (Message message : history) {
            if (message.getRole() == MessageRoleEnum.USER.getCode()) {
                input.add(new HistoryMessage(Role.USER, message.getContent()));
            } else {
                var state = MessageGenerationStatusEnum.fromCode(message.getGenerationStatus());
                if (state == MessageGenerationStatusEnum.PROCESSING) {
                    throw new IllegalStateException("历史回复仍在生成");
                }
                if (!message.getContent().isBlank()) {
                    input.add(new HistoryMessage(Role.ASSISTANT, message.getContent()));
                }
                if (state != MessageGenerationStatusEnum.COMPLETED) {
                    String description = switch (state) {
                        case FAILED -> "生成失败";
                        case CANCELLED -> "生成已取消";
                        case LEGACY_UNKNOWN -> "完整性未知";
                        default -> throw new IllegalStateException("不支持的历史状态");
                    };
                    notice.append("序号").append(message.getSequence()).append("的助手回复")
                            .append(description).append("，问题和已有内容已保留，请结合上下文继续。\n");
                }
            }
        }
        // 4.更新会话消息计数
        Integer newCount = conversationMapper.incrementMessageCountAndGet(request.conversationId(), userId, 2);
        if (newCount == null || newCount < 2) {
            throw new IllegalStateException("锁定会话后消息计数必须更新成功");
        }
        // 5.插入当前问题消息
        Message question = new Message();
        question.setConversationId(request.conversationId());
        question.setUserId(userId);
        question.setRole(MessageRoleEnum.USER.getCode());
        question.setContent(request.message());
        question.setSequence(newCount - 1);
        insertMessage(question);

        // 6.插入助手回复消息
        Message answer = new Message();
        answer.setConversationId(request.conversationId());
        answer.setUserId(userId);
        answer.setRole(MessageRoleEnum.ASSISTANT.getCode());
        answer.setContent("");
        answer.setSequence(newCount);
        answer.setReplyToMessageId(question.getId());
        answer.setGenerationStatus(MessageGenerationStatusEnum.PROCESSING.getCode());
        insertMessage(answer);

        // 7.更新会话标题
        if (newCount == 2) {
            String title = request.message().strip().replaceAll("(?U)\\s+", " ");
            if (title.codePointCount(0, title.length()) > 30) {
                title = title.substring(0, title.offsetByCodePoints(0, 30)) + "…";
            }
            if (conversationMapper.updateTitleByIdAndUserId(request.conversationId(), userId, title) != 1) {
                throw new IllegalStateException("首次会话标题必须更新一条记录");
            }
        }
        // 8.返回准备好的消息
        return new PreparedMessages(answer, conversation,
                new ModelRequest(request.message(), null, null, input, notice.toString()),
                Sinks.many().unicast().onBackpressureBuffer(), new StringBuilder(256), new AtomicReference<>());
    }

    private void insertMessage(Message message) {
        if (messageMapper.insert(message) != 1) {
            throw new BusinessException(ErrorCode.MESSAGE_CREATE_FAILED);
        }
        if (message.getId() == null) {
            throw new IllegalStateException("消息写入后必须回填编号");
        }
    }

    /** 
     * 订阅助手回复
     * @param actor 已认证操作者
     * @param prepared 准备好的消息
     */
    private void subscribeAnswer(CurrentActor actor, PreparedMessages prepared) {
        // 1. 获取会话
        Conversation conversation = prepared.conversation();
        // 2. 订阅助手回复
        Disposable upstream = Flux.defer(() -> {
                    ConversationRequest config = new ConversationRequest(conversation.getConfig());
                    List<Long> documentIds;
                    boolean useSystemKnowledge;
                    try {
                        documentIds = config.ragDocumentIds();
                        useSystemKnowledge = config.useSystemKnowledge();
                    } catch (IllegalArgumentException exception) {
                        throw new IllegalStateException("会话检索配置非法，conversationId=" + conversation.getId(), exception);
                    }
                    return knowledgeRagService.streamAnswer(actor, conversation.getId(), prepared.request(),
                            documentIds, useSystemKnowledge);
                })
                // 3. 处理助手回复
                .doOnNext(chunk -> {
                    // 4.锁定会话，防止并发修改
                    synchronized (prepared) {
                        if (prepared.assistantMsg().getGenerationStatus() == MessageGenerationStatusEnum.PROCESSING.getCode()
                                && !chunk.isEmpty()) {
                            prepared.full().append(chunk);
                            prepared.sink().tryEmitNext(chunk);
                        }
                    }
                })
                // 5. 处理异常
                .onErrorResume(failure -> {
                    try {
                        finishAssistant(prepared, MessageGenerationStatusEnum.FAILED);
                    } catch (RuntimeException persistenceFailure) {
                        persistenceFailure.addSuppressed(failure);
                        return Flux.error(persistenceFailure);
                    }
                    return Flux.error(failure);
                })
                .concatWith(Mono.fromRunnable(() -> finishAssistant(prepared, MessageGenerationStatusEnum.COMPLETED)))
                .subscribe(chunk -> { }, prepared.sink()::tryEmitError, prepared.sink()::tryEmitComplete);
        prepared.upstream().set(upstream);
    }

    /**
     * 在会话行锁保护的短事务中保存内容及终态，提交成功后更新内存状态。
     * 已删除会话结束本地处理；写入或提交失败时保留生成中状态并传播异常。
     *
     * @param prepared 本轮消息、已接收内容及流控制信息
     * @param state 本次结束对应的完成、失败或取消状态
     * @throws IllegalStateException 必须存在的回复未更新一条记录，或事务未返回结果
     */
    private void finishAssistant(PreparedMessages prepared, MessageGenerationStatusEnum state) {
        synchronized (prepared) {
            Message answer = prepared.assistantMsg();
            if (answer.getGenerationStatus() != MessageGenerationStatusEnum.PROCESSING.getCode()) {
                return;
            }
            Integer updated = transactionTemplate.execute(status -> {
                if (conversationMapper.selectOwnedForUpdate(answer.getConversationId(), answer.getUserId()) == null) {
                    return 0;
                }
                int rows = messageMapper.finishAssistant(answer.getId(), answer.getUserId(), answer.getConversationId(),
                        prepared.full().toString(), state.getCode());
                if (rows != 1) {
                    throw new IllegalStateException("生成中的助手回复必须更新一条记录");
                }
                return rows;
            });
            if (updated == null) {
                throw new IllegalStateException("助手回复保存事务未返回结果");
            }
            answer.setGenerationStatus(state.getCode());
        }
    }

    /** 单实例服务就绪前恢复中断的生成；数据库异常阻止启动。 */
    @EventListener(ApplicationStartedEvent.class)
    public void recoverInterruptedGenerations() {
        transactionTemplate.executeWithoutResult(status -> messageMapper.recoverInterrupted());
    }

    /** 准备好的消息 
     * 
     * @param assistantMsg 助手回复消息
     * @param conversation 会话
     * @param request 模型请求
     * @param sink 消息Sink
     * @param full 完整消息
     * @param upstream 上游
     */
    private record PreparedMessages(
        Message assistantMsg,
        Conversation conversation,
        ModelRequest request,
        Sinks.Many<String> sink,
        StringBuilder full,
        AtomicReference<Disposable> upstream
    ) {}

    /**
     * 获取会话消息列表
     *
     * @param actor          已认证操作者，包含用户编号
     * @param conversationId 会话 ID
     * @param page           页码
     * @param limit          每页条数
     * @return 消息列表
     */
    @Override
    public PageDTO<MessageResponse> getMessagesByConversationId(CurrentActor actor, Long conversationId, Integer page,
            Integer limit) {
        conversationService.requireOwned(actor, conversationId);
        PageRequests.Normalized paging = PageRequests.normalize(page, limit);
        var mpPage = messageMapper.selectPageByConversationId(PageRequests.toPage(paging), conversationId);
        List<MessageResponse> list = mpPage.getRecords().stream().map(this::convertToResponse).toList();
        return PageDTO.from(mpPage, list);
    }

    /**
     * 生成 OpenAI 兼容的 delta JSON 片段，便于前端合并。
     * 格式示例：{"choices":[{"delta":{"content":"你"}}]}
     */
    private String toOpenAiDeltaJson(String content) {
        try {
            return OBJECT_MAPPER.writeValueAsString(
                    java.util.Map.of(
                            "choices",
                            java.util.List.of(
                                    java.util.Map.of(
                                            "delta",
                                            java.util.Map.of("content", content)))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("流式增量无法序列化", e);
        }
    }

    private MessageResponse convertToResponse(Message message) {
        return new MessageResponse(message.getId(), message.getConversationId(),
                message.getUserId(), message.getRole(), message.getContent(), message.getSequence(),
                message.getCreatedAt());
    }
}
