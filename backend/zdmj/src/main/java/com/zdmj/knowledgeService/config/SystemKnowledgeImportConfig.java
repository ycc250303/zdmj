package com.zdmj.knowledgeService.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 系统知识库命令行导入配置。
 * @param root 服务器受控资料目录的真实绝对路径，由导入容器只读挂载
 */
@ConfigurationProperties("knowledge.system-import")
public record SystemKnowledgeImportConfig(
        @DefaultValue("/app/data/knowledge-sync/javaguide") String root) {
}
