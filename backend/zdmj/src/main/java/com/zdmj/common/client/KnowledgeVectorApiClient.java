package com.zdmj.common.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.model.Result;
import com.zdmj.knowledgeService.dto.KnowledgeEmbeddingTaskCreateDTO;
import com.zdmj.knowledgeService.dto.KnowledgeEmbeddingTaskDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeVectorApiClient {
    @Qualifier("pythonWebClient")
    private final WebClient pythonWebClient;

    private static final ParameterizedTypeReference<Result<KnowledgeEmbeddingTaskCreateDTO>> CREATE_TYPE = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Result<KnowledgeEmbeddingTaskDTO>> TASK_TYPE = new ParameterizedTypeReference<>() {
    };

    /**
     * 调用 POST /ai/knowledge/embedding
     * 
     * @param knowledgeId 知识库ID
     * @return 知识库向量化任务DTO
     */
    public KnowledgeEmbeddingTaskCreateDTO createEmbeddingTask(Long knowledgeId) {

        Long userId = UserHolder.getUserId();

        Result<KnowledgeEmbeddingTaskCreateDTO> result = pythonWebClient.post()
                .uri("/ai/knowledge/embedding")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("knowledgeId", knowledgeId))
                .retrieve()
                .bodyToMono(CREATE_TYPE)
                .block();

        if (result == null || result.getCode() == null || result.getCode() != ErrorCode.SUCCESS.getCode()
                || result.getData() == null) {
            String msg = result != null ? result.getMsg() : "Python 向量化接口返回为空";
            log.error("调用 Python 创建向量任务失败, knowledgeId={}, userId={}, msg={}", knowledgeId, userId, msg);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED.getCode(),
                    ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED.getMessage() + "：" + msg);
        }

        return result.getData();
    }

    /**
     * 调用 GET /ai/knowledge/embedding/tasks/{taskId}
     * 
     * @param taskId 任务ID
     * @return 知识库向量化任务DTO
     */
    public KnowledgeEmbeddingTaskDTO getTaskStatus(String taskId) {
        Result<KnowledgeEmbeddingTaskDTO> result = pythonWebClient.get()
                .uri("/ai/knowledge/embedding/tasks/{taskId}", taskId)
                .retrieve()
                .bodyToMono(TASK_TYPE)
                .block();
        if (result == null || result.getCode() == null || result.getCode() != 200 || result.getData() == null) {
            String msg = result != null ? result.getMsg() : "Python 任务查询返回为空";
            log.error("查询 Python 向量任务失败, taskId={}, msg={}", taskId, msg);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED.getCode(),
                    ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED.getMessage() + "：" + msg);
        }
        return result.getData();
    }

    /**
     * 调用 POST /ai/knowledge/vectors/delete
     * 
     * @param knowledgeId 知识库ID
     * @return 知识库向量化任务DTO
     */
    public KnowledgeEmbeddingTaskCreateDTO deleteEmbeddingTask(Long knowledgeId) {
        Long userId = UserHolder.getUserId();

        Result<KnowledgeEmbeddingTaskCreateDTO> result = pythonWebClient.post()
                .uri("/ai/knowledge/embedding/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("knowledgeId", knowledgeId))
                .retrieve()
                .bodyToMono(CREATE_TYPE)
                .block();

        if (result == null || result.getCode() == null || result.getCode() != ErrorCode.SUCCESS.getCode()
                || result.getData() == null) {
            String msg = result != null ? result.getMsg() : "Python 删除向量任务接口返回为空";
            log.error("调用 Python 删除向量任务失败, knowledgeId={}, userId={}, msg={}", knowledgeId, userId, msg);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED.getCode(),
                    ErrorCode.KNOWLEDGE_BASE_EMBEDDING_FAILED.getMessage() + "：" + msg);
        }

        return result.getData();
    }
}
