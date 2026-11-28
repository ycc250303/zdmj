package com.zdmj.aiService.api;

/** 模型历史消息角色，对应供应商消息中的 user 和 assistant。 */
public enum ModelMessageRole {
    /** 用户问题，对应 user。 */
    USER,
    /** 助手回复，对应 assistant。 */
    ASSISTANT
}
