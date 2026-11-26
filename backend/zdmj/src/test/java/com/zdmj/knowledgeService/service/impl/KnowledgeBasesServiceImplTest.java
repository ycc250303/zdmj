package com.zdmj.knowledgeService.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.knowledgeService.entity.KnowledgeBases;
import com.zdmj.knowledgeService.mapper.KnowledgeBasesMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeBasesServiceImplTest {
    @Mock private KnowledgeBasesMapper mapper;
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final CurrentActor actor = new CurrentActor(1L);

    @org.junit.jupiter.api.BeforeEach
    void 用户尚未建立私有知识库() {
        when(mapper.selectKnowledgeIdByUserId(1L)).thenReturn(null);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void 首次创建或并发已创建_读取同一个知识库(int inserted) {
        when(mapper.selectKnowledgeIdByUserId(1L)).thenReturn(null, 10L);
        when(mapper.insertUserBaseIfAbsent(1L, LocalDateTime.now(clock))).thenReturn(inserted);
        KnowledgeBases base = new KnowledgeBases();
        base.setId(10L); base.setUserId(1L); base.setScope(1);
        when(mapper.selectByUserId(1L)).thenReturn(base);
        assertThat(new KnowledgeBasesServiceImpl(mapper, clock).create(actor).id()).isEqualTo(10L);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2})
    void 插入返回异常数量_立即终止(int rows) {
        when(mapper.insertUserBaseIfAbsent(eq(1L), any())).thenReturn(rows);
        assertThatThrownBy(() -> new KnowledgeBasesServiceImpl(mapper, clock).getOrCreateKnowledgeBaseId(actor))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("插入数量");
    }

    @Test
    void 插入后仍缺少知识库_暴露实现异常() {
        when(mapper.insertUserBaseIfAbsent(eq(1L), any())).thenReturn(0);
        assertThatThrownBy(() -> new KnowledgeBasesServiceImpl(mapper, clock).getOrCreateKnowledgeBaseId(actor))
                .isInstanceOf(IllegalStateException.class).hasMessage("用户知识库未成功建立");
    }

    @Test
    void 数据库故障_保留原始异常且停止查询() {
        var failure = new org.springframework.dao.DataIntegrityViolationException("数据库故障");
        when(mapper.insertUserBaseIfAbsent(eq(1L), any())).thenThrow(failure);
        assertThatThrownBy(() -> new KnowledgeBasesServiceImpl(mapper, clock).getOrCreateKnowledgeBaseId(actor))
                .isSameAs(failure);
        org.mockito.Mockito.verify(mapper).selectKnowledgeIdByUserId(1L);
        org.mockito.Mockito.verify(mapper).insertUserBaseIfAbsent(1L, LocalDateTime.now(clock));
        verifyNoMoreInteractions(mapper);
    }
}
