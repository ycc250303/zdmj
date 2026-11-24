package com.zdmj.conversationService.enums;

import lombok.Getter;

/** 助手回复生成状态，对应 messages.generation_status；用户和系统消息不设置状态。 */
@Getter
public enum MessageGenerationStatusEnum {
    /** 1：正在生成，启动恢复时将中断的生成标记失败。 */
    PROCESSING(1),
    /** 2：模型正常结束且内容已经保存。 */
    COMPLETED(2),
    /** 3：模型、检索或进程中断导致失败，保留已经保存的内容。 */
    FAILED(3),
    /** 4：客户端取消，保留已接收的部分内容。 */
    CANCELLED(4),
    /** 5：历史记录无法确定是否完整。 */
    LEGACY_UNKNOWN(5);

    private final int code;

    MessageGenerationStatusEnum(int code) {
        this.code = code;
    }

    /** 解析数据库码值；非法或缺失状态立即终止。 */
    public static MessageGenerationStatusEnum fromCode(Integer code) {
        for (var status : values()) {
            if (code != null && status.code == code) {
                return status;
            }
        }
        throw new IllegalStateException("助手回复生成状态非法：" + code);
    }
}
