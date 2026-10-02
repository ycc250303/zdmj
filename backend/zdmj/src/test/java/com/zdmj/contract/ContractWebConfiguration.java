package com.zdmj.contract;

import org.springframework.boot.SpringBootConfiguration;

/**
 * 契约切片的配置入口。避免 {@code @WebMvcTest} 选用带 Mapper 扫描的主启动类。
 */
@SpringBootConfiguration
class ContractWebConfiguration {
}
