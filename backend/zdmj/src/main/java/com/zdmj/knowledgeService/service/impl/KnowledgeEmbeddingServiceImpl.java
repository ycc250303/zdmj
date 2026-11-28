package com.zdmj.knowledgeService.service.impl;

import com.zdmj.common.storage.FileUploadService;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Function;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import java.io.InputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.zdmj.aiService.api.EmbeddingGateway;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.knowledgeService.config.KnowledgeDocumentConfig;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.enums.KnowledgeScopeEnum;
import com.zdmj.knowledgeService.entity.KnowledgeVector;
import com.zdmj.knowledgeService.enums.KnowledgeTypeEnum;
import com.zdmj.knowledgeService.enums.KnowledgeEmbeddingStatusEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class KnowledgeEmbeddingServiceImpl implements KnowledgeEmbeddingService {

    private static final int EMBEDDING_BATCH_SIZE = 10;
    private static final int INSERT_BATCH_SIZE = 500;

    private final TextSplitter textSplitter;
    private final EmbeddingGateway embeddingGateway;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeVectorMapper knowledgeVectorMapper;
    private final PdfParserUtil pdfParserUtil;
    private final FileUploadService fileUploadService;
    private final TransactionTemplate transactionTemplate;
    private final KnowledgeDocumentConfig documentConfig;
    private final Clock clock;

    /**
     * 向量化一篇知识文档。
     * 先短事务标记为进行中，再在事务外抽文本、切块和嵌入；成功后短事务按当时的文档内容覆盖向量。
     * 失败时另开短事务写入失败状态。文档内容在远程调用期间变化时，不覆盖新内容，也不改写其状态。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param documentId 知识文档编号
     * @throws BusinessException 文档不存在、类型不支持、分块或向量结果非法，或保存失败
     */
    @Override
    public void vectorizeAndStore(CurrentActor actor, Long documentId) {
        // 1. 获取知识文档
        KnowledgeDocument loaded = knowledgeDocumentMapper.selectById(documentId);
        if (loaded == null || !actor.userId().equals(loaded.getUserId()) || !Objects.equals(loaded.getScope(), KnowledgeScopeEnum.USER.getCode())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND);
        }
        Long userId = loaded.getUserId();
        String contentSnapshot = loaded.getContent();
        log.info("开始向量化并存储知识库: documentId={}, userId={}", documentId, userId);
        // 2. 标记为进行中
        if (markStatus(documentId, userId, contentSnapshot,
                KnowledgeEmbeddingStatusEnum.RUNNING.getCode(), null) != 1) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_STATE_CONFLICT);
        }

        try {
            // 3. 抽文本
            String rawText = extractText(actor, loaded);
            List<KnowledgeVector> rows = generateVectors(textSplitter, rawText, texts -> embeddingGateway.embed(actor, texts));
            transactionTemplate.execute(status -> {
                saveVectors(documentId, userId, contentSnapshot, rows);
                return null;
            });
            log.info("向量化完成: documentId={}, chunks={}", documentId, rows.size());
        } catch (RuntimeException exception) {
            try {
                markStatus(documentId, userId, contentSnapshot,
                        KnowledgeEmbeddingStatusEnum.FAILED.getCode(), "文档处理失败，请重新处理");
            } catch (RuntimeException markingFailure) {
                exception.addSuppressed(markingFailure);
            }
            throw exception;
        }
    }

    /**
     * 按文档、用户和开始向量化时的内容更新状态。内容已变化时影响行数为 0。
     *
     * @param documentId 知识文档编号
     * @param userId 文档所属用户
     * @param contentSnapshot 开始向量化时的来源地址
     * @param embeddingStatus 目标状态码
     * @param lastError 失败摘要；进行中时为空
     * @return 更新行数
     */
    private int markStatus(Long documentId, Long userId, String contentSnapshot, Integer embeddingStatus, String lastError) {
        var update = new LambdaUpdateWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getId, documentId)
                        .eq(KnowledgeDocument::getScope, KnowledgeScopeEnum.USER.getCode())
                        .eq(KnowledgeDocument::getUserId, userId)
                        .eq(KnowledgeDocument::getContent, contentSnapshot)
                        .set(KnowledgeDocument::getEmbeddingStatus, embeddingStatus)
                        .eq(KnowledgeDocument::getFileStatus, 1)
                        .set(KnowledgeDocument::getUpdatedAt, LocalDateTime.now(clock))
                        .set(KnowledgeDocument::getLastError, lastError);
        if (embeddingStatus.equals(KnowledgeEmbeddingStatusEnum.RUNNING.getCode())) {
            update.ne(KnowledgeDocument::getEmbeddingStatus, KnowledgeEmbeddingStatusEnum.RUNNING.getCode());
        } else {
            update.eq(KnowledgeDocument::getEmbeddingStatus, KnowledgeEmbeddingStatusEnum.RUNNING.getCode());
        }
        Integer updated = transactionTemplate.execute(status -> knowledgeDocumentMapper.update(null, update));
        return updated == null ? 0 : updated;
    }

    /**
     * 将有限且非空的向量转换为 PostgreSQL 字面量，供入库与检索共用。
     *
     * @param vector 非空且各元素均为有限数值的向量
     * @return pgvector 格式的向量字面量
     * @throws IllegalArgumentException 向量为空或包含非有限数值
     */
    static String toPgVector(float[] vector) {
        if (vector == null || vector.length == 0) { throw new IllegalArgumentException("查询向量不能为空"); }
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (!Float.isFinite(vector[index])) { throw new IllegalArgumentException("向量必须为有限数值"); }
            if (index > 0) { result.append(','); }
            result.append(vector[index]);
        }
        return result.append(']').toString();
    }

    /**
     * 在当前事务中确认文档仍属于原用户且来源地址未变，然后覆盖向量并标记成功。
     *
     * @param documentId 知识文档编号
     * @param userId 开始向量化时的用户
     * @param contentSnapshot 开始向量化时的来源地址
     * @param rows 已算好的向量行
     */
    private void saveVectors(Long documentId, Long userId, String contentSnapshot, List<KnowledgeVector> rows) {
        KnowledgeDocument current = knowledgeDocumentMapper.selectOwnedForUpdate(documentId, userId);
        if (current == null || !userId.equals(current.getUserId()) || !Objects.equals(current.getScope(), KnowledgeScopeEnum.USER.getCode())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND);
        }
        if (!Objects.equals(contentSnapshot, current.getContent()) || !Objects.equals(current.getFileStatus(), 1)
                || !Objects.equals(current.getEmbeddingStatus(), KnowledgeEmbeddingStatusEnum.RUNNING.getCode())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_EMBEDDING_FAILED, "文档内容已变化");
        }
        replaceUser(knowledgeVectorMapper, documentId, userId, rows);
        LocalDateTime completedAt = LocalDateTime.now(clock);
        current.setEmbeddingStatus(KnowledgeEmbeddingStatusEnum.SUCCESS.getCode());
        current.setChunkCount(rows.size());
        current.setLastEmbeddedAt(completedAt);
        current.setLastError(null);
        if (knowledgeDocumentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getScope, KnowledgeScopeEnum.USER.getCode()).eq(KnowledgeDocument::getUserId, userId)
                .eq(KnowledgeDocument::getContent, contentSnapshot)
                .eq(KnowledgeDocument::getFileStatus, 1)
                .eq(KnowledgeDocument::getEmbeddingStatus, KnowledgeEmbeddingStatusEnum.RUNNING.getCode())
                .set(KnowledgeDocument::getEmbeddingStatus, current.getEmbeddingStatus())
                .set(KnowledgeDocument::getChunkCount, rows.size())
                .set(KnowledgeDocument::getLastEmbeddedAt, current.getLastEmbeddedAt())
                .set(KnowledgeDocument::getLastError, null)
                .set(KnowledgeDocument::getUpdatedAt, completedAt)) != 1) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_EMBEDDING_FAILED);
        }
    }

    /**
     * 读取知识文档原文件，按知识库配置的容量提取完整文本。
     *
     * @param actor 已认证操作者
     * @param document 已完成归属校验的知识文档
     * @return 规范化后的完整文本
     * @throws BusinessException 类型不支持、文本超过配置容量、文件读取失败或解析失败
     */
    private String extractText(CurrentActor actor, KnowledgeDocument document) {
        if (!Objects.equals(document.getType(), KnowledgeTypeEnum.PROJECT_DOCUMENT.getCode())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_EMBEDDING_FAILED, "当前知识类型暂不支持向量化");
        }
        try (InputStream input = fileUploadService.openInputStream(actor, document.getContent())) {
            return pdfParserUtil.extractText(input, documentConfig.maxExtractedChars());
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_UNAVAILABLE, "知识文档原文件读取失败", exception);
        }
    }

    /**
     * 在事务外完成全文分块、分批嵌入与片段组装，任意批次失败立即终止。
     * @param textSplitter 两种文档流程共用的分块器
     * @param text 统一解析器规范化后的全文
     * @param embed 用户业务或服务器命令行提供的嵌入调用
     * @return 按正文顺序生成的片段，文档关联及来源在保存时设置
     * @throws BusinessException 分块结果为空
     * @throws IllegalStateException 模型返回的向量数量与文本数量不一致
     */
    static List<KnowledgeVector> generateVectors(TextSplitter textSplitter, String text, Function<List<String>, List<float[]>> embed) {
        List<Document> chunks = textSplitter.apply(List.of(new Document(text)));
        if (chunks.isEmpty()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_EMBEDDING_FAILED, "分块结果为空");
        }
        List<KnowledgeVector> rows = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += EMBEDDING_BATCH_SIZE) {
            var texts = chunks.subList(start, Math.min(start + EMBEDDING_BATCH_SIZE, chunks.size()))
                    .stream().map(Document::getText).toList();
            var embeddings = embed.apply(texts);
            if (embeddings == null || embeddings.size() != texts.size()) {
                throw new IllegalStateException("嵌入结果数量与文本数量不一致");
            }
            for (int index = 0; index < texts.size(); index++) {
                String content = texts.get(index);
                var row = new KnowledgeVector();
                row.setEmbedding(toPgVector(embeddings.get(index)));
                row.setContent(content);
                row.setChunkIndex(start + index);
                row.setChunkHash(hash(content));
                row.setTokenCount(content.isBlank() ? 0 : Math.max(1, content.length() / 4));
                rows.add(row);
            }
        }
        return rows;
    }

    /** 在调用方事务内替换已锁定的本人文档片段，删除同时限定私有范围与用户归属。 */
    static void replaceUser(KnowledgeVectorMapper vectors, Long documentId, Long userId, List<KnowledgeVector> rows) {
        requireRows(rows);
        vectors.deleteByDocumentIdAndUserId(documentId, userId);
        write(vectors, documentId, rows, Map.of("DocumentId", String.valueOf(documentId),
                "source", "/knowledge-document/" + documentId + "/download"));
    }

    /**
     * 在调用方事务内替换已锁定的系统文档片段，核对删除数量后写入全部批次。
     * @param vectors 当前事务使用的片段Mapper
     * @param documentId 已锁定或本事务新增的系统文档编号
     * @param expectedChunks 比较阶段读取的旧片段数量，零表示没有旧片段
     * @param sourceUrl 已校验的原文地址
     * @param rows 已完成嵌入的非空片段
     * @throws IllegalArgumentException 待写入片段为空
     * @throws IllegalStateException 删除或任意批次插入数量异常，由调用方事务回滚
     */
    static void replaceSystem(KnowledgeVectorMapper vectors, Long documentId, int expectedChunks, String sourceUrl, List<KnowledgeVector> rows) {
        requireRows(rows);
        if (expectedChunks > 0) { deleteSystem(vectors, documentId, expectedChunks); }
        write(vectors, documentId, rows, Map.of("source", sourceUrl));
    }

    /** 删除已锁定系统文档的旧片段，SQL限定受控来源，数量异常由调用方事务回滚。 */
    static void deleteSystem(KnowledgeVectorMapper vectors, Long documentId, int expectedChunks) {
        if (vectors.deleteSystemByDocumentId(documentId) != expectedChunks) {
            throw new IllegalStateException("系统旧片段删除数量与读取数量不一致，文档编号=" + documentId);
        }
    }

    private static void write(KnowledgeVectorMapper vectors, Long documentId, List<KnowledgeVector> rows, Map<String, Object> metadata) {
        for (var row : rows) {
            row.setDocumentId(documentId);
            row.setMetadata(metadata);
        }
        for (int start = 0; start < rows.size(); start += INSERT_BATCH_SIZE) {
            var batch = rows.subList(start, Math.min(start + INSERT_BATCH_SIZE, rows.size()));
            int inserted = vectors.batchInsert(batch);
            if (inserted != batch.size()) {
                throw new IllegalStateException("向量写入数量与分块数量不一致，expected=" + batch.size() + "，actual=" + inserted);
            }
        }
    }

    private static void requireRows(List<KnowledgeVector> rows) {
        if (rows == null || rows.isEmpty()) { throw new IllegalArgumentException("待写入片段不能为空"); }
    }

    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
