package com.zdmj.knowledgeService.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.entity.KnowledgeVector;
import com.zdmj.knowledgeService.entity.KnowledgeVectorTask;
import com.zdmj.knowledgeService.enums.KnowledgeTypeEnum;
import com.zdmj.knowledgeService.enums.KnowledgeVectorTaskStatusEnum;
import com.zdmj.knowledgeService.enums.KnowledgeVectorTaskTypeEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorTaskMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class KnowledgeEmbeddingServiceImpl implements KnowledgeEmbeddingService {

    private static final int MAX_BATCH_SIZE = 10;

    private final TextSplitter textSplitter;
    private final EmbeddingModel embeddingModel;
    private final KnowledgeBasesService knowledgeBasesService;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeVectorMapper knowledgeVectorMapper;
    private final KnowledgeVectorTaskMapper knowledgeVectorTaskMapper;
    private final PdfParserUtil pdfParserUtil;

    @PostConstruct
    public void resumePendingTasks() {
        List<KnowledgeVectorTask> pendingTasks = knowledgeVectorTaskMapper.selectList(
                new LambdaQueryWrapper<KnowledgeVectorTask>()
                        .eq(KnowledgeVectorTask::getStatus, KnowledgeVectorTaskStatusEnum.PENDING.getCode()));
        for (KnowledgeVectorTask task : pendingTasks) {
            executeTaskAsync(task.getId());
        }
    }

    @Override
    public Long submitVectorizeTask(Long DocumentId) {
        Long userId = UserHolder.requireUserId();
        Long knowledgeId = knowledgeBasesService.getOrCreateKnowledgeBaseId();
        KnowledgeVectorTask task = new KnowledgeVectorTask();
        task.setDocumentId(DocumentId);
        task.setUserId(userId);
        task.setKnowledgeId(knowledgeId);
        task.setTaskType(KnowledgeVectorTaskTypeEnum.EMBEDDING.getCode());
        task.setStatus(KnowledgeVectorTaskStatusEnum.PENDING.getCode());
        knowledgeVectorTaskMapper.insert(task);
        return task.getId();
    }

    @Override
    public Long submitDeleteTask(Long DocumentId) {
        Long userId = UserHolder.requireUserId();
        Long knowledgeId = knowledgeBasesService.getOrCreateKnowledgeBaseId();
        KnowledgeVectorTask task = new KnowledgeVectorTask();
        task.setDocumentId(DocumentId);
        task.setUserId(userId);
        task.setKnowledgeId(knowledgeId);
        task.setTaskType(KnowledgeVectorTaskTypeEnum.DELETE.getCode());
        task.setStatus(KnowledgeVectorTaskStatusEnum.PENDING.getCode());
        knowledgeVectorTaskMapper.insert(task);
        return task.getId();
    }

    @Override
    @Async("embeddingExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void executeTaskAsync(Long taskId) {
        int claimed = knowledgeVectorTaskMapper.claimPendingTask(taskId);
        if (claimed != 1) {
            return;
        }

        KnowledgeVectorTask task = knowledgeVectorTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("异步向量任务不存在，跳过执行: taskId={}", taskId);
            return;
        }

        try {
            if (task.getTaskType() != null && task.getTaskType() == KnowledgeVectorTaskTypeEnum.EMBEDDING.getCode()) {
                runEmbeddingTaskByUser(task.getDocumentId(), task.getUserId());
            } else if (task.getTaskType() != null
                    && task.getTaskType() == KnowledgeVectorTaskTypeEnum.DELETE.getCode()) {
                runDeleteTaskByUser(task.getDocumentId(), task.getUserId());
            } else {
                throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED, "未知任务类型");
            }
            knowledgeVectorTaskMapper.markTaskSuccess(taskId);
        } catch (Exception e) {
            knowledgeVectorTaskMapper.markTaskFailed(taskId, e.getMessage());
            log.error("异步向量任务执行失败: taskId={}, DocumentId={}, error={}",
                    taskId, task.getDocumentId(), e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void vectorizeAndStore(Long DocumentId) {
        KnowledgeDocument kd = knowledgeDocumentMapper.selectById(DocumentId);
        if (kd == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        runEmbeddingTaskByUser(DocumentId, kd.getUserId());
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
     * 单文档向量化：抽文本 → 切块 → 批量 Embedding → 写入 knowledge_vectors。
     * 事务由调用方（executeTaskAsync / vectorizeAndStore）的 {@code @Transactional} 覆盖；
     * 本方法为 private，加注解不会生效。
     */
    private void runEmbeddingTaskByUser(Long DocumentId, Long userId) {
        log.info("开始向量化并存储知识库: DocumentId={}, userId={}", DocumentId, userId);

        // --- 1. 校验：文档存在且归属于任务上的 userId（异步线程无 UserHolder） ---
        KnowledgeDocument kd = knowledgeDocumentMapper.selectById(DocumentId);
        if (kd == null || !userId.equals(kd.getUserId())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        Long knowledgeId = kd.getKnowledgeId();
        if (knowledgeId == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }

        // --- 2. 状态：先标 RUNNING，前端可轮询 embeddingStatus ---
        kd.setEmbeddingStatus(KnowledgeVectorTaskStatusEnum.RUNNING.getCode());
        kd.setLastError(null);
        knowledgeDocumentMapper.updateById(kd);

        try {
            // --- 3. 清旧向量：重跑/更新文档时保证「一份文档一份当前切片」 ---
            knowledgeVectorMapper.deleteByDocumentIdAndUserId(DocumentId, userId);

            // --- 4. 抽文本：content 存的是 COS URL，Tika 按类型取出纯文本 ---
            String rawText = extractText(kd);

            // --- 5. 切块：TokenTextSplitter 默认 800 token/块 ---
            List<Document> chunks = textSplitter.apply(List.of(new Document(rawText)));
            if (chunks.isEmpty()) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED, "分块结果为空");
            }

            // --- 6. 批量 Embedding + 落库：每批 ≤10，控制厂商限流与失败粒度 ---
            int totalChunks = chunks.size();
            int batchCount = (totalChunks + MAX_BATCH_SIZE - 1) / MAX_BATCH_SIZE;
            int persistedCount = 0;
            for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
                int start = batchIndex * MAX_BATCH_SIZE;
                int end = Math.min(start + MAX_BATCH_SIZE, totalChunks);
                List<Document> batchDocs = chunks.subList(start, end);
                List<String> batchTexts = batchDocs.stream().map(Document::getText).toList();
                List<float[]> batchVectors = embeddingModel.embed(batchTexts);
                if (batchVectors == null || batchVectors.size() != batchTexts.size()) {
                    throw new BusinessException(
                            ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED,
                            "批量向量化结果数量异常");
                }
                List<KnowledgeVector> rows = new ArrayList<>(batchTexts.size());
                for (int j = 0; j < batchTexts.size(); j++) {
                    int chunkIndex = start + j;
                    String chunkText = batchTexts.get(j);
                    float[] vec = batchVectors.get(j);
                    KnowledgeVector kv = new KnowledgeVector();
                    kv.setDocumentId(DocumentId);
                    kv.setKnowledgeId(knowledgeId);
                    kv.setUserId(userId);
                    kv.setEmbedding(toPgVector(vec));
                    kv.setContent(chunkText);
                    kv.setChunkIndex(chunkIndex);
                    kv.setChunkHash(sha256(chunkText));
                    kv.setTokenCount(estimateToken(chunkText));
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("DocumentId", String.valueOf(DocumentId));
                    meta.put("KnowledgeId", String.valueOf(knowledgeId));
                    meta.put("content", kd.getContent());
                    kv.setMetadata(meta);
                    rows.add(kv);
                }
                knowledgeVectorMapper.batchInsert(rows);
                persistedCount += rows.size();
            }

            // --- 7. 回写文档：success + 块数 + 内容哈希（供以后判断是否需要重嵌） ---
            kd.setEmbeddingStatus(KnowledgeVectorTaskStatusEnum.SUCCESS.getCode());
            kd.setChunkCount(persistedCount);
            kd.setLastEmbeddedAt(LocalDateTime.now());
            kd.setLastError(null);
            kd.setContentHash(sha256(kd.getContent()));
            knowledgeDocumentMapper.updateById(kd);
            log.info("向量化完成: DocumentId={}, chunks={}, batches={}", DocumentId, persistedCount, batchCount);
        } catch (Exception e) {
            // 文档标 FAILED 后上抛；外层 executeTaskAsync 吞异常并 markTaskFailed，事务仍提交。
            kd.setEmbeddingStatus(KnowledgeVectorTaskStatusEnum.FAILED.getCode());
            kd.setLastError(e.getMessage());
            knowledgeDocumentMapper.updateById(kd);
            throw e;
        }
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
