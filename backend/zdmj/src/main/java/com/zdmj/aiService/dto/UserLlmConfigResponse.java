package com.zdmj.aiService.dto;

import lombok.Data;

/** 当前用户的模型配置展示结果，仅返回密钥掩码。 */
@Data
public class UserLlmConfigResponse {

    /** 是否存在配置；没有记录时为 false，其余字段为空。 */
    private boolean configured;

    /** 配置所选模型的规范目录编号。 */
    private String modelCode;

    /** 模型目录中的展示名称。 */
    private String modelDisplayName;

    /** 供应商密钥掩码，不包含完整明文或密文。 */
    private String apiKeyMasked;
}
