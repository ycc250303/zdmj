package com.zdmj.knowledgeService.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.aiService.api.EmbeddingGateway;
import com.zdmj.common.config.MybatisObjectHandlerConfig;
import com.zdmj.common.config.MybatisPlusConfig;
import com.zdmj.common.config.TimeConfiguration;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.knowledgeService.config.EmbeddingConfig;
import com.zdmj.knowledgeService.config.SystemKnowledgeImportConfig;
import com.zdmj.knowledgeService.dto.SystemKnowledgeImportRequest;
import com.zdmj.knowledgeService.dto.SystemKnowledgeImportResult;
import com.zdmj.knowledgeService.service.SystemKnowledgeImportService;
import com.zdmj.knowledgeService.service.impl.SystemKnowledgeImportServiceImpl;
import java.util.Map;
import java.util.Set;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * 系统知识库一次性导入主类，由同一后端JAR的PropertiesLauncher启动。
 * 显式装配导入所需组件，正常Web应用不会扫描注册此入口。
 */
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
        TransactionAutoConfiguration.class, MybatisPlusAutoConfiguration.class, JacksonAutoConfiguration.class,
        RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class, OpenAiEmbeddingAutoConfiguration.class})
@Import({EmbeddingConfig.class, MybatisPlusConfig.class, MybatisObjectHandlerConfig.class, TimeConfiguration.class,
        PdfParserUtil.class, SystemKnowledgeImportServiceImpl.class})
@EnableConfigurationProperties(SystemKnowledgeImportConfig.class)
@MapperScan("com.zdmj.knowledgeService.mapper")
@ComponentScan(basePackages = "com.zdmj.aiService", useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = EmbeddingGateway.class))
public class SystemKnowledgeImportApplication {
    /**
     * 校验命令行，启动独立处理上下文，等待数据库提交并关闭资源后输出成功摘要。
     * @param args 仅接受--run-id与--manifest-hash，分别且只允许提供一个值
     * @throws Exception 参数、来源、模型、数据库或资源关闭失败；进程以非零状态退出
     */
    public static void main(String[] args) throws Exception {
        SystemKnowledgeImportRequest request = parseArguments(args);
        SystemKnowledgeImportResult result;
        ObjectMapper json;
        try (var context = application().run()) {
            result = context.getBean(SystemKnowledgeImportService.class).importDocuments(request);
            json = context.getBean(ObjectMapper.class);
        }
        System.out.println("SYSTEM_KNOWLEDGE_IMPORT_RESULT=" + json.writeValueAsString(result));
    }

    /** 创建只装配显式声明组件的非Web上下文，共用应用中的数据库与平台嵌入配置。 */
    public static SpringApplication application() {
        var application = new SpringApplication(SystemKnowledgeImportApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        application.setDefaultProperties(Map.of("spring.main.banner-mode", "off"));
        application.addInitializers(context -> context.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource("system-knowledge-import", Map.of(
                        "mybatis-plus.mapper-locations", "classpath:mapper/knowledgeService/*.xml",
                        "mybatis-plus.configuration.log-impl", "org.apache.ibatis.logging.nologging.NoLoggingImpl"))));
        return application;
    }

    /** 校验固定命令行字段，禁止从命令行指定来源范围、任意正文路径或其他应用参数。 */
    public static SystemKnowledgeImportRequest parseArguments(String[] args) {
        var parsed = new DefaultApplicationArguments(args);
        if (!parsed.getNonOptionArgs().isEmpty() || !parsed.getOptionNames().equals(Set.of("run-id", "manifest-hash"))) {
            throw new IllegalArgumentException("必须且仅允许提供--run-id与--manifest-hash");
        }
        for (String name : parsed.getOptionNames()) {
            if (parsed.getOptionValues(name).size() != 1) { throw new IllegalArgumentException("每个导入参数必须且只能提供一个值"); }
        }
        return new SystemKnowledgeImportRequest(parsed.getOptionValues("run-id").getFirst(),
                parsed.getOptionValues("manifest-hash").getFirst());
    }
}
