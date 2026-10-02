package com.zdmj.testsupport;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;

/**
 * 集成测试里的 JSON 请求与成功、Problem Details 断言。
 */
public final class ApiClient {

    public static final String PREFIX = "/api/zdmj";

    private final MockMvc mockMvc;

    public ApiClient(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public MvcResult post(String token, String path, String json) throws Exception {
        return exchange(MockMvcRequestBuilders.post(PREFIX + path), token, json);
    }

    public MvcResult put(String token, String path, String json) throws Exception {
        return exchange(MockMvcRequestBuilders.put(PREFIX + path), token, json);
    }

    public MvcResult get(String token, String path) throws Exception {
        return exchange(MockMvcRequestBuilders.get(PREFIX + path), token, null);
    }

    public MvcResult delete(String token, String path) throws Exception {
        return exchange(MockMvcRequestBuilders.delete(PREFIX + path), token, null);
    }

    public MvcResult exchange(MockHttpServletRequestBuilder builder, String token, String json) throws Exception {
        builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (json != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(builder).andReturn();
    }

    public ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder);
    }

    public static String requireOk(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("期望成功响应，实际 " + result.getResponse().getStatus() + " " + body);
        }
        if (!Integer.valueOf(0).equals(JsonPath.<Integer>read(body, "$.code"))) {
            throw new AssertionError("期望业务码 0，实际响应 " + body);
        }
        return body;
    }

    public static long dataId(MvcResult result) throws Exception {
        return JsonPath.<Number>read(requireOk(result), "$.data.id").longValue();
    }

    public static long dataLong(MvcResult result, String path) throws Exception {
        return JsonPath.<Number>read(requireOk(result), path).longValue();
    }

    public static void assertProblem(MvcResult result, int httpStatus, int code) throws Exception {
        String body = result.getResponse().getContentAsString();
        if (result.getResponse().getStatus() != httpStatus) {
            throw new AssertionError("期望 HTTP " + httpStatus + "，实际 " + result.getResponse().getStatus() + " " + body);
        }
        String contentType = result.getResponse().getContentType();
        if (contentType == null || !contentType.contains("application/problem+json")) {
            throw new AssertionError("期望 application/problem+json，实际 " + contentType + " " + body);
        }
        if (!Integer.valueOf(code).equals(JsonPath.<Integer>read(body, "$.code"))) {
            throw new AssertionError("期望业务码 " + code + "，实际响应 " + body);
        }
    }

    public static ResultActions expectOk(ResultActions actions) throws Exception {
        return actions.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(0));
    }
}
