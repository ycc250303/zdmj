package com.zdmj.knowledgeService.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.RecordId;

import com.zdmj.common.constants.RedisConstants;
import com.zdmj.common.util.RedisUtil;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorTaskMapper;

@ExtendWith(MockitoExtension.class)
class EmbedStreamProducerTest {

    @Mock
    private RedisUtil redisUtil;
    @Mock
    private KnowledgeVectorTaskMapper mapper;

    private EmbedStreamProducer producer;

    @BeforeEach
    void setUp() {
        producer = new EmbedStreamProducer(redisUtil, mapper);
    }

    @Test
    void send_ok_shouldXaddTask() {
        when(redisUtil.xaddTask(eq(RedisConstants.EMBED_STREAM_KEY), eq(9L)))
                .thenReturn(RecordId.of("1-0"));

        assertTrue(producer.send(9L));

        verify(mapper, never()).markEnqueueFailed(anyLong(), anyString());
    }

    @Test
    void send_xaddThrows_shouldMarkEnqueueFailed() {
        when(redisUtil.xaddTask(anyString(), anyLong()))
                .thenThrow(new IllegalStateException("XADD 空 id"));
        when(mapper.markEnqueueFailed(9L, "任务入队失败: XADD 空 id")).thenReturn(1);

        assertFalse(producer.send(9L));

        verify(mapper).markEnqueueFailed(9L, "任务入队失败: XADD 空 id");
    }
}
