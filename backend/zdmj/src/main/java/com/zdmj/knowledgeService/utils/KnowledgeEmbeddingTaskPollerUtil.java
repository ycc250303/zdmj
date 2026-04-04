package com.zdmj.knowledgeService.utils;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.zdmj.common.client.KnowledgeVectorApiClient;
import com.zdmj.knowledgeService.mapper.KnowledgeBasesMapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@AllArgsConstructor
public class KnowledgeEmbeddingTaskPollerUtil {
    private static final List<String> POLLING_STATUSES = Arrays.asList("PENDING", "RUNNING");
    

    private final KnowledgeBasesMapper knowledgeBasesMapper;
    private final KnowledgeVectorApiClient knowledgeVectorApiClient;
}
