package com.zdmj;

import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.zdmj")
@MapperScan(value = {
        "com.zdmj.userAuthService.mapper",
        "com.zdmj.resumeService.mapper",
        "com.zdmj.knowledgeService.mapper",
        "com.zdmj.jobService.mapper",
        "com.zdmj.conversationService.mapper"
}, annotationClass = Mapper.class)
public class ZdmjApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZdmjApplication.class, args);
    }
}
