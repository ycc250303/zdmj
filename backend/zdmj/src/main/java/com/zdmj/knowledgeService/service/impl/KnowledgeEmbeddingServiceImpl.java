package com.zdmj.knowledgeService.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.zdmj.aiService.api.EmbeddingGateway;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.entity.KnowledgeVector;
import com.zdmj.knowledgeService.enums.KnowledgeTypeEnum;
import com.zdmj.knowledgeService.enums.KnowledgeVectorTaskStatusEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class KnowledgeEmbeddingServiceImpl implements KnowledgeEmbeddingService {

    private static final int MAX_BATCH_SIZE = 10;

    private final TextSplitter textSplitter;
    private final EmbeddingGateway embeddingGateway;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeVectorMapper knowledgeVectorMapper;
    private final PdfParserUtil pdfParserUtil;
    private final TransactionTemplate transactionTemplate;

    /**
     * 向量化一篇知识文档。
     * 先短事务标记为进行中，再在事务外抽文本、切块和嵌入；成功后短事务按当时的文档内容覆盖向量。
     * 失败时另开短事务写入失败状态。文档内容在远程调用期间变化时，不覆盖新内容，也不改写其状态。
     *
     * @param documentId 知识文档编号
     * @throws BusinessException 文档不存在、类型不支持、分块或向量结果非法，或保存失败
     */
    @Override
    public void vectorizeAndStore(Long documentId) {
        // 1. 获取知识文档
        KnowledgeDocument loaded = knowledgeDocumentMapper.selectById(documentId);
        if (loaded == null || loaded.getUserId() == null || loaded.getKnowledgeId() == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        Long userId = loaded.getUserId();
        String contentSnapshot = loaded.getContent();
        log.info("开始向量化并存储知识库: documentId={}, userId={}", documentId, userId);
        // 2. 标记为进行中
        if (markStatus(documentId, userId, contentSnapshot,
                KnowledgeVectorTaskStatusEnum.RUNNING.getCode(), null) != 1) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }

        try {
            // 3. 抽文本
            String rawText = extractText(loaded);
            // 4. 切块
            List<Document> chunks = textSplitter.apply(List.of(new Document(rawText)));
            if (chunks.isEmpty()) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED, "分块结果为空");
            }
            List<KnowledgeVector> rows = embedChunks(loaded, chunks);
            transactionTemplate.execute(status -> {
                saveVectors(documentId, userId, contentSnapshot, rows);
                return null;
            });
        } catch (RuntimeException exception) {
            markStatus(documentId, userId, contentSnapshot,
                    KnowledgeVectorTaskStatusEnum.FAILED.getCode(), exception.getMessage());
            throw exception;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVectors(Long DocumentId) {
        KnowledgeDocument kd = knowledgeDocumentMapper.selectById(DocumentId);
        if (kd == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        runDeleteTaskByUser(DocumentId, kd.getUserId());
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
        Integer updated = transactionTemplate.execute(status -> knowledgeDocumentMapper.update(null,
                new LambdaUpdateWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getId, documentId)
                        .eq(KnowledgeDocument::getUserId, userId)
                        .eq(KnowledgeDocument::getContent, contentSnapshot)
                        .set(KnowledgeDocument::getEmbeddingStatus, embeddingStatus)
                        .set(KnowledgeDocument::getLastError, lastError)));
        return updated == null ? 0 : updated;
    }

    /**
     * 调用嵌入模型并组装待写入的向量行，不访问数据库。
     *
     * @param source 开始向量化时读到的文档
     * @param chunks 文本分块
     * @return 与分块顺序一致的向量行
     */
    private List<KnowledgeVector> embedChunks(KnowledgeDocument source, List<Document> chunks) {
        int totalChunks = chunks.size();
        int batchCount = (totalChunks + MAX_BATCH_SIZE - 1) / MAX_BATCH_SIZE;
        List<KnowledgeVector> rows = new ArrayList<>(totalChunks);
        for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
            int start = batchIndex * MAX_BATCH_SIZE;
            int end = Math.min(start + MAX_BATCH_SIZE, totalChunks);
            List<String> batchTexts = chunks.subList(start, end).stream().map(Document::getText).toList();
            List<float[]> batchVectors = embeddingGateway.embed(CurrentActor.of(source.getUserId()), batchTexts);
            for (int j = 0; j < batchTexts.size(); j++) {
                String chunkText = batchTexts.get(j);
                KnowledgeVector row = new KnowledgeVector();
                row.setDocumentId(source.getId());
                row.setKnowledgeId(source.getKnowledgeId());
                row.setUserId(source.getUserId());
                row.setEmbedding(toPgVector(batchVectors.get(j)));
                row.setContent(chunkText);
                row.setChunkIndex(start + j);
                row.setChunkHash(sha256(chunkText));
                row.setTokenCount(estimateToken(chunkText));
                Map<String, Object> meta = new HashMap<>();
                meta.put("DocumentId", String.valueOf(source.getId()));
                meta.put("KnowledgeId", String.valueOf(source.getKnowledgeId()));
                meta.put("content", source.getContent());
                row.setMetadata(meta);
                rows.add(row);
            }
        }
        return rows;
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
        KnowledgeDocument current = knowledgeDocumentMapper.selectById(documentId);
        if (current == null || !userId.equals(current.getUserId()) || current.getKnowledgeId() == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        if (!Objects.equals(contentSnapshot, current.getContent())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED, "文档内容已变化");
        }
        knowledgeVectorMapper.deleteByDocumentIdAndUserId(documentId, userId);
        knowledgeVectorMapper.batchInsert(rows);
        current.setEmbeddingStatus(KnowledgeVectorTaskStatusEnum.SUCCESS.getCode());
        current.setChunkCount(rows.size());
        current.setLastEmbeddedAt(LocalDateTime.now());
        current.setLastError(null);
        current.setContentHash(sha256(current.getContent()));
        if (knowledgeDocumentMapper.updateById(current) != 1) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED);
        }
        log.info("向量化完成: documentId={}, chunks={}", documentId, rows.size());
    }

    @Override
    public String toPgVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private void runDeleteTaskByUser(Long DocumentId, Long userId) {
        try {
            knowledgeVectorMapper.deleteByDocumentIdAndUserId(DocumentId, userId);
            KnowledgeDocument kd = knowledgeDocumentMapper.selectById(DocumentId);
            if (kd != null && userId.equals(kd.getUserId())) {
                kd.setChunkCount(0);
                kd.setEmbeddingStatus(KnowledgeVectorTaskStatusEnum.PENDING.getCode());
                kd.setLastEmbeddedAt(null);
                kd.setLastError(null);
                knowledgeDocumentMapper.updateById(kd);
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_DELETE_FAILED);
        }
    }

    private String extractText(KnowledgeDocument kd) {
        if (kd.getType() != null && kd.getType().equals(KnowledgeTypeEnum.PROJECT_DOCUMENT.getCode())) {
            return pdfParserUtil.extractTextFromUrl(kd.getContent(), kd.getUserId());
        }
        throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED, "当前知识类型暂不支持向量化");
    }


    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest)
                hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 估计文本的token数量
     * 
     * @param text 输入文本
     * @return token数量
     */
    private static int estimateToken(String text) {
        if (text == null || text.isBlank())
            return 0;
        return Math.max(1, text.length() / 4);
    }
}
