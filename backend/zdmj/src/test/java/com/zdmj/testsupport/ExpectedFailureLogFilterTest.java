package com.zdmj.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ExpectedFailureLogFilterTest {

    @Test
    void 主动失败分支不输出错误日志_其它错误仍输出() {
        Logger logger = (Logger) LoggerFactory.getLogger(ExpectedFailureLogFilterTest.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level previousLevel = logger.getLevel();
        boolean previousAdditive = logger.isAdditive();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        logger.setAdditive(false);
        try {
            logger.error("简历识别失败", new RuntimeException("expected"));
            logger.error("未捕获异常: ", new IllegalStateException("boom"));
            logger.error("未捕获异常: ", new IllegalStateException(
                    "ERROR: new row for relation \"users\" violates check constraint \"test_block_reset\""));
            logger.error("未捕获异常: ", new IllegalStateException("数据库连接中断"));
            logger.error("不该被屏蔽的错误");
            logger.warn("[UserLlmConfig] testConnection failed modelCode={} elapsedMs={}",
                    "deepseek-flash", 0L, new RuntimeException("probe unavailable"));
            logger.warn("业务异常: code={}, message={}", 2014, "大模型连通性测试失败");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditive);
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        "未捕获异常: ",
                        "不该被屏蔽的错误",
                        "业务异常: code=2014, message=大模型连通性测试失败");
    }
}
