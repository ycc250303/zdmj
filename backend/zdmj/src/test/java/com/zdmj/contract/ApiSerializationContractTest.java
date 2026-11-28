package com.zdmj.contract;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.zdmj.common.config.WebMvcConfig;
import com.zdmj.common.exception.GlobalExceptionHandler;
import com.zdmj.contract.support.ContractProbeController;

@WebMvcTest(controllers = ContractProbeController.class, useDefaultFilters = false)
@Import({ContractProbeController.class, GlobalExceptionHandler.class, WebMvcConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("mvc-contract")
class ApiSerializationContractTest {

    private static final String BASE = "/api/zdmj/contract";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 成功响应_会话分页与空值_按既定JSON写出且不含敏感字段() throws Exception {
        mockMvc.perform(get(BASE + "/serialization"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("操作成功"))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.limit").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(7))
                .andExpect(jsonPath("$.data.list[0].userId").value(1))
                .andExpect(jsonPath("$.data.list[0].title").value("求职准备"))
                .andExpect(jsonPath("$.data.list[0].messageCount").value(2))
                .andExpect(jsonPath("$.data.list[0].lastMessageAt").value("2024-09-01T08:30:00"))
                .andExpect(jsonPath("$.data.list[0].config").value(nullValue()))
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andExpect(jsonPath("$.data.pageable").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
