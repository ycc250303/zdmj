package com.zdmj.knowledgeService.controller;

import java.util.concurrent.TimeUnit;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zdmj.common.annotation.RateLimit;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.model.Result;
import com.zdmj.knowledgeService.dto.KnowledgeRetrievalRequest;
import com.zdmj.knowledgeService.dto.KnowledgeRetrievalResponse;
import com.zdmj.knowledgeService.service.KnowledgeRagService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 知识库排序检索（不含生成、不含整篇展开）。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/knowledge/retrievals")
@Validated
@Tag(name = "知识库检索", description = "返回 RAG 排序层命中列表，供评测与调试")
public class KnowledgeRagController {

    private final KnowledgeRagService knowledgeRagService;

    /**
     * 对当前用户执行与对话 RAG 相同的排序检索。
     */
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 120, interval = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping
    public Result<KnowledgeRetrievalResponse> retrieve(@Valid @RequestBody KnowledgeRetrievalRequest request) {
        return Result.success("检索成功", knowledgeRagService.retrieveRanked(
                UserHolder.requireUserId(),
                request.getQuery(),
                request.getRagDocumentIds(),
                request.isUseSystemKnowledge()));
    }
}
