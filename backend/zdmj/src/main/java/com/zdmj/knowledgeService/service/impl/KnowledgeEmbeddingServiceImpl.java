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
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.knowledgeService.entity.KnowledgeBases;
import com.zdmj.knowledgeService.entity.KnowledgeVector;
import com.zdmj.knowledgeService.entity.KnowledgeVectorTask;
import com.zdmj.knowledgeService.enums.KnowledgeTypeEnum;
import com.zdmj.knowledgeService.enums.KnowledgeVectorTaskStatusEnum;
import com.zdmj.knowledgeService.enums.KnowledgeVectorTaskTypeEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeBasesMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorTaskMapper;
import com.zdmj.knowledgeService.service.KnowledgeEmbeddingService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class KnowledgeEmbeddingServiceImpl implements KnowledgeEmbeddingService {

    /**
     * 最大批量大小（千问embedding模型限制）
     */
    private static final int MAX_BATCH_SIZE = 10;

    private final TextSplitter textSplitter;
    private final EmbeddingModel embeddingModel;
    private final KnowledgeBasesMapper knowledgeBasesMapper;
    private final KnowledgeVectorMapper knowledgeVectorMapper;
    private final KnowledgeVectorTaskMapper knowledgeVectorTaskMapper;

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
    public Long submitVectorizeTask(Long knowledgeId, Long userId) {
        KnowledgeVectorTask task = new KnowledgeVectorTask();
        task.setKnowledgeId(knowledgeId);
        task.setUserId(userId);
        task.setTaskType(KnowledgeVectorTaskTypeEnum.EMBEDDING.getCode());
        task.setStatus(KnowledgeVectorTaskStatusEnum.PENDING.getCode());
        knowledgeVectorTaskMapper.insert(task);
        return task.getId();
    }

    @Override
    public Long submitDeleteTask(Long knowledgeId, Long userId) {
        KnowledgeVectorTask task = new KnowledgeVectorTask();
        task.setKnowledgeId(knowledgeId);
        task.setUserId(userId);
        task.setTaskType(KnowledgeVectorTaskTypeEnum.DELETE.getCode());
        task.setStatus(KnowledgeVectorTaskStatusEnum.PENDING.getCode());
        knowledgeVectorTaskMapper.insert(task);
        return task.getId();
    }

    @Override
    @Async("embeddingExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void executeTaskAsync(Long taskId) {
        KnowledgeVectorTask task = knowledgeVectorTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        if (task.getStatus() == null || task.getStatus() != KnowledgeVectorTaskStatusEnum.PENDING.getCode()) {
            return;
        }
        task.setStatus(KnowledgeVectorTaskStatusEnum.RUNNING.getCode());
        task.setStartedAt(LocalDateTime.now());
        knowledgeVectorTaskMapper.updateById(task);

        try {
            if (task.getTaskType() != null && task.getTaskType() == KnowledgeVectorTaskTypeEnum.EMBEDDING.getCode()) {
                runEmbeddingTask(task);
            } else if (task.getTaskType() != null
                    && task.getTaskType() == KnowledgeVectorTaskTypeEnum.DELETE.getCode()) {
                runDeleteTask(task);
            } else {
                throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED.getCode(), "未知任务类型");
            }
            task.setStatus(KnowledgeVectorTaskStatusEnum.SUCCESS.getCode());
            task.setCompletedAt(LocalDateTime.now());
            task.setErrorMessage(null);
            knowledgeVectorTaskMapper.updateById(task);
        } catch (Exception e) {
            task.setStatus(KnowledgeVectorTaskStatusEnum.FAILED.getCode());
            task.setCompletedAt(LocalDateTime.now());
            task.setErrorMessage(e.getMessage());
            knowledgeVectorTaskMapper.updateById(task);
            log.error("异步向量任务执行失败: taskId={}, knowledgeId={}, error={}",
                    taskId, task.getKnowledgeId(), e.getMessage(), e);
        }
    }

    /**
     * 向量化并存储知识库(先删除旧向量后向量化)
     * 
     * @param knowledgeId 知识库ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void vectorizeAndStore(Long knowledgeId) {
        KnowledgeBases kb = knowledgeBasesMapper.selectById(knowledgeId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        runEmbeddingTaskByUser(knowledgeId, kb.getUserId());

    }

    /**
     * 删除知识库向量
     * 
     * @param knowledgeId 知识库ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVectors(Long knowledgeId) {
        KnowledgeBases kb = knowledgeBasesMapper.selectById(knowledgeId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        runDeleteTaskByUser(knowledgeId, kb.getUserId());
    }

    private void runEmbeddingTask(KnowledgeVectorTask task) {
        runEmbeddingTaskByUser(task.getKnowledgeId(), task.getUserId());
    }

    private void runEmbeddingTaskByUser(Long knowledgeId, Long userId) {
        log.info("开始向量化并存储知识库: knowledgeId={}, userId={}", knowledgeId, userId);
        KnowledgeBases kb = knowledgeBasesMapper.selectById(knowledgeId);
        if (kb == null || !userId.equals(kb.getUserId())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        kb.setEmbeddingStatus(KnowledgeVectorTaskStatusEnum.RUNNING.getCode());
        kb.setLastError(null);
        knowledgeBasesMapper.updateById(kb);

        try {
            knowledgeVectorMapper.deleteByKnowledgeIdAndUserId(knowledgeId, userId);
            String rawText = extractText(kb);
            List<Document> chunks = textSplitter.apply(List.of(new Document(rawText)));
            if (chunks.isEmpty()) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED.getCode(), "分块结果为空");
            }

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
                            ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED.getCode(),
                            "批量向量化结果数量异常");
                }
                List<KnowledgeVector> rows = new ArrayList<>(batchTexts.size());
                for (int j = 0; j < batchTexts.size(); j++) {
                    int chunkIndex = start + j;
                    String chunkText = batchTexts.get(j);
                    float[] vec = batchVectors.get(j);
                    KnowledgeVector kv = new KnowledgeVector();
                    kv.setKnowledgeId(knowledgeId);
                    kv.setUserId(userId);
                    kv.setEmbedding(toPgVector(vec));
                    kv.setContent(chunkText);
                    kv.setChunkIndex(chunkIndex);
                    kv.setChunkHash(sha256(chunkText));
                    kv.setTokenCount(estimateToken(chunkText));
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("knowledgeId", String.valueOf(knowledgeId));
                    meta.put("source", kb.getContent());
                    kv.setMetadata(meta);
                    rows.add(kv);
                }
                knowledgeVectorMapper.batchInsert(rows);
                persistedCount += rows.size();
            }

            kb.setEmbeddingStatus(KnowledgeVectorTaskStatusEnum.SUCCESS.getCode());
            kb.setChunkCount(persistedCount);
            kb.setLastEmbeddedAt(LocalDateTime.now());
            kb.setLastError(null);
            kb.setContentHash(sha256(kb.getContent()));
            knowledgeBasesMapper.updateById(kb);
            log.info("向量化完成: knowledgeId={}, chunks={}, batches={}", knowledgeId, persistedCount, batchCount);
        } catch (Exception e) {
            kb.setEmbeddingStatus(KnowledgeVectorTaskStatusEnum.FAILED.getCode());
            kb.setLastError(e.getMessage());
            knowledgeBasesMapper.updateById(kb);
            throw e;
        }
    }

    private void runDeleteTask(KnowledgeVectorTask task) {
        runDeleteTaskByUser(task.getKnowledgeId(), task.getUserId());
    }

    private void runDeleteTaskByUser(Long knowledgeId, Long userId) {
        try {
            knowledgeVectorMapper.deleteByKnowledgeIdAndUserId(knowledgeId, userId);
            KnowledgeBases kb = knowledgeBasesMapper.selectById(knowledgeId);
            if (kb != null && userId.equals(kb.getUserId())) {
                kb.setChunkCount(0);
                kb.setEmbeddingStatus(KnowledgeVectorTaskStatusEnum.PENDING.getCode());
                kb.setVectorTaskId(null);
                kb.setLastEmbeddedAt(null);
                kb.setLastError(null);
                knowledgeBasesMapper.updateById(kb);
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_DELETE_FAILED);
        }
    }

    private String extractText(KnowledgeBases kb) {
        if (kb.getType() != null && kb.getType().equals(KnowledgeTypeEnum.PROJECT_DOCUMENT.getCode())) {
            return PdfParserUtil.extractTextFromUrl(kb.getContent());
        }
        throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED.getCode(), "当前知识类型暂不支持向量化");
    }

    private static String toPgVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
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

    private static int estimateToken(String text) {
        if (text == null || text.isBlank())
            return 0;
        return Math.max(1, text.length() / 4);
    }
}
