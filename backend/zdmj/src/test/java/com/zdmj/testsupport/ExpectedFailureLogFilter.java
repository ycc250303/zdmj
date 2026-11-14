package com.zdmj.testsupport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.List;
import org.slf4j.Marker;

/**
 * 丢弃测试主动触发的失败分支错误日志。
 *
 * <p>用例仍断言返回值、事务回滚和错误响应。过滤器只存在于测试 classpath。
 * 未列入清单的错误，以及未识别的未捕获异常，继续输出。
 */
public class ExpectedFailureLogFilter extends TurboFilter {

    private static final List<String> EXPECTED_ERROR_FORMATS = List.of(
            "简历结构化输出解析失败",
            "简历识别失败",
            "PDF 解析失败",
            "人岗匹配结构化输出解析失败",
            "人岗匹配 LLM 调用失败",
            "报告查询向量生成失败",
            "能力画像结构化输出失败",
            "大模型生成能力画像失败",
            "岗位要求画像生成失败",
            "查询改写失败",
            "查询向量生成失败",
            "写入登录态失败",
            "邮件失败后的验证码清理失败",
            "邮件发送失败",
            "登录状态服务不可用",
            "文件上传异常",
            "Aggregation Error",
            "Operator called default onErrorDropped");

    private static final List<String> EXPECTED_UNCAUGHT_MESSAGES = List.of(
            "boom",
            "secret-uncaught-detail",
            "模型检查失败");

    private static final List<String> EXPECTED_CONSTRAINTS = List.of(
            "test_block_reset",
            "career_reports_reject_overwrite",
            "test_reject_title");

    private static final List<String> EXPECTED_WARN_FORMATS = List.of(
            "[UserLlmConfig] testConnection failed");

    /**
     * 匹配主动失败分支时丢弃该条错误日志。
     *
     * @param marker 日志标记，不参与判断
     * @param logger 日志记录器，不参与判断
     * @param level 日志级别，处理 ERROR 和已列出的 WARN
     * @param format 日志格式
     * @param params 格式参数，不参与判断
     * @param throwable 关联异常；未捕获异常据此识别测试探针和测试约束
     * @return 主动失败分支返回 DENY，其余返回 NEUTRAL
     */
    @Override
    public FilterReply decide(
            Marker marker, Logger logger, Level level, String format, Object[] params, Throwable throwable) {
        if (format == null || (level != Level.ERROR && level != Level.WARN)) {
            return FilterReply.NEUTRAL;
        }
        if (level == Level.WARN) {
            return startsWithAny(format, EXPECTED_WARN_FORMATS) ? FilterReply.DENY : FilterReply.NEUTRAL;
        }
        if (format.startsWith("未捕获异常")) {
            return isExpectedUncaught(throwable) ? FilterReply.DENY : FilterReply.NEUTRAL;
        }
        return startsWithAny(format, EXPECTED_ERROR_FORMATS) ? FilterReply.DENY : FilterReply.NEUTRAL;
    }

    private static boolean startsWithAny(String format, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (format.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExpectedUncaught(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (containsAny(message, EXPECTED_UNCAUGHT_MESSAGES)
                    || containsAny(message, EXPECTED_CONSTRAINTS))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean containsAny(String message, List<String> needles) {
        for (String needle : needles) {
            if (message.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
