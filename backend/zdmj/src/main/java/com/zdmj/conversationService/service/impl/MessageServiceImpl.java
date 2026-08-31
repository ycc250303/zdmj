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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息 Service 实现类
 */
@RequiredArgsConstructor
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    private final StringRedisTemplate redisTemplate;
    private final ChatUtil chatUtil;
    private final MessageMapper messageMapper;
    private final ConversationService conversationService;
    private final ConversationMapper conversationMapper;
    private final KnowledgeRagService knowledgeRagService;
    private final RagConfig ragConfig;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Redis 刷新策略：至少间隔 300ms 或新增 >= 1024 字符时写一次
    private static final long REDIS_FLUSH_INTERVAL_MS = 300L;
    private static final int REDIS_FLUSH_DELTA_CHARS = 1024;
    // 流式消息过期时间
    private static final int STREAM_TTL_SECONDS = 3600;
    // 流式消息 sink 映射
    private final ConcurrentHashMap<Long, Sinks.Many<String>> streamSinkMap = new ConcurrentHashMap<>();

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
        userMsg.setRole(MessageRoleEnum.USER.getCode()); // user
        userMsg.setContent(request.getMessage());
        userMsg.setSequence(userSeq);
        if (messageMapper.insert(userMsg) != 1) {
            throw new BusinessException(ErrorCode.MESSAGE_CREATE_FAILED);
        }

        // 3.预写 assistant 消息
        Message assistantMsg = new Message();
        assistantMsg.setConversationId(request.getConversationId());
        assistantMsg.setUserId(userId);
        assistantMsg.setRole(2); // assistant
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
            // 仅更新标题，避免回写旧的 message_count / last_message_at
            conversationMapper.updateTitleByIdAndUserId(request.getConversationId(), userId, title);
        }

        // 5.创建流式消息
        Long streamId = assistantMsg.getId();
        String statusKey = StreamKeys.status(streamId);
        String contentKey = StreamKeys.content(streamId);
        String doneKey = StreamKeys.done(streamId);
        String errorKey = StreamKeys.error(streamId);

        // 6.初始化状态（失败则抛，创建流对调用方可见；成功后再登记 sink）
        setStreamValue(statusKey, "streaming");
        setStreamValue(contentKey, "");
        setStreamValue(doneKey, "0");
        redisTemplate.delete(errorKey);

        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        streamSinkMap.put(streamId, sink);

        StringBuilder full = new StringBuilder(256);
        AtomicLong lastFlushAt = new AtomicLong(System.currentTimeMillis());
        AtomicInteger lastFlushedLen = new AtomicInteger(0);

        // 7.调用 AI 服务
        List<Long> ragDocumentIds = resolveRagDocumentIds(request, conversation);
        boolean useSystemKnowledge = ConversationContextSupport.resolveUseSystemKnowledge(request, conversation);
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

            // 追加内容
            full.append(chunk);
            // 尝试发送消息
            sink.tryEmitNext(chunk);

            int currLen = full.length();
            long now = System.currentTimeMillis();

            boolean flushByTime = now - lastFlushAt.get() > REDIS_FLUSH_INTERVAL_MS;
            boolean flushByLength = currLen - lastFlushedLen.get() > REDIS_FLUSH_DELTA_CHARS;
            boolean flush = flushByTime || flushByLength;

            if (flush) {
                // 节流写 Redis，避免每 chunk 全量覆盖
                // TODO：可考虑引入 Redis Stream/消息队列 进一步优化
                setStreamValue(contentKey, full.toString());
                lastFlushAt.set(now);
                lastFlushedLen.set(currLen);
            }
        })
                .doOnError(e -> {
                    // 错误态 + 最终内容落缓存，方便前端恢复展示
                    setStreamValue(contentKey, full.toString());
                    setStreamValue(statusKey, "failed");
                    setStreamValue(errorKey, e.getMessage() == null ? "stream failed" : e.getMessage());
                    sink.tryEmitError(e);
                })
                .doOnComplete(() -> {
                    // 完成时强刷缓存 + 回写 DB
                    String finalText = full.toString();
                    setStreamValue(contentKey, finalText);

                    assistantMsg.setContent(finalText);
                    if (messageMapper.updateById(assistantMsg) != 1) {
                        // 写入 DB 失败时标记失败，避免前端误判完成
                        setStreamValue(statusKey, "failed");
                        setStreamValue(errorKey, "assistant message persist failed");
                        sink.tryEmitError(new RuntimeException("assistant message persist failed"));
                        return;
                    }

                    setStreamValue(statusKey, "completed");
                    setStreamValue(doneKey, "1");
                    sink.tryEmitComplete();
                })
                .doFinally(signalType -> {
                    // 无论 complete/error/cancel 都做资源清理
                    streamSinkMap.remove(streamId);
                })
                .subscribe();

        // 8.返回流式消息
        Flux<ServerSentEvent<String>> meta = Flux.just(
                ServerSentEvent.<String>builder()
                        .event("meta")
                        .id("0")
                        .data("{\"streamId\":\"" + streamId + "\"}")
                        .build());

        Flux<ServerSentEvent<String>> body = sink.asFlux()
                .index()
                .map(tp -> ServerSentEvent.<String>builder()
                        .event("delta")
                        .id(String.valueOf(tp.getT1() + 1))
                        .data(toOpenAiDeltaJson(tp.getT2()))
                        .build());
        return meta.concatWith(body);
    }

    /**
     * 恢复流式消息
     * @param streamId 流式消息 ID
     * @param offset 偏移量
     * @return 流式消息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Flux<ServerSentEvent<String>> resumeStream(Long streamId, int offset) {
        if (streamId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "streamId不能为空");
        }
        if (offset < 0) {
            offset = 0;
        }

        String contentKey = StreamKeys.content(streamId);
        String statusKey = StreamKeys.status(streamId);
        String errorKey = StreamKeys.error(streamId);
        String cached = redisTemplate.opsForValue().get(contentKey);
        String status = redisTemplate.opsForValue().get(statusKey);

        if (cached == null) {
            cached = "";
        }
        if (status == null) {
            status = "failed";
        }

        // 1.先补发历史缺失片段
        String replay = offset < cached.length() ? cached.substring(offset) : "";
        Flux<ServerSentEvent<String>> replayFlux = replay.isEmpty()
                ? Flux.empty()
                : Flux.just(ServerSentEvent.<String>builder().event("replay").data(toOpenAiDeltaJson(replay)).build());
        // 2.已结束：补 done/error 后结束
        if ("completed".equals(status)) {
            Flux<ServerSentEvent<String>> done = Flux.just(
                    ServerSentEvent.<String>builder().event("done").data("[DONE]").build());
            return replayFlux.concatWith(done);
        }
        if ("failed".equals(status)) {
            String err = redisTemplate.opsForValue().get(errorKey);
            Flux<ServerSentEvent<String>> fail = Flux.just(
                    ServerSentEvent.<String>builder().event("error").data(err == null ? "stream failed" : err).build());
            return replayFlux.concatWith(fail);
        }
        // 3.streaming：继续订阅 live
        Sinks.Many<String> sink = streamSinkMap.get(streamId);
        if (sink == null) {
            // 兜底：流刚结束或资源已回收，返回当前可补发内容
            return replayFlux;
        }
        Flux<ServerSentEvent<String>> live = sink.asFlux()
                .map(chunk -> ServerSentEvent.<String>builder().event("delta").data(toOpenAiDeltaJson(chunk)).build());
        return replayFlux.concatWith(live);
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
     * 解析本次 RAG 参与的知识文档 ID。
     * 请求体优先；否则读取会话 config.ragDocumentIds；均未配置时返回 null（检索全部文档）。
     */
    private List<Long> resolveRagDocumentIds(ChatStreamRequest request, Conversation conversation) {
        if (request.getRagDocumentIds() != null) {
            return request.getRagDocumentIds();
        }
        if (conversation == null || conversation.getConfig() == null) {
            return null;
        }
        return parseRagDocumentIds(conversation.getConfig().get("ragDocumentIds"));
    }

    private List<Long> parseRagDocumentIds(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<Long> ids = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Number number) {
                ids.add(number.longValue());
            }
        }
        return ids;
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
            // 极端场景兜底，至少保证流不中断
            return "{\"choices\":[{\"delta\":{\"content\":\"\"}}]}";
        }
    }

    /** 流状态写入：固定 TTL，异常向上抛，不走 RedisUtil 的抖动与吞异常。 */
    private void setStreamValue(String key, String value) {
        redisTemplate.opsForValue().set(key, value, STREAM_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private MessageResponse convertToResponse(Message message) {
        MessageResponse response = new MessageResponse();
        BeanUtils.copyProperties(message, response);
        return response;
    }

    /**
     * 流式消息缓存前缀
     */
    public final class StreamKeys {
        private StreamKeys() {
        }

        // 流式消息状态
        public static String status(Long streamId) {
            return "chat:stream:" + streamId + ":status";
        } // streaming/completed/failed

        // 流式消息内容
        public static String content(Long streamId) {
            return "chat:stream:" + streamId + ":content";
        } // full text buffer

        // 流式消息错误
        public static String error(Long streamId) {
            return "chat:stream:" + streamId + ":error";
        }

        // 流式消息完成
        public static String done(Long streamId) {
            return "chat:stream:" + streamId + ":done";
        } // 1/0
    }
}
