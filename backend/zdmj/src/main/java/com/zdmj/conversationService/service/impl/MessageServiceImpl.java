package com.zdmj.conversationService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.cache.RedisUtil;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.ChatUtil;
import com.zdmj.conversationService.dto.MessageDTO;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.entity.Message;
import com.zdmj.conversationService.enums.MessageRoleEnum;
import com.zdmj.conversationService.mapper.MessageMapper;
import com.zdmj.conversationService.service.ConversationService;
import com.zdmj.conversationService.service.MessageService;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息 Service 实现类
 */
@RequiredArgsConstructor
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    private final RedisUtil redisUtil;
    private final ChatUtil chatUtil;
    private final MessageMapper messageMapper;
    private final ConversationService conversationService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Redis 刷新策略：至少间隔 300ms 或新增 >= 1024 字符时写一次
    private static final long REDIS_FLUSH_INTERVAL_MS = 300L;
    private static final int REDIS_FLUSH_DELTA_CHARS = 1024;
    // 流式消息过期时间
    private static final int STREAM_TTL_SECONDS = 3600;
    // 流式消息 sink 映射
    private final ConcurrentHashMap<Long, Sinks.Many<String>> streamSinkMap = new ConcurrentHashMap<>();

    @Override
    public Flux<ServerSentEvent<String>> createStream(MessageDTO dto) {
        Long userId = UserHolder.requireUserId();
        Conversation conversation = conversationService.getById(dto.getConversationId());
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        } else if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // 1.写入 user 消息
        Message userMsg = new Message();
        userMsg.setConversationId(dto.getConversationId());
        userMsg.setUserId(userId);
        userMsg.setRole(MessageRoleEnum.USER.getCode()); // user
        userMsg.setContent(dto.getMessage());
        userMsg.setSequence(messageMapper.selectMessageCountByConversationId(dto.getConversationId()) + 1);
        if (messageMapper.insert(userMsg) != 1) {
            throw new BusinessException(ErrorCode.MESSAGE_CREATE_FAILED);
        }

        // 2.预写 assistant 消息
        Message assistantMsg = new Message();
        assistantMsg.setConversationId(dto.getConversationId());
        assistantMsg.setUserId(userId);
        assistantMsg.setRole(2); // assistant
        assistantMsg.setContent("");
        assistantMsg.setSequence(messageMapper.selectMessageCountByConversationId(dto.getConversationId()) + 1);
        if (messageMapper.insert(assistantMsg) != 1) {
            throw new BusinessException(ErrorCode.MESSAGE_CREATE_FAILED);
        }

        // 3.创建流式消息
        Long streamId = assistantMsg.getId();
        String statusKey = StreamKeys.status(streamId);
        String contentKey = StreamKeys.content(streamId);
        String doneKey = StreamKeys.done(streamId);
        String errorKey = StreamKeys.error(streamId);

        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        streamSinkMap.put(streamId, sink);

        // 初始化状态
        redisUtil.setString(statusKey, "streaming", STREAM_TTL_SECONDS);
        redisUtil.setString(contentKey, "", STREAM_TTL_SECONDS);
        redisUtil.setString(doneKey, "0", STREAM_TTL_SECONDS);
        redisUtil.delete(errorKey);

        StringBuilder full = new StringBuilder(256);
        AtomicLong lastFlushAt = new AtomicLong(System.currentTimeMillis());
        AtomicInteger lastFlushedLen = new AtomicInteger(0);

        // 4.调用 AI 服务
        chatUtil.chatStream(dto.getMessage())
                .doOnNext(chunk -> {
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
                        // TODO：可考虑引入 Redis Stream进一步优化
                        redisUtil.setString(contentKey, full.toString(), STREAM_TTL_SECONDS);
                        lastFlushAt.set(now);
                        lastFlushedLen.set(currLen);
                    }
                })
                .doOnError(e -> {
                    // 错误态 + 最终内容落缓存，方便前端恢复展示
                    redisUtil.setString(contentKey, full.toString(), STREAM_TTL_SECONDS);
                    redisUtil.setString(statusKey, "failed", STREAM_TTL_SECONDS);
                    redisUtil.setString(errorKey, e.getMessage() == null ? "stream failed" : e.getMessage(),
                            STREAM_TTL_SECONDS);
                    sink.tryEmitError(e);
                })
                .doOnComplete(() -> {
                    // 完成时强刷缓存 + 回写 DB
                    String finalText = full.toString();
                    redisUtil.setString(contentKey, finalText, STREAM_TTL_SECONDS);

                    assistantMsg.setContent(finalText);
                    if (messageMapper.updateById(assistantMsg) != 1) {
                        // 写入 DB 失败时标记失败，避免前端误判完成
                        redisUtil.setString(statusKey, "failed", STREAM_TTL_SECONDS);
                        redisUtil.setString(errorKey, "assistant message persist failed", STREAM_TTL_SECONDS);
                        sink.tryEmitError(new RuntimeException("assistant message persist failed"));
                        return;
                    }

                    redisUtil.setString(statusKey, "completed", STREAM_TTL_SECONDS);
                    redisUtil.setString(doneKey, "1", STREAM_TTL_SECONDS);
                    sink.tryEmitComplete();
                })
                .doFinally(signalType -> {
                    // 无论 complete/error/cancel 都做资源清理
                    streamSinkMap.remove(streamId);
                })
                .subscribe();

        // 5.返回流式消息
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

    @Override
    public Flux<ServerSentEvent<String>> resumeStream(Long streamId, int offset) {
        if (streamId == null) {
            throw new BusinessException(ErrorCode.ILLEGAL_ARGUMENT.getCode(), "streamId不能为空");
        }
        if (offset < 0) {
            offset = 0;
        }

        String contentKey = StreamKeys.content(streamId);
        String statusKey = StreamKeys.status(streamId);
        String errorKey = StreamKeys.error(streamId);
        String cached = redisUtil.getString(contentKey);
        String status = redisUtil.getString(statusKey);

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
            String err = redisUtil.getString(errorKey);
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

    @Override
    public List<Message> getByConversationId(Long conversationId) {
        throw new UnsupportedOperationException("TODO: implement list messages by conversation");
    }

    @Data
    public class ChatStreamRequest {
        private Long conversationId; // 会话ID
        private String message; // 消息
    }

    @Data
    public class ChatResumeRequest {
        private Long streamId; // 流式消息ID 建议=assistantMessageId
        private Integer offset; // 前端已接收字符数
    }

    private String generateConversationTitle(String message) {
        String prompt;
        String title = chatUtil.chat(message);
        return title;
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
