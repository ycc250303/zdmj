package com.zdmj.knowledgeService.service;

import com.zdmj.knowledgeService.dto.SystemKnowledgeImportRequest;
import com.zdmj.knowledgeService.dto.SystemKnowledgeImportResult;

/** 服务器命令行专用的系统知识库资料导入，进程由更新脚本互斥启动。 */
public interface SystemKnowledgeImportService {
    /** 校验受控资料并对比已入库正文摘要，事务外生成新增和变更文档的向量，再以单个事务发布；失败保持原有可见内容。 */
    SystemKnowledgeImportResult importDocuments(SystemKnowledgeImportRequest request);
}
