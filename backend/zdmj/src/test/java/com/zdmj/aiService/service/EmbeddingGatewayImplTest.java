package com.zdmj.aiService.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class EmbeddingGatewayImplTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Test
    void embed_whenActorMissing_shouldThrowNotLogin() {
        EmbeddingGatewayImpl gateway = gateway();

        BusinessException missingActor = assertThrows(BusinessException.class,
                () -> gateway.embed(null, List.of("hello")));
        BusinessException missingUserId = assertThrows(BusinessException.class,
                () -> gateway.embed(CurrentActor.of(null), List.of("hello")));

        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), missingActor.getCode());
        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), missingUserId.getCode());
        verify(embeddingModel, never()).embed(anyList());
    }

    @Test
    void embed_whenTextsBlank_shouldThrowBeforeCallingModel() {
        EmbeddingGatewayImpl gateway = gateway();

        assertThrows(IllegalArgumentException.class, () -> gateway.embed(CurrentActor.of(1L), null));
        assertThrows(IllegalArgumentException.class, () -> gateway.embed(CurrentActor.of(1L), List.of()));
        assertThrows(IllegalArgumentException.class, () -> gateway.embed(CurrentActor.of(1L), List.of(" ")));
        verify(embeddingModel, never()).embed(anyList());
    }

    @Test
    void embed_shouldDelegateToPlatformModel() {
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[] {0.1f}));
        EmbeddingGatewayImpl gateway = gateway();

        List<float[]> vectors = gateway.embed(CurrentActor.of(1L), List.of("hello"));

        assertEquals(1, vectors.size());
        assertEquals(0.1f, vectors.get(0)[0]);
        verify(embeddingModel).embed(List.of("hello"));
    }

    @Test
    void embed_whenResultCountMismatches_shouldThrow() {
        when(embeddingModel.embed(anyList())).thenReturn(List.of());
        EmbeddingGatewayImpl gateway = gateway();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> gateway.embed(CurrentActor.of(1L), List.of("hello")));

        assertEquals("嵌入结果数量与文本数量不一致", ex.getMessage());
    }

    private EmbeddingGatewayImpl gateway() {
        return new EmbeddingGatewayImpl(embeddingModel);
    }
}
