package com.zdmj.common.async;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;

import com.zdmj.common.constants.RedisConstants;
import com.zdmj.common.util.RedisUtil;
import com.zdmj.knowledgeService.entity.KnowledgeVectorTask;
import com.zdmj.knowledgeService.enums.KnowledgeVectorTaskTypeEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorTaskMapper;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;
import com.zdmj.knowledgeService.support.EmbedStreamConsumer;
import com.zdmj.knowledgeService.support.EmbedStreamProducer;

@ExtendWith(MockitoExtension.class)
class EmbedStreamConsumerTest {

    @Mock
    private RedisUtil redisUtil;
    @Mock
    private KnowledgeVectorTaskMapper mapper;
    @Mock
    private KnowledgeEmbeddingService embeddingService;
    @Mock
    private EmbedStreamProducer producer;

    @Test
    void consume_registered_shouldExecuteClaimedAndMarkSuccess() {
        EmbedStreamConsumer consumer = new EmbedStreamConsumer(redisUtil, mapper, embeddingService, producer);
        when(mapper.selectById(9L)).thenReturn(task(9L));
        when(mapper.claimPendingTask(9L)).thenReturn(1);

        consumer.consumeRecord(record(9L));

        verify(embeddingService).executeClaimed(any(KnowledgeVectorTask.class));
        verify(mapper).markTaskSuccess(9L);
        verify(redisUtil).xack(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    void consume_executeThrows_shouldMarkFailed() {
        EmbedStreamConsumer consumer = new EmbedStreamConsumer(redisUtil, mapper, embeddingService, producer);
        when(mapper.selectById(9L)).thenReturn(task(9L));
        when(mapper.claimPendingTask(9L)).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("embed down"))
                .when(embeddingService).executeClaimed(any(KnowledgeVectorTask.class));

        consumer.consumeRecord(record(9L));

        verify(mapper).markTaskFailed(eq(9L), eq("embed down"));
        verify(mapper, never()).markTaskSuccess(any());
        verify(redisUtil).xack(anyString(), anyString(), any(RecordId.class));
    }

    private static MapRecord<String, String, String> record(long taskId) {
        return MapRecord.create(RedisConstants.EMBED_STREAM_KEY, Map.of(
                RedisConstants.STREAM_FIELD_TASK_ID, Long.toString(taskId))).withId(RecordId.of("1-0"));
    }

    private static KnowledgeVectorTask task(long id) {
        KnowledgeVectorTask row = new KnowledgeVectorTask();
        row.setId(id);
        row.setUserId(8L);
        row.setDocumentId(3L);
        row.setTaskType(KnowledgeVectorTaskTypeEnum.EMBEDDING.getCode());
        return row;
    }
}
