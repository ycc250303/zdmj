package com.zdmj.knowledgeService.controller;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.ProblemDetailSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Parameter;
import com.zdmj.common.context.CurrentActor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.Result;
import com.zdmj.common.model.UpdateGroup;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentPublicResponse;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentRequest;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentResponse;
import com.zdmj.knowledgeService.service.KnowledgeDocumentService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 知识库控制器
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/knowledge-document")
@Tag(name = "知识库文档", description = "文档上传、检索与管理")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    /**
     * 分页查询当前用户的知识文档
     *
     * @param actor 已认证操作者，包含用户编号
     * @param page  页码，从 1 开始，默认 1
     * @param limit 每页条数，默认 20，最大 100
     */
    @GetMapping
    public Result<PageDTO<KnowledgeDocumentPublicResponse>> listKnowledgeDocuments(@Parameter(hidden = true) CurrentActor actor,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit) {
        return Result.success("查询知识文档列表成功", knowledgeDocumentService.getByPage(actor, page, limit));
    }

    /**
     * 创建知识文档
     *
     * @param actor 已认证操作者，包含用户编号
     * @param request 知识文档请求
     * @return 知识文档
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<KnowledgeDocumentResponse> createKnowledgeDocument(@Parameter(hidden = true) CurrentActor actor,
            @Validated(CreateGroup.class) @RequestBody KnowledgeDocumentRequest request) {
        return Result.success("创建知识文档成功", knowledgeDocumentService.create(actor, request));
    }

    /**
     * 根据ID获取知识文档
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 知识文档ID
     * @return 知识文档
     */
    @GetMapping("/{id}")
    public Result<KnowledgeDocumentPublicResponse> getKnowledgeDocumentById(@Parameter(hidden = true) CurrentActor actor, @PathVariable Long id) {
        return Result.success("查询知识文档成功", knowledgeDocumentService.getPublicById(actor, id));
    }

    /**
     * 更新知识文档
     *
     * @param actor 已认证操作者，包含用户编号
     * @param request 知识文档请求
     * @return 知识文档
     */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<KnowledgeDocumentResponse> updateKnowledgeDocument(@Parameter(hidden = true) CurrentActor actor,
            @Validated(UpdateGroup.class) @RequestBody KnowledgeDocumentRequest request) {
        return Result.success("更新知识文档成功", knowledgeDocumentService.update(actor, request));
    }

    /**
     * 删除知识文档
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 知识文档ID
     * @return 知识文档
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteKnowledgeDocument(@Parameter(hidden = true) CurrentActor actor, @PathVariable Long id) {
        knowledgeDocumentService.delete(actor, id);
        return Result.success("删除知识文档成功", null);
    }
    /** 保留失败类型及已建立的文档编号，便于客户端查询、重试和删除。 */
    @ExceptionHandler(KnowledgeDocumentService.OperationFailure.class)
    public ResponseEntity<ProblemDetail> operationFailure(
            KnowledgeDocumentService.OperationFailure failure, HttpServletRequest request) {
        Throwable cause = failure.getCause();
        var code = cause instanceof BusinessException business
                ? business.getErrorCode() : ErrorCode.SYSTEM_EXCEPTION;
        var problem = ProblemDetailSupport.of(code,
                cause instanceof BusinessException ? cause.getMessage() : code.getMessage());
        problem.setProperty("documentId", failure.documentId());
        problem.setInstance(URI.create(request.getRequestURI()));
        log.error("知识文档处理失败: documentId={}", failure.documentId(), cause);
        return ResponseEntity.status(code.getHttpStatus())
                .contentType(ProblemDetailSupport.PROBLEM_JSON).body(problem);
    }

    /** multipart 上传知识文档；文件只从请求读取，操作者由认证参数注入。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeDocumentResponse> upload(@Parameter(hidden = true) CurrentActor actor,
            @Validated(CreateGroup.class) @ModelAttribute KnowledgeDocumentRequest request,
            @RequestParam("file") MultipartFile file) {
        return Result.success("知识文档保存并向量化成功", knowledgeDocumentService.upload(actor, request, file));
    }

    /** 原文件替换与标题更新在同一请求提交，文档编号保持不变。 */
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeDocumentResponse> replace(@Parameter(hidden = true) CurrentActor actor,
            @Validated(UpdateGroup.class) @ModelAttribute KnowledgeDocumentRequest request,
            @RequestParam("file") MultipartFile file) {
        return Result.success("知识文档替换并向量化成功", knowledgeDocumentService.replace(actor, request, file));
    }

    /** 校验归属后返回附件流，不向客户端暴露服务器文件位置。 */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @Parameter(hidden = true) CurrentActor actor, @PathVariable Long id) {
        var file = knowledgeDocumentService.openDownload(actor, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(file.input()));
    }

    /** 同步重新执行向量化，原文件可用时允许显式重试。 */
    @PostMapping("/{id}/embedding")
    public Result<KnowledgeDocumentResponse> retry(@Parameter(hidden = true) CurrentActor actor, @PathVariable Long id) {
        return Result.success("文档向量化成功", knowledgeDocumentService.retryEmbedding(actor, id));
    }
}
