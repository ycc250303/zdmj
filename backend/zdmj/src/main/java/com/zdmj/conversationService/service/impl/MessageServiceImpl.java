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
import com.zdmj.conversationService.support.ConversationContextSupport;
import com.zdmj.knowledgeService.service.KnowledgeRagService;

import lombok.RequiredArgsConstructor;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
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
     * 先在短事务中写入消息、首条消息标题并提交，再订阅模型流。
     * 流正常结束、失败或客户端中断时，另以短事务回写助手消息内容。
     * 失败仍通过流错误向下游传递，消息表没有单独的状态列。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param request 会话编号与用户消息
     * @return SSE 增量事件
     * @throws BusinessException 会话不存在、无权访问或消息写入失败
     */
    @Override
    public Flux<ServerSentEvent<String>> createStream(CurrentActor actor, ChatStreamRequest request) {
        Conversation conversation = conversationService.requireOwned(actor, request.getConversationId());
        Long userId = actor.userId();
        PreparedMessages prepared = transactionTemplate.execute(status -> insertMessages(request, userId));
        if (prepared == null) {
            throw new IllegalStateException("消息写入未返回结果");
        }
        subscribeAnswer(conversation, actor, request, prepared);
        return prepared.sink().asFlux()
                .doOnCancel(() -> {
                    try {
                        persistAssistantContent(prepared.assistantMsg(), prepared.full().toString());
                    } finally {
                        Disposable upstream = prepared.upstream().get();
                        if (upstream != null) {
                            upstream.dispose();
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

    private PreparedMessages insertMessages(ChatStreamRequest request, Long userId) {
        Integer newCount = conversationMapper.incrementMessageCountAndGet(request.getConversationId(), userId, 2);
        if (newCount == null || newCount < 2) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        int userSeq = newCount - 1;
        int assistantSeq = newCount;

        Message userMsg = new Message();
        userMsg.setConversationId(request.getConversationId());
        userMsg.setUserId(userId);
        userMsg.setRole(MessageRoleEnum.USER.getCode());
        userMsg.setContent(request.getMessage());
        userMsg.setSequence(userSeq);
        if (messageMapper.insert(userMsg) != 1) {
            throw new BusinessException(ErrorCode.MESSAGE_CREATE_FAILED);
        }

        Message assistantMsg = new Message();
        assistantMsg.setConversationId(request.getConversationId());
        assistantMsg.setUserId(userId);
        assistantMsg.setRole(MessageRoleEnum.ASSISTANT.getCode());
        assistantMsg.setContent("");
        assistantMsg.setSequence(assistantSeq);
        if (messageMapper.insert(assistantMsg) != 1) {
            throw new BusinessException(ErrorCode.MESSAGE_CREATE_FAILED);
        }
        if (newCount == 2) {
            String title = request.getMessage().strip().replaceAll("(?U)\\s+", " ");
            if (title.codePointCount(0, title.length()) > 30) {
                title = title.substring(0, title.offsetByCodePoints(0, 30)) + "…";
            }
            if (conversationMapper.updateTitleByIdAndUserId(request.getConversationId(), userId, title) != 1) {
                throw new IllegalStateException("首次会话标题必须更新一条记录");
            }
        }
        return new PreparedMessages(assistantMsg, Sinks.many().unicast().onBackpressureBuffer(),
                new StringBuilder(256), new AtomicReference<>());
    }

    private void subscribeAnswer(Conversation conversation, CurrentActor actor, ChatStreamRequest request,
            PreparedMessages prepared) {
        List<Long> ragDocumentIds = ConversationContextSupport.resolveRagDocumentIds(conversation);
        boolean useSystemKnowledge = ConversationContextSupport.resolveUseSystemKnowledge(conversation);
        Flux<String> chatFlux = knowledgeRagService.streamAnswer(
                actor,
                request.getConversationId(),
                request.getMessage(),
                ragDocumentIds,
                useSystemKnowledge,
                null);
        Disposable upstream = chatFlux.doOnNext(chunk -> {
            if (chunk == null || chunk.isEmpty()) {
                return;
            }
            prepared.full().append(chunk);
            prepared.sink().tryEmitNext(chunk);
        })
                .doOnError(e -> {
                    persistAssistantContent(prepared.assistantMsg(), prepared.full().toString());
                    prepared.sink().tryEmitError(e);
                })
                .doOnComplete(() -> {
                    if (persistAssistantContent(prepared.assistantMsg(), prepared.full().toString()) != 1) {
                        prepared.sink().tryEmitError(new RuntimeException("assistant message persist failed"));
                        return;
                    }
                    prepared.sink().tryEmitComplete();
                })
                .subscribe();
        prepared.upstream().set(upstream);
    }

    private record PreparedMessages(Message assistantMsg, Sinks.Many<String> sink, StringBuilder full,
            AtomicReference<Disposable> upstream) {
    }

    /**
     * 获取会话消息列表
     * @param actor 已认证操作者，包含用户编号
     * @param conversationId 会话 ID
     * @param page 页码
     * @param limit 每页条数
     * @return 消息列表
     */
    @Override
    public PageDTO<MessageResponse> getMessagesByConversationId(CurrentActor actor, Long conversationId, Integer page, Integer limit) {
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

    /**
     * 在短事务中回写助手消息内容。
     *
     * @param assistantMsg 占位助手消息
     * @param content 已生成的文本，失败或中断时可能不完整
     * @return 更新行数
     * @throws IllegalStateException 事务未返回结果
     */
    private int persistAssistantContent(Message assistantMsg, String content) {
        Integer updated = transactionTemplate.execute(status -> {
            assistantMsg.setContent(content);
            return messageMapper.updateById(assistantMsg);
        });
        if (updated == null) {
            throw new IllegalStateException("助手消息回写未返回结果");
        }
        return updated;
    }

    private MessageResponse convertToResponse(Message message) {
        MessageResponse response = new MessageResponse();
        response.setId(message.getId());
        response.setConversationId(message.getConversationId());
        response.setUserId(message.getUserId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setSequence(message.getSequence());
        response.setCreatedAt(message.getCreatedAt());
        return response;
    }
}
