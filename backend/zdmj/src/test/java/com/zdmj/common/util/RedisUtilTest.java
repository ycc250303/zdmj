package com.zdmj.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.constants.RedisConstants;

@ExtendWith(MockitoExtension.class)
class RedisUtilTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    @SuppressWarnings("rawtypes")
    private StreamOperations streamOps;

    private RedisUtil redisUtil;

    @BeforeEach
    void setUp() {
        redisUtil = new RedisUtil(redisTemplate, new ObjectMapper());
    }

    @Test
    void xadd_shouldTrimWithApproxMaxlen() {
        stubStreamOps();
        RecordId recordId = RecordId.of("1-0");
        when(streamOps.add(eq("zdmj:llm:stream"), any(Map.class), any(XAddOptions.class))).thenReturn(recordId);

        RecordId out = redisUtil.xadd("zdmj:llm:stream", Map.of("taskId", "1"));

        assertEquals(recordId, out);
        ArgumentCaptor<XAddOptions> options = ArgumentCaptor.forClass(XAddOptions.class);
        verify(streamOps).add(eq("zdmj:llm:stream"), any(Map.class), options.capture());
        assertEquals(RedisConstants.STREAM_MAXLEN, options.getValue().getMaxlen());
        assertTrue(options.getValue().isApproximateTrimming());
    }

    @Test
    void xadd_nullRecordId_shouldThrow() {
        stubStreamOps();
        when(streamOps.add(any(), any(Map.class), any(XAddOptions.class))).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> redisUtil.xadd("s", Map.of("a", "b")));
    }

    @Test
    void xaddTask_shouldWriteTaskIdOnly() {
        stubStreamOps();
        when(streamOps.add(any(), any(Map.class), any(XAddOptions.class))).thenReturn(RecordId.of("2-0"));

        redisUtil.xaddTask(RedisConstants.LLM_STREAM_KEY, 88L);

        ArgumentCaptor<Map<String, String>> fields = ArgumentCaptor.forClass(Map.class);
        verify(streamOps).add(eq(RedisConstants.LLM_STREAM_KEY), fields.capture(), any(XAddOptions.class));
        Map<String, String> body = fields.getValue();
        assertEquals("88", body.get(RedisConstants.STREAM_FIELD_TASK_ID));
        assertEquals(1, body.size());
    }

    @Test
    void ensureConsumerGroup_busyGroup_shouldIgnore() {
        stubStreamOps();
        when(streamOps.createGroup(eq("zdmj:llm:stream"), any(ReadOffset.class), eq("zdmj:llm:group")))
                .thenThrow(new RedisSystemException("BUSYGROUP Consumer Group name already exists", null));

        redisUtil.ensureConsumerGroup("zdmj:llm:stream", "zdmj:llm:group");
    }

    @Test
    void ensureConsumerGroup_otherError_shouldPropagate() {
        stubStreamOps();
        when(streamOps.createGroup(any(), any(ReadOffset.class), any()))
                .thenThrow(new RedisSystemException("NOAUTH", null));

        assertThrows(RedisSystemException.class,
                () -> redisUtil.ensureConsumerGroup("zdmj:llm:stream", "zdmj:llm:group"));
    }

    @Test
    void xreadGroup_null_shouldReturnEmpty() {
        stubStreamOps();
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(null);

        List<MapRecord<String, String, String>> out = redisUtil.xreadGroup(
                "zdmj:llm:stream", "zdmj:llm:group", "c1", 1, Duration.ofSeconds(2), ReadOffset.lastConsumed());

        assertTrue(out.isEmpty());
    }

    @Test
    void xack_shouldAcknowledgeRecordId() {
        stubStreamOps();
        RecordId id = RecordId.of("1-0");
        when(streamOps.acknowledge("s", "g", id)).thenReturn(1L);

        assertEquals(1L, redisUtil.xack("s", "g", id));
    }

    @Test
    void xack_emptyIds_shouldSkipRedis() {
        assertEquals(0L, redisUtil.xack("s", "g"));
        verify(redisTemplate, never()).opsForStream();
    }

    @SuppressWarnings("unchecked")
    private void stubStreamOps() {
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
    }
}
