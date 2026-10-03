package com.zdmj.aiService.provider;

/**
 * 查询用户模型配置时的用途。
 */
public enum ModelPurpose {

    /** 按用户已保存的模型与密钥调用 */
    USER_CHAT,

    /** 平台固定任务，不读取用户配置 */
    PLATFORM
}
