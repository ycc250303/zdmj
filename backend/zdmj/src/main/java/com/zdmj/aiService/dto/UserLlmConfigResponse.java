package com.zdmj.aiService.dto;

import java.util.List;

import lombok.Data;

/** 模型设置页面数据，包含当前配置、密钥掩码与可选模型目录。 */
@Data
public class UserLlmConfigResponse {

    /** 是否存在配置；没有记录时为 false，当前配置字段为空，模型目录仍然返回。 */
    private boolean configured;

    /** 配置所选模型的规范目录编号。 */
    private String modelCode;

    /** 模型目录中的展示名称。 */
    private String modelDisplayName;

    /** 供应商密钥掩码，不包含完整明文或密文。 */
    private String apiKeyMasked;

    /** 可供选择的模型目录，顺序与模型枚举一致。 */
    private List<ModelOption> models;

    /**
     * 可选模型目录项。
     *
     * @param code 保存时使用的模型目录编号
     * @param displayName 模型目录中的展示名称
     */
    public record ModelOption(String code, String displayName) {
    }
}
