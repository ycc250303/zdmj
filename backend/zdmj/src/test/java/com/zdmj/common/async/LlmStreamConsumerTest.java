package com.zdmj.common.async;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void consume_phaseOne_shouldMarkFailedWithoutSuccess() {
        LlmStreamConsumer consumer = new LlmStreamConsumer(redisUtil, mapper);
        when(mapper.selectById(9L)).thenReturn(task(9L));
        when(mapper.claimPendingTask(9L)).thenReturn(1);

        consumer.consumeRecord(MapRecord.create(RedisConstants.LLM_STREAM_KEY, Map.of(
                RedisConstants.STREAM_FIELD_TASK_ID, "9")).withId(RecordId.of("1-0")));

        verify(mapper).markTaskFailed(eq(9L), eq("一期未接入业务执行器: type=1"));
        verify(redisUtil).xack(anyString(), anyString(), any(RecordId.class));
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
}
