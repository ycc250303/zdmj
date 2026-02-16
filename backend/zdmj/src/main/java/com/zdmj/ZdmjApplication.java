package com.zdmj;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.zdmj")
@MapperScan({
        "com.zdmj.userAuthservice.mapper",
        "com.zdmj.resumeService.mapper"
})
public class ZdmjApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZdmjApplication.class, args);
    }
}
