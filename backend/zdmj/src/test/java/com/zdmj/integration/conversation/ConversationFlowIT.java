package com.zdmj.integration.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.ExternalAiStubConfig;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;

@AutoConfigureMockMvc
class ConversationFlowIT extends IntegrationTestBase {

    private final ApiClient api;
    private final MockMvc mockMvc;
    private final TestUsers testUsers;
    private final TestJwt testJwt;

    @Autowired
    ConversationFlowIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc, TestUsers testUsers,
            TestJwt testJwt) {
        super(databaseCleaner);
        this.api = new ApiClient(mockMvc);
        this.mockMvc = mockMvc;
        this.testUsers = testUsers;
        this.testJwt = testJwt;
    }

    @Test
    void 会话消息_发送后接收固定事件流_消息按序号排列且角色为用户与助手() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        long conversationId = ApiClient.dataId(api.post(token, "/conversations", """
                {"config":{"useSystemKnowledge":false,"ragDocumentIds":[]}}
                """));

        MvcResult async = mockMvc.perform(post(ApiClient.PREFIX + "/messages/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"conversationId\":" + conversationId + ",\"message\":\"你好\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult streamed = mockMvc.perform(asyncDispatch(async))
                .andExpect(status().isOk())
                .andReturn();
        String stream = new String(streamed.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(stream).contains("event:delta");
        assertThat(stream).contains("测试");
        assertThat(stream).contains("模型");
        assertThat(stream).contains("固定回复");

        String messages = ApiClient.requireOk(api.get(token,
                "/messages?conversationId=" + conversationId + "&page=1&limit=20"));
        assertThat(JsonPath.<Integer>read(messages, "$.data.list.length()")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(messages, "$.data.list[0].sequence")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(messages, "$.data.list[0].role")).isEqualTo(1);
        assertThat(JsonPath.<String>read(messages, "$.data.list[0].content")).isEqualTo("你好");
        assertThat(JsonPath.<Integer>read(messages, "$.data.list[1].sequence")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(messages, "$.data.list[1].role")).isEqualTo(2);
        assertThat(JsonPath.<String>read(messages, "$.data.list[1].content"))
                .isEqualTo(ExternalAiStubConfig.FIXED_REPLY);

        String conversation = ApiClient.requireOk(api.get(token, "/conversations/" + conversationId));
        assertThat(JsonPath.<String>read(conversation, "$.data.title")).isEqualTo(ExternalAiStubConfig.FIXED_REPLY);
    }
}
