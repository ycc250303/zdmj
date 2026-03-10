package com.zdmj;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.zdmj")
@MapperScan({
        "com.zdmj.userAuthService.mapper",
        "com.zdmj.resumeService.mapper",
        "com.zdmj.knowledgeService.mapper",
        "com.zdmj.projectHighlightService.mapper",
        "com.zdmj.conversationService.mapper"
})
public class ZdmjApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZdmjApplication.class, args);
    }
}
