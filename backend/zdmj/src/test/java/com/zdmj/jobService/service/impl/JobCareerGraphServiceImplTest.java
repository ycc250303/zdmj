package com.zdmj.jobService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.ai.ChatUtil;
import com.zdmj.jobService.dto.JobCareerGraphResponse;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.entity.JobCareerGraph;
import com.zdmj.jobService.service.JobService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobCareerGraphServiceImplTest {

    @Mock
    private JobService jobService;
    @Mock
    private ChatUtil chatUtil;

    private JobCareerGraphServiceImpl graphService;

    @BeforeEach
    void setUp() {
        graphService = spy(new JobCareerGraphServiceImpl(jobService, chatUtil, new ObjectMapper()));
        UserHolder.set(UserContext.of(1L, "u1"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void graph_generate_fail_shouldThrow10004() {
        Long jobId = 31L;
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doThrow(new RuntimeException("llm timeout")).when(chatUtil)
                .chatStructuredOnce(anyLong(), any(), any(), eq(null), eq(JobCareerGraphResponse.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> graphService.generate(jobId));

        assertEquals(ErrorCode.JOB_CAREER_GRAPH_GENERATION_FAILED.getCode(), ex.getCode());
        verify(graphService, never()).save(any(JobCareerGraph.class));
    }

    @Test
    void graph_generate_invalid_shouldThrow10005() {
        Long jobId = 32L;
        JobCareerGraphResponse invalidGraph = new JobCareerGraphResponse();
        invalidGraph.setVerticalPath(List.of(vNode(1, "初级"), vNode(2, "中级")));
        invalidGraph.setTransitionPaths(List.of());
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(invalidGraph).when(chatUtil).chatStructuredOnce(anyLong(), any(), any(), eq(null), eq(JobCareerGraphResponse.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> graphService.generate(jobId));

        assertEquals(ErrorCode.JOB_CAREER_GRAPH_INVALID.getCode(), ex.getCode());
    }

    @Test
    void graph_generate_success_shouldMarkCurrentAndSave() {
        Long jobId = 33L;
        JobCareerGraphResponse valid = buildValidGraphWithoutCurrent();
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(valid).when(chatUtil).chatStructuredOnce(anyLong(), any(), any(), eq(null), eq(JobCareerGraphResponse.class));
        doReturn(null).when(graphService).getOne(any(LambdaQueryWrapper.class));
        doReturn(true).when(graphService).save(any(JobCareerGraph.class));

        JobCareerGraphResponse result = graphService.generate(jobId);

        assertEquals(2, result.getCurrentNode().getLevel());
        assertEquals("中级Java工程师", result.getCurrentNode().getTitle());
        assertEquals("java-backend", result.getTargetRoleType());
        verify(graphService).save(any(JobCareerGraph.class));
    }

    @Test
    void graph_notFound_getOrNull_shouldReturnNullAndVerifyInteraction() {
        Long jobId = 34L;
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(null).when(graphService).getOne(any(LambdaQueryWrapper.class));

        JobCareerGraphResponse result = graphService.getOrNull(jobId);

        assertNull(result);
        verify(jobService).getDetail(jobId);
        verify(graphService).getOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void graph_getDetail_getOrNull_whenExists_shouldHydrateDto() {
        Long jobId = 35L;
        JobCareerGraph entity = new JobCareerGraph();
        entity.setJobId(jobId);
        entity.setSummary("summary");
        entity.setPromptName("JOB_CAREER_GRAPH_JAVA");
        entity.setCurrentNode("{\"level\":2,\"title\":\"中级工程师\"}");
        entity.setVerticalPath("[{\"level\":1,\"title\":\"初级\"},{\"level\":2,\"title\":\"中级\"},{\"level\":3,\"title\":\"高级\"}]");
        entity.setTransitionPaths(
                "[{\"name\":\"转产品\",\"targetRole\":\"产品经理\",\"nodes\":[{\"title\":\"Java工程师\"},{\"title\":\"产品经理\"}]}]");
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(entity).when(graphService).getOne(any(LambdaQueryWrapper.class));

        JobCareerGraphResponse dto = graphService.getOrNull(jobId);

        assertEquals(jobId, dto.getJobId());
        assertEquals("summary", dto.getSummary());
        assertEquals(2, dto.getCurrentNode().getLevel());
        assertEquals(3, dto.getVerticalPath().size());
        verify(jobService).getDetail(jobId);
        verify(graphService).getOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void graph_getDetail_getOrNull_whenJsonBroken_shouldReturnBasicDto() {
        Long jobId = 37L;
        JobCareerGraph entity = new JobCareerGraph();
        entity.setJobId(jobId);
        entity.setSummary("broken");
        entity.setPromptName("JOB_CAREER_GRAPH_JAVA");
        entity.setCurrentNode("not-json");
        entity.setVerticalPath("not-json");
        entity.setTransitionPaths("not-json");
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(entity).when(graphService).getOne(any(LambdaQueryWrapper.class));

        JobCareerGraphResponse dto = graphService.getOrNull(jobId);

        assertEquals(jobId, dto.getJobId());
        assertEquals("broken", dto.getSummary());
        assertEquals("JOB_CAREER_GRAPH_JAVA", dto.getTargetRoleType());
        assertNull(dto.getCurrentNode());
        assertNull(dto.getVerticalPath());
        assertNull(dto.getTransitionPaths());
    }

    @Test
    void graph_generate_update_whenExisting_shouldUpdateById() {
        Long jobId = 38L;
        JobCareerGraphResponse valid = buildValidGraphWithoutCurrent();
        JobCareerGraph existing = new JobCareerGraph();
        existing.setId(999L);
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(valid).when(chatUtil).chatStructuredOnce(anyLong(), any(), any(), eq(null), eq(JobCareerGraphResponse.class));
        doReturn(existing).when(graphService).getOne(any(LambdaQueryWrapper.class));
        doReturn(true).when(graphService).updateById(any(JobCareerGraph.class));

        JobCareerGraphResponse result = graphService.generate(jobId);

        assertEquals(jobId, result.getJobId());
        assertEquals(5, result.getTransitionPaths().size());
        verify(graphService).updateById(any(JobCareerGraph.class));
        verify(graphService, never()).save(any(JobCareerGraph.class));
    }

    @Test
    void graph_generate_whenCurrentNodeGiven_shouldPreserveLevelAndTitle() {
        Long jobId = 39L;
        JobCareerGraphResponse valid = buildValidGraphWithoutCurrent();
        JobCareerGraphResponse.CurrentNode current = new JobCareerGraphResponse.CurrentNode();
        current.setLevel(3);
        current.setTitle("高级Java工程师");
        valid.setCurrentNode(current);
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(valid).when(chatUtil).chatStructuredOnce(anyLong(), any(), any(), eq(null), eq(JobCareerGraphResponse.class));
        doReturn(null).when(graphService).getOne(any(LambdaQueryWrapper.class));
        doReturn(true).when(graphService).save(any(JobCareerGraph.class));

        JobCareerGraphResponse result = graphService.generate(jobId);

        assertEquals(3, result.getCurrentNode().getLevel());
        assertEquals("高级Java工程师", result.getCurrentNode().getTitle());
        assertTrue(result.getVerticalPath().stream()
                .anyMatch(v -> Boolean.TRUE.equals(v.getCurrent()) && v.getLevel() == 3));
    }

    @Test
    void graph_generate_fail_whenTransitionNodeTooShort_shouldThrow10005() {
        Long jobId = 36L;
        JobCareerGraphResponse invalidGraph = new JobCareerGraphResponse();
        invalidGraph.setVerticalPath(List.of(vNode(1, "初级"), vNode(2, "中级"), vNode(3, "高级")));
        List<JobCareerGraphResponse.TransitionPath> transitions = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            JobCareerGraphResponse.TransitionPath p = new JobCareerGraphResponse.TransitionPath();
            p.setName("路径" + i);
            p.setTargetRole("目标" + i);
            p.setNodes(i == 5 ? List.of(tNode("仅一个节点")) : List.of(tNode("节点A"), tNode("节点B")));
            transitions.add(p);
        }
        invalidGraph.setTransitionPaths(transitions);
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(invalidGraph).when(chatUtil).chatStructuredOnce(anyLong(), any(), any(), eq(null), eq(JobCareerGraphResponse.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> graphService.generate(jobId));

        assertEquals(ErrorCode.JOB_CAREER_GRAPH_INVALID.getCode(), ex.getCode());
        verify(graphService, never()).save(any(JobCareerGraph.class));
        verify(graphService, never()).updateById(any(JobCareerGraph.class));
    }

    @Test
    void graph_generate_fail_whenTransitionPathCountTooFew_shouldThrow10005() {
        Long jobId = 40L;
        JobCareerGraphResponse invalidGraph = new JobCareerGraphResponse();
        invalidGraph.setVerticalPath(List.of(vNode(1, "初级"), vNode(2, "中级"), vNode(3, "高级")));
        List<JobCareerGraphResponse.TransitionPath> transitions = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            JobCareerGraphResponse.TransitionPath p = new JobCareerGraphResponse.TransitionPath();
            p.setName("路径" + i);
            p.setTargetRole("目标" + i);
            p.setNodes(List.of(tNode("节点A"), tNode("节点B")));
            transitions.add(p);
        }
        invalidGraph.setTransitionPaths(transitions);
        doReturn(buildJobDetail()).when(jobService).getDetail(jobId);
        doReturn(invalidGraph).when(chatUtil).chatStructuredOnce(anyLong(), any(), any(), eq(null), eq(JobCareerGraphResponse.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> graphService.generate(jobId));

        assertEquals(ErrorCode.JOB_CAREER_GRAPH_INVALID.getCode(), ex.getCode());
        verify(graphService, never()).save(any(JobCareerGraph.class));
        verify(graphService, never()).updateById(any(JobCareerGraph.class));
    }

    private JobCareerGraphResponse buildValidGraphWithoutCurrent() {
        JobCareerGraphResponse dto = new JobCareerGraphResponse();
        dto.setSummary("可成长为架构师");
        dto.setVerticalPath(List.of(vNode(1, "初级Java工程师"), vNode(2, "中级Java工程师"), vNode(3, "高级Java工程师")));

        List<JobCareerGraphResponse.TransitionPath> transitions = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            JobCareerGraphResponse.TransitionPath path = new JobCareerGraphResponse.TransitionPath();
            path.setName("路径" + i);
            path.setTargetRole("目标岗位" + i);
            path.setNodes(List.of(tNode("当前岗位" + i), tNode("目标岗位" + i)));
            transitions.add(path);
        }
        dto.setTransitionPaths(transitions);
        return dto;
    }

    private JobCareerGraphResponse.VerticalPathNode vNode(int level, String title) {
        JobCareerGraphResponse.VerticalPathNode node = new JobCareerGraphResponse.VerticalPathNode();
        node.setLevel(level);
        node.setTitle(title);
        return node;
    }

    private JobCareerGraphResponse.TransitionNode tNode(String title) {
        JobCareerGraphResponse.TransitionNode node = new JobCareerGraphResponse.TransitionNode();
        node.setTitle(title);
        return node;
    }

    private JobListItemResponse buildJobDetail() {
        JobListItemResponse dto = new JobListItemResponse();
        dto.setJobName("Java后端");
        dto.setCompanyName("ZDMJ");
        dto.setDescription("Java Spring Redis");
        dto.setJobDuties(List.of("接口开发"));
        dto.setJobRequirements(List.of("熟悉Spring"));
        dto.setKeywords(List.of("java", "spring", "redis", "mysql"));
        dto.setCompanyIndustries(List.of("互联网"));
        return dto;
    }
}
