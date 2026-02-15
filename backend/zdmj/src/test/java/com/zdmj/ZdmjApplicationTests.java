package com.zdmj;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Spring Boot应用上下文加载测试
 * 在CI环境中不连接外部服务，只验证基本的Spring Boot配置是否正确
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        // 排除需要外部服务的自动配置
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.mongodb.MongoDataAutoConfiguration," +
                "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration," +
                "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration," +
                "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
})
class ZdmjApplicationTests {

    @Test
    void contextLoads() {
        // 测试Spring上下文是否能正常加载
        // 不连接外部服务，不启动Web服务器，只验证基本的Spring Boot配置
        // 如果上下文加载成功，说明基本的Bean配置和依赖注入没有问题
    }

}
