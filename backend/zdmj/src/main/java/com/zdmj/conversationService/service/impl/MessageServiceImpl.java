package com.zdmj.conversationService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.ai.config.RagConfig;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.PageRequests;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.common.ai.prompt.PromptNames;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import org.springframework.beans.BeanUtils;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 消息 Service 实现类
 */
@RequiredArgsConstructor
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    private final ChatUtil chatUtil;
    private final MessageMapper messageMapper;
    private final ConversationService conversationService;
    private final ConversationMapper conversationMapper;
    private final KnowledgeRagService knowledgeRagService;
    private final RagConfig ragConfig;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Flux<ServerSentEvent<String>> createStream(ChatStreamRequest request) {
        Conversation conversation = requireConversationAccess(request.getConversationId());
        Long userId = UserHolder.requireUserId();

        // 1.原子递增消息计数
        Integer newCount = conversationMapper.incrementMessageCountAndGet(request.getConversationId(), userId, 2);
        if (newCount == null || newCount < 2) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }

        int userSeq = newCount - 1;
        int assistantSeq = newCount;

        // 2.写入 user 消息
        Message userMsg = new Message();
        userMsg.setConversationId(request.getConversationId());
        userMsg.setUserId(userId);
        userMsg.setRole(MessageRoleEnum.USER.getCode());
        userMsg.setContent(request.getMessage());
        userMsg.setSequence(userSeq);
        if (messageMapper.insert(userMsg) != 1) {
            throw new BusinessException(ErrorCode.MESSAGE_CREATE_FAILED);
        }

        // 3.预写 assistant 消息
        Message assistantMsg = new Message();
        assistantMsg.setConversationId(request.getConversationId());
        assistantMsg.setUserId(userId);
        assistantMsg.setRole(2);
        assistantMsg.setContent("");
        assistantMsg.setSequence(assistantSeq);
        if (messageMapper.insert(assistantMsg) != 1) {
            throw new BusinessException(ErrorCode.MESSAGE_CREATE_FAILED);
        }

        // 4.更新会话标题
        if (newCount == 2) {
            String title = chatUtil.chatOnce(
                    userId,
                    request.getMessage(),
                    PromptNames.GENERATE_CONVERSATION_TITLE,
                    null
            );
            conversationMapper.updateTitleByIdAndUserId(request.getConversationId(), userId, title);
        }

        // 5.方法内 sink：HTTP 断开后 LLM 仍跑完并落库；不跨请求、不进 Redis
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder full = new StringBuilder(256);

        List<Long> ragDocumentIds = ConversationContextSupport.resolveRagDocumentIds(conversation);
        boolean useSystemKnowledge = ConversationContextSupport.resolveUseSystemKnowledge(conversation);
        Map<String, Object> promptVars = ConversationContextSupport.buildChatPromptVars(conversation);
        Flux<String> chatFlux = ragConfig.isEnabled()
                ? knowledgeRagService.streamAnswer(
                        userId,
                        request.getConversationId(),
                        request.getMessage(),
                        ragDocumentIds,
                        useSystemKnowledge,
                        promptVars)
                : chatUtil.chatStreamInConversation(
                        userId,
                        request.getConversationId(),
                        request.getMessage(),
                        PromptNames.SYSTEM,
                        promptVars);
        chatFlux.doOnNext(chunk -> {
            if (chunk == null || chunk.isEmpty()) {
                return;
            }
            full.append(chunk);
            sink.tryEmitNext(chunk);
        })
                .doOnError(e -> {
                    persistAssistantContent(assistantMsg, full.toString());
                    sink.tryEmitError(e);
                })
                .doOnComplete(() -> {
                    String finalText = full.toString();
                    assistantMsg.setContent(finalText);
                    if (messageMapper.updateById(assistantMsg) != 1) {
                        sink.tryEmitError(new RuntimeException("assistant message persist failed"));
                        return;
                    }
                    sink.tryEmitComplete();
                })
                .subscribe();

        return sink.asFlux()
                .index()
                .map(tp -> ServerSentEvent.<String>builder()
                        .event("delta")
                        .id(String.valueOf(tp.getT1() + 1))
                        .data(toOpenAiDeltaJson(tp.getT2()))
                        .build());
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
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "会话ID不能为空");
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

    /** 错误路径尽力回写已生成内容，失败不影响向下游抛错。 */
    private void persistAssistantContent(Message assistantMsg, String content) {
        assistantMsg.setContent(content);
        messageMapper.updateById(assistantMsg);
    }

    private MessageResponse convertToResponse(Message message) {
        MessageResponse response = new MessageResponse();
        BeanUtils.copyProperties(message, response);
        return response;
    }
}
