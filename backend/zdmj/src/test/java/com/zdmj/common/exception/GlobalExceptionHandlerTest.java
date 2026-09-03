package com.zdmj.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        objectMapper.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(converter)
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
    void rateLimit_shouldReturn429() throws Exception {
        mockMvc.perform(get("/__exception-probe/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.RATE_LIMIT_EXCEEDED.getCode()));
    }

    @Test
    void methodArgumentNotValid_shouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/__exception-probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    void httpMessageNotReadable_shouldReturnRequestBodyError() throws Exception {
        mockMvc.perform(post("/__exception-probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.REQUEST_BODY_ERROR.getCode()));
    }

    @Test
    void methodNotAllowed_shouldUseCode1006() throws Exception {
        mockMvc.perform(post("/__exception-probe/only-get"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.REQUEST_METHOD_NOT_SUPPORTED.getCode()));
    }

    @Test
    void noResource_shouldUseCode1006() throws Exception {
        mockMvc.perform(get("/__exception-probe/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.REQUEST_METHOD_NOT_SUPPORTED.getCode()));
    }

    @Test
    void uncaughtException_shouldReturnSystemException() throws Exception {
        mockMvc.perform(get("/__exception-probe/uncaught"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(ProblemDetailSupport.PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_EXCEPTION.getCode()))
                .andExpect(jsonPath("$.detail").value(ErrorCode.SYSTEM_EXCEPTION.getMessage()));
    }

    @RestController
    @RequestMapping("/__exception-probe")
    static class ProbeController {

        @GetMapping("/business")
        void business() {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }

        @GetMapping("/limited")
        void limited() {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        @GetMapping("/only-get")
        void onlyGet() {
        }

        @GetMapping("/no-resource")
        void noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/missing");
        }

        @GetMapping("/uncaught")
        void uncaught() {
            throw new IllegalStateException("boom");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody ProbeBody body) {
        }
    }

    static class ProbeBody {
        @NotBlank
        public String name;
    }
}
