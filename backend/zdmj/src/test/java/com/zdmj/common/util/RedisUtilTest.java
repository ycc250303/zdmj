package com.zdmj.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.constants.RedisConstants;

@ExtendWith(MockitoExtension.class)
class RedisUtilTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    @SuppressWarnings("rawtypes")
    private StreamOperations streamOps;
    @Mock
    private ValueOperations<String, String> values;

    private RedisUtil redisUtil;

    @BeforeEach
    void setUp() {
        redisUtil = new RedisUtil(redisTemplate, new ObjectMapper());
    }

    @Test
    void setAndGet_jsonRoundTrip_shouldReturnObject() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("job:1")).thenReturn("{\"name\":\"后端\"}");

        redisUtil.set("job:1", java.util.Map.of("name", "后端"), 30);
        assertEquals("后端", redisUtil.get("job:1", java.util.Map.class).get("name"));
    }

    @Test
    void get_missingOrBroken_shouldReturnNull() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("missing")).thenReturn(null);
        when(values.get("broken")).thenReturn("{");

        assertNull(redisUtil.get("missing", String.class));
        assertNull(redisUtil.get("broken", String.class));
    }

    @Test
    void stringCache_failure_shouldNotEscape() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("down"));
        when(redisTemplate.hasKey("null:value:job:1")).thenThrow(new IllegalStateException("down"));
        when(redisTemplate.delete("null:value:job:1")).thenThrow(new IllegalStateException("down"));

        redisUtil.setString("job:1", "1", 10);
        assertNull(redisUtil.getString("job:1"));
        assertFalse(redisUtil.exists("job:1"));
        redisUtil.setNullValue("job:1", 10);
        assertFalse(redisUtil.isNullValue("job:1"));
        redisUtil.delete("job:1");
        redisUtil.deleteNullValue("job:1");
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
        assertEquals(0L, redisUtil.xack("s", "g", (RecordId[]) null));
        verify(redisTemplate, never()).opsForStream();
    }

    @Test
    void xreadGroup_absentBlockAndOffset_shouldReadWithoutBlocking() {
        stubStreamOps();
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of());

        assertTrue(redisUtil.xreadGroup("s", "g", "c", 1, null, null).isEmpty());
        assertTrue(redisUtil.xreadGroup("s", "g", "c", 1, Duration.ofMillis(-1), ReadOffset.from("0-0")).isEmpty());
    }

    @Test
    void xack_nullAckCount_shouldReturnZero() {
        stubStreamOps();
        when(streamOps.acknowledge(eq("s"), eq("g"), any(RecordId[].class))).thenReturn(null);

        assertEquals(0L, redisUtil.xack("s", "g", RecordId.of("1-0")));
    }

    @Test
    void ensureConsumerGroup_busyGroupInCause_shouldIgnore() {
        stubStreamOps();
        RuntimeException busy = new RuntimeException((String) null,
                new RedisSystemException("BUSYGROUP Consumer Group name already exists", null));
        when(streamOps.createGroup(eq("s"), any(ReadOffset.class), eq("g"))).thenThrow(busy);

        redisUtil.ensureConsumerGroup("s", "g");
    }

    @SuppressWarnings("unchecked")
    private void stubStreamOps() {
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
    }
}
