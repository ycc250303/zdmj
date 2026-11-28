package com.zdmj.conversationService.service.impl;

import com.zdmj.conversationService.dto.PreparedConversationMessages;
import com.zdmj.conversationService.dto.ConversationGeneration;
import com.zdmj.common.context.TraceIdAccessor;
import com.zdmj.conversationService.dto.ConversationDeletedEvent;
import com.zdmj.common.model.PageParameters;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.aiService.api.ModelHistoryMessage;
import com.zdmj.aiService.api.ModelMessageRole;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.PageRequests;
import com.zdmj.conversationService.dto.ChatStreamRequest;
import com.zdmj.conversationService.dto.ConversationRequest;
import com.zdmj.conversationService.dto.MessageResponse;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.entity.Message;
import com.zdmj.conversationService.enums.MessageGenerationStatusEnum;
import com.zdmj.conversationService.enums.MessageRoleEnum;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.mapper.MessageMapper;
import com.zdmj.conversationService.service.MessageService;
import com.zdmj.knowledgeService.api.KnowledgeRagService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionalEventListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 会话消息及生成生命周期，封装准备、检索、流式回复与一次结束保存。
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final ConversationServiceImpl conversationService;
    private final ConversationMapper conversationMapper;
    private final KnowledgeRagService knowledgeRagService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final ConcurrentMap<Long, ConversationGeneration> generations = new ConcurrentHashMap<>();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 返回流式消息
     *
     * @param actor   已认证操作者
     * @param request 会话编号和当前问题，当前问题仅由模型网关追加一次
     * @return 保持现有格式的 SSE 增量事件
     * @throws BusinessException 会话不存在、无权访问、正在生成或写入失败
     */
    @Override
    public Flux<ServerSentEvent<String>> createStream(CurrentActor actor, ChatStreamRequest request) {
        return Flux.deferContextual(context -> {
            ConversationGeneration generation = new ConversationGeneration(actor, request.conversationId(),
                    context.getOrDefault(TraceIdAccessor.KEY, null));
            log.info("会话生成开始 conversationId={}", generation.getConversationId());
            return Flux.usingWhen(
                    Mono.just(generation),
                    current -> prepareAnswer(current, request),
                    current -> finishAssistant(current, MessageGenerationStatusEnum.COMPLETED, null),
                    (current, failure) -> finishAssistant(current, MessageGenerationStatusEnum.FAILED, failure),
                    current -> {
                        current.setCancelled(true);
                        return finishAssistant(current, MessageGenerationStatusEnum.CANCELLED, null);
                    })
                    .onErrorMap(failure -> generation.getPersistenceFailure() != null
                            && failure.getCause() == generation.getPersistenceFailure()
                                    ? generation.getPersistenceFailure()
                                    : failure);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 准备回答
     * 
     * @param generation 本轮生成
     * @param request    会话编号和当前问题，当前问题仅由模型网关追加一次
     * @return 保持现有格式的 SSE 增量事件
     */
    private Flux<ServerSentEvent<String>> prepareAnswer(ConversationGeneration generation, ChatStreamRequest request) {
        // 1.锁住本轮会话生成
        synchronized (generation) {
            // 2.如果本轮生成已取消，则返回空
            if (generation.isCancelled()) {
                return Flux.empty();
            }
            // 3.检查会话归属
            conversationService.requireOwned(generation.getActor(), request.conversationId());
            // 4.如果本轮会话生成已存在，则抛出异常
            if (generations.putIfAbsent(generation.getConversationId(), generation) != null) {
                throw new BusinessException(ErrorCode.CONVERSATION_GENERATING);
            }
            // 5.插入消息
            generation.setPrepared(transactionTemplate
                    .execute(status -> insertMessages(request, generation.getActor().userId())));
            // 6.如果本轮会话生成未返回结果，则抛出异常
            if (generation.getPrepared() == null) {
                throw new IllegalStateException("消息写入未返回结果");
            }
            log.info("会话消息准备完成 conversationId={} messageId={}",
                    generation.getConversationId(),
                    generation.getPrepared().assistantMsg().getId());
        }
        // 7.返回本轮会话生成的流式消息
        return Flux.defer(() -> {
            PreparedConversationMessages prepared = generation.getPrepared();
            ConversationRequest config = new ConversationRequest(prepared.conversation().getConfig());
            List<Long> documentIds;
            boolean useSystemKnowledge;
            try {
                documentIds = config.ragDocumentIds();
                useSystemKnowledge = config.useSystemKnowledge();
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("会话检索配置非法，conversationId=" + generation.getConversationId(), exception);
            }
            return knowledgeRagService.streamAnswer(
                    generation.getActor(),
                    generation.getConversationId(),
                    prepared.request(),
                    documentIds,
                    useSystemKnowledge);
        })
                .takeUntilOther(Mono.fromFuture(generation.getDeletion(), true))
                .<String>handle((chunk, sink) -> {
                    synchronized (generation) {
                        if (!generation.isCancelled() && !generation.getDeletion().isDone() && !chunk.isEmpty()) {
                            generation.getFull().append(chunk);
                            sink.next(chunk);
                        }
                    }
                })
                .index()
                .map(tp -> ServerSentEvent.<String>builder()
                        .event("delta")
                        .id(String.valueOf(tp.getT1() + 1))
                        .data(toOpenAiDeltaJson(tp.getT2()))
                        .build());
    }

    /** 锁定会话后读取历史并建立关联问答，所有写入共同提交或回滚。 */
    private PreparedConversationMessages insertMessages(ChatStreamRequest request, Long userId) {
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
        List<ModelHistoryMessage> input = new ArrayList<>();
        StringBuilder notice = new StringBuilder();
        // 3.遍历历史消息记录，构建输入和提示
        for (Message message : history) {
            if (message.getRole() == MessageRoleEnum.USER.getCode()) {
                input.add(new ModelHistoryMessage(ModelMessageRole.USER, message.getContent()));
            } else {
                var state = MessageGenerationStatusEnum.fromCode(message.getGenerationStatus());
                if (state == MessageGenerationStatusEnum.PROCESSING) {
                    throw new IllegalStateException("历史回复仍在生成");
                }
                if (!message.getContent().isBlank()) {
                    input.add(new ModelHistoryMessage(ModelMessageRole.ASSISTANT, message.getContent()));
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
        LocalDateTime now = LocalDateTime.now(clock);
        Integer newCount = conversationMapper.incrementMessageCountAndGet(request.conversationId(), userId, 2, now);
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
            if (conversationMapper.updateTitleByIdAndUserId(request.conversationId(), userId, title, now) != 1) {
                throw new IllegalStateException("首次会话标题必须更新一条记录");
            }
        }
        // 8.返回准备好的消息
        return new PreparedConversationMessages(
                answer,
                conversation,
                new ModelRequest(
                        request.message(),
                        null,
                        null,
                        input,
                        notice.toString()));
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
     * 完成、失败与取消共用一次结束保存；提交后才更新内存终态。
     * 结束保存作为资源清理执行，开始后继续提交；保存失败保留登记和内容，原始错误继续传播。
     * 客户端取消后的保存错误由服务端记录。
     *
     * @param generation    本轮生成及已接收内容
     * @param state         目标终态
     * @param sourceFailure 检索或模型异常，保存也失败时保留为 suppressed 原因
     * @return 在独立线程执行的短事务保存
     */
    private Mono<Void> finishAssistant(ConversationGeneration generation, MessageGenerationStatusEnum state,
            Throwable sourceFailure) {
        return Mono.<Void>fromRunnable(() -> {
            synchronized (generation) {
                if (generation.getPrepared() == null || generation.getDeletion().isDone()) {
                    generations.remove(generation.getConversationId(), generation);
                    log.info("会话生成结束 conversationId={} reason={} elapsedMs={}",
                            generation.getConversationId(),
                            generation.getDeletion().isDone() ? "DELETED" : "UNPREPARED",
                            (System.nanoTime() - generation.getStartedNanos()) / 1_000_000);
                    return;
                }
                Message answer = generation.getPrepared().assistantMsg();
                try {
                    Boolean saved = transactionTemplate.execute(status -> {
                        if (conversationMapper.selectOwnedForUpdate(answer.getConversationId(),
                                answer.getUserId()) == null) {
                            return false;
                        }
                        int rows = messageMapper.finishAssistant(answer.getId(), answer.getUserId(),
                                answer.getConversationId(),
                                generation.getFull().toString(), state.getCode());
                        if (rows != 1) {
                            throw new IllegalStateException("生成中的助手回复必须更新一条记录");
                        }
                        return true;
                    });
                    if (saved == null) {
                        throw new IllegalStateException("结束保存事务未返回结果");
                    }
                    answer.setGenerationStatus(state.getCode());
                    generations.remove(generation.getConversationId(), generation);
                    if (saved) {
                        log.info("会话生成保存完成 conversationId={} messageId={} status={} elapsedMs={}",
                                generation.getConversationId(), answer.getId(), state.name(),
                                (System.nanoTime() - generation.getStartedNanos()) / 1_000_000);
                    } else {
                        log.info("会话生成结束 conversationId={} reason=DELETED elapsedMs={}",
                                generation.getConversationId(),
                                (System.nanoTime() - generation.getStartedNanos()) / 1_000_000);
                    }
                } catch (RuntimeException failure) {
                    generation.setPersistenceFailure(failure);
                    if (sourceFailure != null && sourceFailure != failure) {
                        failure.addSuppressed(sourceFailure);
                    }
                    log.error("会话结束保存失败 conversationId={} messageId={} targetStatus={}",
                            generation.getConversationId(), answer.getId(), state.getCode(),
                            failure);
                    throw failure;
                }
            }
        }).subscribeOn(Schedulers.boundedElastic())
                .contextWrite(context -> generation.getTraceId() == null
                        ? context.delete(TraceIdAccessor.KEY)
                        : context.put(TraceIdAccessor.KEY, generation.getTraceId()));
    }

    /** 删除提交后取消对应生成；回滚时监听器不执行，通知不访问数据库。 */
    @TransactionalEventListener
    public void cancelDeletedConversation(ConversationDeletedEvent event) {
        ConversationGeneration generation = generations.remove(event.conversationId());
        if (generation != null) {
            generation.getDeletion().complete(null);
        }
    }

    /** 单实例服务就绪前恢复中断的生成；数据库异常阻止启动。 */
    @EventListener(ApplicationStartedEvent.class)
    public void recoverInterruptedGenerations() {
        transactionTemplate.executeWithoutResult(status -> messageMapper.recoverInterrupted());
    }

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
        PageParameters paging = PageRequests.normalize(page, limit);
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
