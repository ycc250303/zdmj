package com.zdmj.common.async;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;

import com.zdmj.common.async.mapper.AsyncLlmTaskMapper;
import com.zdmj.common.constants.RedisConstants;
import com.zdmj.common.util.RedisUtil;

@ExtendWith(MockitoExtension.class)
class LlmStreamConsumerTest {

    @Mock
    private RedisUtil redisUtil;
    @Mock
    private AsyncLlmTaskMapper mapper;
    @Mock
    private AsyncTaskExecutor studentExecutor;
    @Mock
    private AsyncTaskExecutor duplicateExecutor;

    @Test
    void constructor_duplicateType_shouldFail() {
        when(studentExecutor.type()).thenReturn(AsyncTaskType.STUDENT_PROFILE);
        when(duplicateExecutor.type()).thenReturn(AsyncTaskType.STUDENT_PROFILE);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new LlmStreamConsumer(redisUtil, mapper, List.of(studentExecutor, duplicateExecutor)));
        assertTrue(ex.getMessage().contains("重复的任务执行器"));
    }

    @Test
    void consume_unregisteredType_shouldMarkFailed() {
        LlmStreamConsumer consumer = new LlmStreamConsumer(redisUtil, mapper, List.of());
        when(mapper.selectById(9L)).thenReturn(task(9L, AsyncTaskType.STUDENT_PROFILE.getCode()));
        when(mapper.claimPendingTask(9L)).thenReturn(1);

        consumer.consumeRecord(record(9L));

        verify(mapper).markTaskFailed(eq(9L), eq("未注册任务执行器: type=1"));
        verify(mapper, never()).markTaskSuccess(any(), any());
        verify(redisUtil).xack(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    void consume_reservedKbType_shouldMarkFailed() {
        LlmStreamConsumer consumer = new LlmStreamConsumer(redisUtil, mapper, List.of());
        when(mapper.selectById(9L)).thenReturn(task(9L, AsyncTaskType.KB_EMBED.getCode()));
        when(mapper.claimPendingTask(9L)).thenReturn(1);

        consumer.consumeRecord(record(9L));

        verify(mapper).markTaskFailed(eq(9L), eq("未注册任务执行器: type=9"));
        verify(redisUtil).xack(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    void consume_registered_shouldMarkSuccessWithResult() {
        when(studentExecutor.type()).thenReturn(AsyncTaskType.STUDENT_PROFILE);
        when(studentExecutor.execute(any(AsyncLlmTask.class))).thenReturn("{\"ok\":true}");
        LlmStreamConsumer consumer = new LlmStreamConsumer(redisUtil, mapper, List.of(studentExecutor));
        when(mapper.selectById(9L)).thenReturn(task(9L, AsyncTaskType.STUDENT_PROFILE.getCode()));
        when(mapper.claimPendingTask(9L)).thenReturn(1);

        consumer.consumeRecord(record(9L));

        verify(studentExecutor).execute(any(AsyncLlmTask.class));
        verify(mapper).markTaskSuccess(9L, "{\"ok\":true}");
        verify(redisUtil).xack(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    void consume_executorThrows_shouldMarkFailed() {
        when(studentExecutor.type()).thenReturn(AsyncTaskType.STUDENT_PROFILE);
        when(studentExecutor.execute(any(AsyncLlmTask.class))).thenThrow(new IllegalArgumentException("任务 payload 为空"));
        LlmStreamConsumer consumer = new LlmStreamConsumer(redisUtil, mapper, List.of(studentExecutor));
        when(mapper.selectById(9L)).thenReturn(task(9L, AsyncTaskType.STUDENT_PROFILE.getCode()));
        when(mapper.claimPendingTask(9L)).thenReturn(1);

        consumer.consumeRecord(record(9L));

        verify(mapper).markTaskFailed(eq(9L), eq("任务 payload 为空"));
        verify(mapper, never()).markTaskSuccess(any(), any());
        verify(redisUtil).xack(anyString(), anyString(), any(RecordId.class));
    }

    private static MapRecord<String, String, String> record(long taskId) {
        return MapRecord.create(RedisConstants.LLM_STREAM_KEY, Map.of(
                RedisConstants.STREAM_FIELD_TASK_ID, Long.toString(taskId))).withId(RecordId.of("1-0"));
    }

    private static AsyncLlmTask task(long id, int type) {
        AsyncLlmTask row = new AsyncLlmTask();
        row.setId(id);
        row.setUserId(8L);
        row.setTaskType(type);
        row.setBizKey("user:8");
        row.setStatus(AsyncTaskStatus.PENDING.getCode());
        return row;
    }
}
