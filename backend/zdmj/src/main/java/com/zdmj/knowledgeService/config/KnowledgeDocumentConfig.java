package com.zdmj.knowledgeService.config;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 知识文档处理配置。
 *
 * @param maxExtractedChars 单篇文档最大提取长度，按 UTF-16 代码单元计数，
 *                          必须为正数，默认一百万
 */
@Validated
@ConfigurationProperties(prefix = "knowledge.document")
public record KnowledgeDocumentConfig(
        @DefaultValue("1000000")
        @Min(value = 1, message = "知识文档最大提取长度必须为正数")
        int maxExtractedChars) {
}
