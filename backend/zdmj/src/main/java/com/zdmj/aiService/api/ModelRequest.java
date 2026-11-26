package com.zdmj.aiService.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文本、结构化和流式调用共用的不可变模型输入。
 *
 * @param message 当前用户消息，历史中不重复追加
 * @param promptName classpath 提示词名称，空则不附加业务系统提示词
 * @param promptVars 提示词变量，构造时复制，变量对象由调用方保证稳定
 * @param history 按时间正序排列的历史消息，由调用方完成归属和窗口选择
 * @param historyNotice 服务端生成的历史完成情况说明，不包含异常详情
 */
public record ModelRequest(String message, String promptName, Map<String, Object> promptVars,
        List<HistoryMessage> history, String historyNotice) {

    public ModelRequest {
        promptVars = promptVars == null ? null
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(promptVars));
        history = List.copyOf(Objects.requireNonNull(history, "历史消息不能为空"));
        Objects.requireNonNull(historyNotice, "历史状态说明不能为空");
    }

    /** 单次请求显式使用空历史。 */
    public ModelRequest(String message, String promptName, Map<String, Object> promptVars) {
        this(message, promptName, promptVars, List.of(), "");
    }

    /**
     * 模型历史消息，只表达角色和原文，不携带持久化类型。
     *
     * @param role 用户或助手角色
     * @param content 非空白的历史原文
     */
    public record HistoryMessage(Role role, String content) {
        public HistoryMessage {
            Objects.requireNonNull(role, "历史角色不能为空");
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("历史消息内容不能为空白");
            }
        }

        /** 对应模型输入中的 user 和 assistant。 */
        public enum Role { USER, ASSISTANT }
    }
}
