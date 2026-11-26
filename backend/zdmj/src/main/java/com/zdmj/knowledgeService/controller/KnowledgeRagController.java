package com.zdmj.knowledgeService.controller;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zdmj.common.annotation.RateLimit;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.ProblemDetailSupport;
import com.zdmj.common.model.Result;
import com.zdmj.knowledgeService.dto.KnowledgeRetrievalRequest;
import com.zdmj.knowledgeService.api.KnowledgeRetrievalResponse;
import com.zdmj.knowledgeService.api.KnowledgeRagService;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 知识库排序检索，返回知识片段与查询信息供评测和调试。
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
     *
     * @param actor 已认证操作者，包含用户编号
     */
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 120, interval = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping
    public Result<KnowledgeRetrievalResponse> retrieve(@Parameter(hidden = true) CurrentActor actor, @Valid @RequestBody KnowledgeRetrievalRequest request) {
        return Result.success("检索成功", knowledgeRagService.retrieveRanked(actor,
                request.query(),
                request.ragDocumentIds(),
                request.useSystemKnowledge()));
    }

    /**
     * 将请求解析失败转换为参数错误，字段说明不包含原始输入。
     *
     * @param exception JSON 解析或请求构造失败
     * @param request 当前 HTTP 请求，仅用于设置错误地址
     * @return HTTP 400、业务码 1001 的 Problem Details
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> invalidRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        Throwable cause = exception.getMostSpecificCause();
        String detail = cause.getClass() == IllegalArgumentException.class
                ? cause.getMessage() : "检索请求必须为合法 JSON，query 为字符串，ragDocumentIds 为正整数数组，useSystemKnowledge 为布尔值";
        var problem = ProblemDetailSupport.of(
                ErrorCode.VALIDATION_ERROR, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.badRequest()
                .contentType(ProblemDetailSupport.PROBLEM_JSON).body(problem);
    }
}
