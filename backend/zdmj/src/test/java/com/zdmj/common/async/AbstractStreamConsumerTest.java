package com.zdmj.common.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;

import com.zdmj.common.async.mapper.AsyncLlmTaskMapper;
import com.zdmj.common.constants.RedisConstants;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.util.RedisUtil;

@ExtendWith(MockitoExtension.class)
class AbstractStreamConsumerTest {

    @Mock
    private RedisUtil redisUtil;
    @Mock
    private AsyncLlmTaskMapper mapper;

    private RecordingConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new RecordingConsumer(redisUtil, mapper);
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void consume_malformed_shouldAckWithoutClaim() {
        consumer.consumeRecord(record(Map.of("foo", "bar")));

        verify(redisUtil).xack(eq(RedisConstants.LLM_STREAM_KEY), eq(RedisConstants.LLM_STREAM_GROUP),
                eq(RecordId.of("1-0")));
        verify(mapper, never()).claimPendingTask(any());
        verify(mapper, never()).selectById(any());
    }

    @Test
    void consume_invalidTaskId_shouldAckWithoutClaim() {
        consumer.consumeRecord(record(Map.of(RedisConstants.STREAM_FIELD_TASK_ID, "x")));

        verify(redisUtil).xack(anyString(), anyString(), eq(RecordId.of("1-0")));
        verify(mapper, never()).selectById(any());
    }

    @Test
    void consume_taskMissing_shouldAckDiscard() {
        when(mapper.selectById(9L)).thenReturn(null);

        consumer.consumeRecord(record(fields(9L)));

        verify(redisUtil).xack(anyString(), anyString(), eq(RecordId.of("1-0")));
        verify(mapper, never()).claimPendingTask(any());
        assertEquals(0, consumer.processed);
    }

    @Test
    void consume_claimZero_shouldAckSkipBusiness() {
        when(mapper.selectById(9L)).thenReturn(task(9L));
        when(mapper.claimPendingTask(9L)).thenReturn(0);

        consumer.consumeRecord(record(fields(9L)));

        assertEquals(0, consumer.processed);
        verify(mapper, never()).markTaskSuccess(any(), any());
        verify(redisUtil).xack(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    void consume_success_shouldMarkAckAndClearUser() {
        when(mapper.selectById(9L)).thenReturn(task(9L));
        when(mapper.claimPendingTask(9L)).thenReturn(1);

        consumer.consumeRecord(record(fields(9L)));

        assertEquals(1, consumer.processed);
        verify(mapper).markTaskSuccess(9L, null);
        verify(redisUtil).xack(anyString(), anyString(), any(RecordId.class));
        assertNull(UserHolder.getUserId());
    }

    @Test
    void consume_businessException_shouldMarkFailedAck() {
        when(mapper.selectById(9L)).thenReturn(task(9L));
        when(mapper.claimPendingTask(9L)).thenReturn(1);
        consumer.failWith = new RuntimeException("llm timeout");

        consumer.consumeRecord(record(fields(9L)));

        verify(mapper).markTaskFailed(eq(9L), eq("llm timeout"));
        verify(mapper, never()).markTaskSuccess(any(), any());
        verify(redisUtil).xack(anyString(), anyString(), any(RecordId.class));
        assertEquals(0, consumer.processed);
    }

    private static Map<String, String> fields(long taskId) {
        return Map.of(RedisConstants.STREAM_FIELD_TASK_ID, Long.toString(taskId));
    }

    private static MapRecord<String, String, String> record(Map<String, String> data) {
        return MapRecord.create(RedisConstants.LLM_STREAM_KEY, data).withId(RecordId.of("1-0"));
    }

    private static AsyncLlmTask task(long id) {
        AsyncLlmTask row = new AsyncLlmTask();
        row.setId(id);
        row.setUserId(8L);
        row.setTaskType(1);
        row.setBizKey("user:8");
        row.setStatus(AsyncTaskStatus.PENDING.getCode());
        return row;
    }

    private static final class RecordingConsumer extends AbstractStreamConsumer {

        int processed;
        RuntimeException failWith;

        RecordingConsumer(RedisUtil redisUtil, AsyncLlmTaskMapper mapper) {
            super(redisUtil, mapper);
        }

        @Override
        protected String processBusiness(AsyncLlmTask task) {
            if (failWith != null) {
                throw failWith;
            }
            processed++;
            return null;
        }

        @Override
        protected String streamKey() {
            return RedisConstants.LLM_STREAM_KEY;
        }

        @Override
        protected String groupName() {
            return RedisConstants.LLM_STREAM_GROUP;
        }

        @Override
        protected String name() {
            return "LLM";
        }
    }
}
