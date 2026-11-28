package com.zdmj.knowledgeService.dto;

/**
 * 系统知识库命令行导入参数，来源和文档范围由服务端固定。
 * @param runId 清理工具生成的成功目录编号
 * @param manifestHash manifest.json原始字节的SHA-256
 */
public record SystemKnowledgeImportRequest(String runId, String manifestHash) {
    public SystemKnowledgeImportRequest {
        if (runId == null || !runId.matches("[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}")
                || manifestHash == null || !manifestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("运行编号或清单摘要非法");
        }
    }
}
