package com.zdmj.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import java.util.concurrent.TimeUnit;

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
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.constants.RedisConstants;

@ExtendWith(MockitoExtension.class)
class RedisUtilTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    @SuppressWarnings("rawtypes")
    private StreamOperations streamOps;

    private RedisUtil redisUtil;

    @BeforeEach
    void setUp() {
        redisUtil = new RedisUtil(redisTemplate, new ObjectMapper());
    }

    @Test
    void tryLock_acquired_shouldReturnTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("zdmj:tasklock:1:user:1", "9", 600L, TimeUnit.SECONDS)).thenReturn(true);

        assertTrue(redisUtil.tryLock("zdmj:tasklock:1:user:1", "9", 600));
    }

    @Test
    void tryLock_alreadyHeld_shouldReturnFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("k", "v", 10L, TimeUnit.SECONDS)).thenReturn(false);

        assertFalse(redisUtil.tryLock("k", "v", 10));
    }

    @Test
    void tryLock_redisDown_shouldPropagate() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), any(Long.class), any())).thenThrow(new RuntimeException("redis down"));

        assertThrows(RuntimeException.class, () -> redisUtil.tryLock("k", "v", 10));
    }

    @Test
    void unlock_shouldDeleteKey() {
        redisUtil.unlock("zdmj:tasklock:1:user:1");
        verify(redisTemplate).delete("zdmj:tasklock:1:user:1");
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
    void xaddTask_shouldWriteIdentityFields() {
        stubStreamOps();
        when(streamOps.add(any(), any(Map.class), any(XAddOptions.class))).thenReturn(RecordId.of("2-0"));

        redisUtil.xaddTask(RedisConstants.LLM_STREAM_KEY, 88L, 4, 7L, "user:7:job:3", 1);

        ArgumentCaptor<Map<String, String>> fields = ArgumentCaptor.forClass(Map.class);
        verify(streamOps).add(eq(RedisConstants.LLM_STREAM_KEY), fields.capture(), any(XAddOptions.class));
        Map<String, String> body = fields.getValue();
        assertEquals("88", body.get(RedisConstants.STREAM_FIELD_TASK_ID));
        assertEquals("4", body.get(RedisConstants.STREAM_FIELD_TYPE));
        assertEquals("7", body.get(RedisConstants.STREAM_FIELD_USER_ID));
        assertEquals("user:7:job:3", body.get(RedisConstants.STREAM_FIELD_BIZ_KEY));
        assertEquals("1", body.get(RedisConstants.STREAM_FIELD_RETRY_COUNT));
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
                "zdmj:llm:stream", "zdmj:llm:group", "c1", 1, Duration.ofSeconds(2));

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

    @Test
    void xclaim_emptyIds_shouldSkipRedis() {
        assertTrue(redisUtil.xclaim("s", "g", "c", Duration.ofSeconds(700)).isEmpty());
        verify(redisTemplate, never()).opsForStream();
    }

    @Test
    void xclaim_shouldReturnClaimedRecords() {
        stubStreamOps();
        RecordId id = RecordId.of("3-0");
        MapRecord<String, String, String> record = MapRecord.create("s", Map.of("taskId", "3")).withId(id);
        when(streamOps.claim(eq("s"), eq("g"), eq("c"), eq(Duration.ofSeconds(700)), eq(id)))
                .thenReturn(List.of(record));

        List<MapRecord<String, String, String>> out = redisUtil.xclaim("s", "g", "c", Duration.ofSeconds(700), id);

        assertEquals(1, out.size());
        assertSame(record, out.get(0));
    }

    @SuppressWarnings("unchecked")
    private void stubStreamOps() {
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
    }
}
