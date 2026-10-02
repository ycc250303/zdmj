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

import com.zdmj.common.async.AsyncTaskStatus;
import com.zdmj.common.async.AsyncTaskType;
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
    void 成功响应_信封分页任务与空值_按既定JSON写出且不含敏感字段() throws Exception {
        mockMvc.perform(get(BASE + "/serialization"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("操作成功"))
                .andExpect(jsonPath("$.data.empty").value(nullValue()))
                .andExpect(jsonPath("$.data.tasks.page").value(2))
                .andExpect(jsonPath("$.data.tasks.limit").value(20))
                .andExpect(jsonPath("$.data.tasks.total").value(1))
                .andExpect(jsonPath("$.data.tasks.totalPages").value(1))
                .andExpect(jsonPath("$.data.tasks.list[0].taskId").value(7))
                .andExpect(jsonPath("$.data.tasks.list[0].taskType").value(AsyncTaskType.RESUME_PARSE.getCode()))
                .andExpect(jsonPath("$.data.tasks.list[0].status").value(AsyncTaskStatus.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.tasks.list[0].startedAt").value("2024-09-01T08:30:00"))
                .andExpect(jsonPath("$.data.tasks.list[0].completedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.tasks.list[0].errorMessage").value(nullValue()))
                .andExpect(jsonPath("$.data.tasks.list[0].result").value(nullValue()))
                .andExpect(jsonPath("$.data.tasks.content").doesNotExist())
                .andExpect(jsonPath("$.data.tasks.pageable").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
