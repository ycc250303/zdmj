package com.zdmj.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zdmj.common.config.RequestTraceFilter;
import com.zdmj.common.config.WebMvcConfig;
import com.zdmj.common.exception.GlobalExceptionHandler;
import com.zdmj.contract.support.ContractProbeController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebMvcTest(controllers = ContractProbeController.class, useDefaultFilters = false)
@Import({ContractProbeController.class, GlobalExceptionHandler.class, WebMvcConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("mvc-contract")
class RequestTraceContractTest {

    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void configureTraceFilter() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new RequestTraceFilter()).build();
    }

    @ParameterizedTest
    @CsvSource({"business,404", "limited,429", "uncaught,500", "no-resource,404"})
    void 业务与框架异常_响应头和错误正文包含相同编号(String path, int status) throws Exception {
        var result = mvc.perform(get("/api/zdmj/contract/" + path))
                .andExpect(status().is(status)).andReturn();
        String traceId = result.getResponse().getHeader("X-Trace-Id");
        assertThat(traceId).matches("[0-9a-f]{32}");
        jsonPath("$.traceId").value(traceId).match(result);
    }

    @Test
    void JSON解析和字段校验失败_错误响应保持请求编号() throws Exception {
        for (String body : java.util.List.of("{", "{}")) {
            var result = mvc.perform(post("/api/zdmj/contract/validate")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andReturn();
            String traceId = result.getResponse().getHeader("X-Trace-Id");
            assertThat(traceId).matches("[0-9a-f]{32}");
            jsonPath("$.traceId").value(traceId).match(result);
        }
    }

    @Test
    void 成功响应_返回编号且保留原有成功信封() throws Exception {
        var result = mvc.perform(get("/api/zdmj/contract/serialization"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].id").value(7))
                .andExpect(jsonPath("$.traceId").doesNotExist())
                .andReturn();
        assertThat(result.getResponse().getHeader("X-Trace-Id")).matches("[0-9a-f]{32}");
    }
}
