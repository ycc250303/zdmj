package com.zdmj.aiService.dto;

import java.util.List;

/**
 * 模型设置页面数据，包含当前配置、密钥掩码与可选模型目录。
 *
 * @param configured 是否存在配置；没有记录时为 false，当前配置字段为空，模型目录仍然返回。
 * @param modelCode 配置所选模型的规范目录编号。
 * @param modelDisplayName 模型目录中的展示名称。
 * @param apiKeyMasked 供应商密钥掩码，不包含完整明文或密文。
 * @param models 可供选择的模型目录，顺序与模型枚举一致。
 */
public record UserLlmConfigResponse(
        boolean configured,
        String modelCode,
        String modelDisplayName,
        String apiKeyMasked,
        List<ModelOption> models) {

    /** 复制集合结构，保留既有空值和元素语义。 */
    public UserLlmConfigResponse {
        models = models == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(models));
    }
}
