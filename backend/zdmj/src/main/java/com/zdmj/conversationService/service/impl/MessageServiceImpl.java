package com.zdmj.conversationService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.PageRequests;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.conversationService.dto.ChatStreamRequest;
import com.zdmj.conversationService.dto.MessageResponse;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.entity.Message;
import com.zdmj.conversationService.enums.MessageRoleEnum;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.mapper.MessageMapper;
import com.zdmj.conversationService.service.ConversationService;
import com.zdmj.conversationService.service.MessageService;
import com.zdmj.conversationService.support.ConversationContextSupport;
import com.zdmj.knowledgeService.service.KnowledgeRagService;

import lombok.RequiredArgsConstructor;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import org.springframework.beans.BeanUtils;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 消息 Service 实现类
 */
@RequiredArgsConstructor
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    private static final String TITLE_PROMPT = "generate-conversation-title";

    private final ModelGateway modelGateway;
    private final MessageMapper messageMapper;
    private final ConversationService conversationService;
    private final ConversationMapper conversationMapper;
    private final KnowledgeRagService knowledgeRagService;
    private final TransactionTemplate transactionTemplate;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 先在短事务中写入用户消息和助手占位并提交，再生成标题、订阅模型流。
     * 流正常结束、失败或客户端中断时，另以短事务回写助手消息内容。
     * 失败仍通过流错误向下游传递，消息表没有单独的状态列。
     *
     * @param request 会话编号与用户消息
     * @return SSE 增量事件
     * @throws BusinessException 会话不存在、无权访问或消息写入失败
     */
    @Override
    public Flux<ServerSentEvent<String>> createStream(ChatStreamRequest request) {
        Conversation conversation = requireConversationAccess(request.getConversationId());
        Long userId = UserHolder.requireUserId();
        PreparedMessages prepared = transactionTemplate.execute(status -> insertMessages(request, userId));
        if (prepared == null) {
            throw new IllegalStateException("消息写入未返回结果");
        }
        if (prepared.newCount() == 2) {
            String title = modelGateway.generate(
                    CurrentActor.of(userId),
                    new ModelRequest(request.getMessage(), TITLE_PROMPT, null));
            conversationMapper.updateTitleByIdAndUserId(request.getConversationId(), userId, title);
        }
        subscribeAnswer(conversation, userId, request, prepared);
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
        return new PreparedMessages(newCount, assistantMsg, Sinks.many().unicast().onBackpressureBuffer(),
                new StringBuilder(256), new AtomicReference<>());
    }

    private void subscribeAnswer(Conversation conversation, Long userId, ChatStreamRequest request,
            PreparedMessages prepared) {
        List<Long> ragDocumentIds = ConversationContextSupport.resolveRagDocumentIds(conversation);
        boolean useSystemKnowledge = ConversationContextSupport.resolveUseSystemKnowledge(conversation);
        Map<String, Object> promptVars = ConversationContextSupport.buildChatPromptVars(conversation);
        Flux<String> chatFlux = knowledgeRagService.streamAnswer(
                userId,
                request.getConversationId(),
                request.getMessage(),
                ragDocumentIds,
                useSystemKnowledge,
                promptVars);
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

    private record PreparedMessages(int newCount, Message assistantMsg, Sinks.Many<String> sink, StringBuilder full,
            AtomicReference<Disposable> upstream) {
    }

    /**
     * 获取会话消息列表
     * @param conversationId 会话 ID
     * @param page 页码
     * @param limit 每页条数
     * @return 消息列表
     */
    @Override
    public PageDTO<MessageResponse> getMessagesByConversationId(Long conversationId, Integer page, Integer limit) {
        requireConversationAccess(conversationId);
        PageRequests.Normalized paging = PageRequests.normalize(page, limit);
        var mpPage = messageMapper.selectPageByConversationId(PageRequests.toPage(paging), conversationId);
        List<MessageResponse> list = mpPage.getRecords().stream().map(this::convertToResponse).toList();
        return PageDTO.from(mpPage, list);
    }

    /**
     * 校验会话 ID 有效且存在，且属于当前用户（发送消息、拉取消息列表前调用）。
     */
    private Conversation requireConversationAccess(Long conversationId) {
        if (conversationId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "会话ID不能为空");
        }
        return conversationService.requireOwned(conversationId);
    }

    /**
     * 生成 OpenAI 兼容的 delta JSON 片段，便于前端/Apifox 自动合并。
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
            return "{\"choices\":[{\"delta\":{\"content\":\"\"}}]}";
        }
    }

    /**
     * 在短事务中回写助手消息内容。
     *
     * @param assistantMsg 占位助手消息
     * @param content 已生成的文本，失败或中断时可能不完整
     * @return 更新行数；事务未返回结果时为 0
     */
    private int persistAssistantContent(Message assistantMsg, String content) {
        Integer updated = transactionTemplate.execute(status -> {
            assistantMsg.setContent(content);
            return messageMapper.updateById(assistantMsg);
        });
        return updated == null ? 0 : updated;
    }

    private MessageResponse convertToResponse(Message message) {
        MessageResponse response = new MessageResponse();
        BeanUtils.copyProperties(message, response);
        return response;
    }
}
