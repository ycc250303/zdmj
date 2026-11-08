package com.zdmj.aiService.dto;

import lombok.Data;

/** 可供用户选择的模型目录项。 */
@Data
public class LlmModelOptionResponse {

    /** 保存配置和连通性检查使用的模型目录编号。 */
    private String code;

    /** 模型目录中的展示名称。 */
    private String displayName;
}
