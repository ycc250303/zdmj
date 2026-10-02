package com.zdmj.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        objectMapper.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void businessException_shouldWriteProblemDetails() throws Exception {
        mockMvc.perform(get("/__exception-probe/business"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.RESUME_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.detail").value(ErrorCode.RESUME_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.instance").value("/__exception-probe/business"));
    }

    @Test
    void uncaughtException_shouldReturnSystemException() throws Exception {
        mockMvc.perform(get("/__exception-probe/uncaught"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_EXCEPTION.getCode()))
                .andExpect(jsonPath("$.detail").value(ErrorCode.SYSTEM_EXCEPTION.getMessage()))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("boom"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @RestController
    @RequestMapping("/__exception-probe")
    static class ProbeController {

        @GetMapping("/business")
        void business() {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }

        @GetMapping("/uncaught")
        void uncaught() {
            throw new IllegalStateException("boom");
        }
    }
}
