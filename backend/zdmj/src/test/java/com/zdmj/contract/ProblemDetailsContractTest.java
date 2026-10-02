package com.zdmj.contract;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.GlobalExceptionHandler;
import com.zdmj.common.exception.ProblemDetailSupport;
import com.zdmj.contract.support.ContractProbeController;

@WebMvcTest(controllers = ContractProbeController.class, useDefaultFilters = false)
@Import({ContractProbeController.class, GlobalExceptionHandler.class, WebMvcConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("mvc-contract")
class ProblemDetailsContractTest {

    private static final String BASE = "/api/zdmj/contract";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 业务异常_简历不存在_返回404与业务码且不含堆栈() throws Exception {
        mockMvc.perform(get(BASE + "/business"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value(ErrorCode.RESUME_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.title").value(ErrorCode.RESUME_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.detail").value(ErrorCode.RESUME_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.instance").value(BASE + "/business"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.msg").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(content().string(not(containsString("BusinessException"))));
    }

    @Test
    void 参数校验_缺少必填字段_返回400与校验码() throws Exception {
        mockMvc.perform(post(BASE + "/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.title").value(ErrorCode.VALIDATION_ERROR.getMessage()))
                .andExpect(jsonPath("$.detail").value(containsString("must not be blank")))
                .andExpect(jsonPath("$.instance").value(BASE + "/validate"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void 请求体_非法JSON_返回400且不写业务码() throws Exception {
        mockMvc.perform(post(BASE + "/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.instance").value(BASE + "/validate"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void 路径_资源不存在_返回404且不写业务码() throws Exception {
        mockMvc.perform(get(BASE + "/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.instance").value(BASE + "/no-resource"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void 方法_POST访问只读接口_返回405且不写业务码() throws Exception {
        mockMvc.perform(post(BASE + "/only-get"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.instance").value(BASE + "/only-get"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void 限流_业务异常_返回429与限流码() throws Exception {
        mockMvc.perform(get(BASE + "/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value(ErrorCode.RATE_LIMIT_EXCEEDED.getCode()))
                .andExpect(jsonPath("$.title").value(ErrorCode.RATE_LIMIT_EXCEEDED.getMessage()))
                .andExpect(jsonPath("$.detail").value(ErrorCode.RATE_LIMIT_EXCEEDED.getMessage()))
                .andExpect(jsonPath("$.instance").value(BASE + "/limited"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void 未捕获异常_编程错误_返回500通用文案且不回显异常详情() throws Exception {
        mockMvc.perform(get(BASE + "/uncaught"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_EXCEPTION.getCode()))
                .andExpect(jsonPath("$.title").value(ErrorCode.SYSTEM_EXCEPTION.getMessage()))
                .andExpect(jsonPath("$.detail").value(ErrorCode.SYSTEM_EXCEPTION.getMessage()))
                .andExpect(jsonPath("$.instance").value(BASE + "/uncaught"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString(ContractProbeController.SECRET_FAILURE))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
