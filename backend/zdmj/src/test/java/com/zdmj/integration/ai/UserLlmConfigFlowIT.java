package com.zdmj.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.ResourceAccessException;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.aiService.api.ModelRequest;
import com.zdmj.aiService.support.ModelClientFactory;
import com.zdmj.aiService.enums.ModelCode;
import com.zdmj.aiService.service.impl.ModelGatewayImpl;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.DatabaseCleaner;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;

import reactor.core.publisher.Flux;

/** 模型配置接口使用真实持久化、解密和按次读取配置，供应商客户端由确定性替身提供。 */
@AutoConfigureMockMvc
class UserLlmConfigFlowIT extends IntegrationTestBase {

    @MockitoBean
    private ModelClientFactory clients;

    /** 替换公共测试配置中的网关替身，使模型调用使用生产网关读取当前配置。 */
    @MockitoBean(name = "testModelGateway")
    private ModelGateway testModelGateway;

    @Autowired
    @Qualifier("modelGatewayImpl")
    private ModelGatewayImpl gateway;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ApiClient api;
    private final TestUsers testUsers;
    private final TestJwt testJwt;
    private final JdbcTemplate jdbcTemplate;

    private CurrentActor actor;
    private String token;
    private String tokenB;
    private Long otherUserId;
    private ChatModel probe;

    @Autowired
    UserLlmConfigFlowIT(DatabaseCleaner cleaner, MockMvc mockMvc, TestUsers testUsers,
            TestJwt testJwt, JdbcTemplate jdbcTemplate) {
        super(cleaner);
        this.api = new ApiClient(mockMvc);
        this.testUsers = testUsers;
        this.testJwt = testJwt;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void authorizeUser() {
        var pair = testUsers.createPair();
        var user = pair.userA();
        otherUserId = pair.userB().getId();
        tokenB = testJwt.authorize(pair.userB());
        actor = CurrentActor.of(user.getId());
        token = testJwt.authorize(user);
        probe = mock(ChatModel.class);
        when(clients.buildProbe(any(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return probe;
        });
        when(probe.call(any(Prompt.class))).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new ChatResponse(List.of(new Generation(new AssistantMessage("pong"))));
        });
    }

    @Test
    void 配置保存更新删除_接口与模型调用保持一致() throws Exception {
        String absent = ApiClient.requireOk(api.get(token, "/users/llm-config"));
        assertThat(JsonPath.<Boolean>read(absent, "$.data.configured")).isFalse();
        assertThat(JsonPath.<List<String>>read(absent, "$.data.models[*].code"))
                .containsExactly(java.util.Arrays.stream(ModelCode.values()).map(ModelCode::code).toArray(String[]::new));

        ChatModel original = modelReplying("原配置回复");
        ChatModel updated = modelReplying("更新配置回复");
        when(clients.buildUserModel(ModelCode.DEEPSEEK_FLASH, "sk-original-secret")).thenReturn(original);
        when(clients.buildUserModel(ModelCode.QWEN_PLUS, "sk-updated-secret")).thenReturn(updated);

        ApiClient.requireOk(api.put(token, "/users/llm-config", config("deepseek-flash", "sk-original-secret")));
        String saved = ApiClient.requireOk(api.get(token, "/users/llm-config"));
        assertThat(JsonPath.<String>read(saved, "$.data.modelCode")).isEqualTo("deepseek-flash");
        assertThat(JsonPath.<String>read(saved, "$.data.apiKeyMasked")).isEqualTo("sk-****cret");
        assertThat(saved).doesNotContain("sk-original-secret", "apiKeyCiphertext");
        assertThat(JsonPath.<List<String>>read(saved, "$.data.models[*].displayName"))
                .containsExactly(java.util.Arrays.stream(ModelCode.values()).map(ModelCode::displayName).toArray(String[]::new));
        verify(clients).buildProbe(ModelCode.DEEPSEEK_FLASH, "sk-original-secret");
        verify(probe).call(org.mockito.ArgumentMatchers.argThat(
                (Prompt prompt) -> "ping".equals(prompt.getUserMessage().getText())));
        assertThat(jdbcTemplate.queryForObject("SELECT api_key_ciphertext FROM user_llm_config WHERE user_id = ?",
                String.class, actor.userId())).isNotBlank().isNotEqualTo("sk-original-secret");
        assertReplies("原配置回复");
        assertReplies("原配置回复");

        ApiClient.requireOk(api.put(token, "/users/llm-config", config("qwen3.8-flash", "sk-updated-secret")));
        assertReplies("更新配置回复");

        ApiClient.requireOk(api.delete(token, "/users/llm-config"));
        ApiClient.requireOk(api.delete(token, "/users/llm-config"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM user_llm_config WHERE user_id = ?",
                Integer.class, actor.userId())).isZero();
        assertThat(JsonPath.<Boolean>read(ApiClient.requireOk(api.get(token, "/users/llm-config")),
                "$.data.configured")).isFalse();
        assertThatThrownBy(() -> gateway.generate(actor, new ModelRequest("hi", null, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.USER_LLM_NOT_CONFIGURED));
        assertThatThrownBy(() -> gateway.stream(actor, 11L, new ModelRequest("hi", null, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.USER_LLM_NOT_CONFIGURED));
    }

    @Test
    void 首次保存探测失败_不建立配置() throws Exception {
        when(probe.call(any(Prompt.class))).thenThrow(new ResourceAccessException("供应商连接超时"));

        ApiClient.assertProblem(api.put(token, "/users/llm-config", config("deepseek-flash", "  probe-secret  ")),
                400, 2014);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM user_llm_config", Integer.class)).isZero();
        verify(clients).buildProbe(ModelCode.DEEPSEEK_FLASH, "probe-secret");
    }

    @Test
    void 替换配置探测失败_保留原配置和模型调用结果() throws Exception {
        ChatModel original = modelReplying("原配置回复");
        when(clients.buildUserModel(ModelCode.DEEPSEEK_FLASH, "original")).thenReturn(original);
        putConfig("deepseek-flash", "original");
        assertReplies("原配置回复");
        var saved = jdbcTemplate.queryForMap("SELECT * FROM user_llm_config WHERE user_id = ?", actor.userId());
        when(probe.call(any(Prompt.class))).thenThrow(new ResourceAccessException("供应商连接超时"));

        ApiClient.assertProblem(api.put(token, "/users/llm-config", config("qwen3.8-flash", "updated")), 400, 2014);

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM user_llm_config WHERE user_id = ?", actor.userId()))
                .isEqualTo(saved);
        assertReplies("原配置回复");
    }

    @Test
    void 配置接口_缺少身份或输入非法_返回既有错误() throws Exception {
        ApiClient.assertProblem(api.get("", "/users/llm-config"), 401, 1002);
        ApiClient.assertProblem(api.put("", "/users/llm-config", config("deepseek-flash", "secret")), 401, 1002);
        ApiClient.assertProblem(api.delete("", "/users/llm-config"), 401, 1002);
        ApiClient.assertProblem(api.put(token, "/users/llm-config", config("invalid", "secret")), 400, 2011);
        ApiClient.assertProblem(api.put(token, "/users/llm-config", config("deepseek-flash", " ")), 400, 1001);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM user_llm_config", Integer.class)).isZero();
    }

    @Test
    void 配置接口_移除独立目录与探测入口() throws Exception {
        assertThat(api.get(token, "/users/llm-config/models").getResponse().getStatus()).isEqualTo(404);
        assertThat(api.post(token, "/users/llm-config/test", config("deepseek-flash", "secret"))
                .getResponse().getStatus()).isEqualTo(404);
        org.mockito.Mockito.verifyNoInteractions(clients);
    }

    @Test
    void 校验并保存_每用户每分钟最多十次探测() throws Exception {
        for (int index = 0; index < 10; index++) {
            ApiClient.requireOk(api.put(token, "/users/llm-config", config("deepseek-flash", "secret")));
        }
        ApiClient.assertProblem(api.put(token, "/users/llm-config", config("deepseek-flash", "secret")), 429, 1011);
        verify(clients, times(10)).buildProbe(ModelCode.DEEPSEEK_FLASH, "secret");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM user_llm_config WHERE user_id = ?",
                Integer.class, actor.userId())).isEqualTo(1);
    }

    @Test
    void 用户模型配置_其他用户保存或删除_只影响自己的配置() throws Exception {
        ApiClient.requireOk(api.put(token, "/users/llm-config", """
                {"modelCode":"deepseek-flash","apiKey":"sk-user-a-secret"}
                """));
        String hidden = ApiClient.requireOk(api.get(tokenB, "/users/llm-config"));
        assertThat(JsonPath.<Boolean>read(hidden, "$.data.configured")).isFalse();

        String cipherA = jdbcTemplate.queryForObject(
                "SELECT api_key_ciphertext FROM user_llm_config WHERE user_id = ?", String.class, actor.userId());
        ApiClient.requireOk(api.put(tokenB, "/users/llm-config", """
                {"modelCode":"deepseek-flash","apiKey":"sk-user-b-secret"}
                """));
        ApiClient.requireOk(api.delete(tokenB, "/users/llm-config"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT api_key_ciphertext FROM user_llm_config WHERE user_id = ?", String.class, actor.userId()))
                .isEqualTo(cipherA);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_llm_config WHERE user_id = ?", Integer.class, otherUserId)).isZero();
    }

    @Test
    void 配置保存_规范化模型编号并保留创建时间() throws Exception {
        ApiClient.requireOk(api.put(token, "/users/llm-config", config(" DEEPSEEK-V4-FLASH ", "  secret  ")));
        assertThat(jdbcTemplate.queryForObject("SELECT model_code FROM user_llm_config WHERE user_id = ?",
                String.class, actor.userId())).isEqualTo("deepseek-flash");
        jdbcTemplate.update("UPDATE user_llm_config SET created_at = '2020-01-01', updated_at = '2020-01-01' WHERE user_id = ?",
                actor.userId());

        ApiClient.requireOk(api.put(token, "/users/llm-config", config("qwen3.8-flash", "updated")));

        var times = jdbcTemplate.queryForMap("SELECT created_at, updated_at FROM user_llm_config WHERE user_id = ?",
                actor.userId());
        assertThat(times.get("created_at").toString()).startsWith("2020-01-01");
        assertThat(times.get("updated_at").toString()).doesNotStartWith("2020-01-01");
    }

    @Test
    void 配置提交前发生模型调用_提交后模型调用使用新配置() throws Exception {
        ChatModel original = modelReplying("原配置回复");
        ChatModel updated = modelReplying("更新配置回复");
        when(clients.buildUserModel(ModelCode.DEEPSEEK_FLASH, "original")).thenReturn(original);
        when(clients.buildUserModel(ModelCode.QWEN_PLUS, "updated")).thenReturn(updated);
        putConfig("deepseek-flash", "original");
        assertReplies("原配置回复");

        try (var executor = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                putConfig("qwen3.8-flash", "updated");
                try {
                    executor.submit(() -> assertReplies("原配置回复")).get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new AssertionError("提交前的独立调用未能完成", e);
                }
            });
        }

        assertReplies("更新配置回复");
    }

    @Test
    void 配置保存或删除回滚_保留原配置和模型调用结果() throws Exception {
        ChatModel original = modelReplying("原配置回复");
        when(clients.buildUserModel(ModelCode.DEEPSEEK_FLASH, "original")).thenReturn(original);
        putConfig("deepseek-flash", "original");
        assertReplies("原配置回复");
        String ciphertext = jdbcTemplate.queryForObject(
                "SELECT api_key_ciphertext FROM user_llm_config WHERE user_id = ?", String.class, actor.userId());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            putConfig("qwen3.8-flash", "updated");
            status.setRollbackOnly();
        });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT api_key_ciphertext FROM user_llm_config WHERE user_id = ?", String.class, actor.userId()))
                .isEqualTo(ciphertext);
        assertReplies("原配置回复");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            try {
                ApiClient.requireOk(api.delete(token, "/users/llm-config"));
            } catch (Exception e) {
                throw new AssertionError("删除配置请求失败", e);
            }
            status.setRollbackOnly();
        });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT api_key_ciphertext FROM user_llm_config WHERE user_id = ?", String.class, actor.userId()))
                .isEqualTo(ciphertext);
        assertReplies("原配置回复");
    }

    @Test
    void 同用户并发首次保存_均成功并按数据库写入顺序覆盖() throws Exception {
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                putConfig("deepseek-flash", "first");
                firstWritten.countDown();
                try {
                    assertThat(commitFirst.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }));
            try {
                assertThat(firstWritten.await(10, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> putConfig("qwen3.8-flash", "second"));
                await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE datname = current_database() "
                                + "AND wait_event_type = 'Lock' AND query ILIKE '%user_llm_config%')", Boolean.class)));
                commitFirst.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            } finally {
                commitFirst.countDown();
            }
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM user_llm_config WHERE user_id = ?",
                Integer.class, actor.userId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT model_code FROM user_llm_config WHERE user_id = ?",
                String.class, actor.userId())).isEqualTo("qwen3.8-flash");
        ChatModel updated = modelReplying("并发保存后的配置回复");
        when(clients.buildUserModel(ModelCode.QWEN_PLUS, "second")).thenReturn(updated);
        assertReplies("并发保存后的配置回复");
    }

    private void putConfig(String modelCode, String apiKey) {
        try {
            ApiClient.requireOk(api.put(token, "/users/llm-config", config(modelCode, apiKey)));
        } catch (Exception e) {
            throw new AssertionError("保存配置请求失败", e);
        }
    }

    private void assertReplies(String expected) {
        assertThat(gateway.generate(actor, new ModelRequest("hi", null, null))).isEqualTo(expected);
        chatMemory.clear("11");
        assertThat(gateway.stream(actor, 11L, new ModelRequest("hi", null, null))
                .collectList().block(Duration.ofSeconds(10))).containsExactly(expected);
    }

    private static ChatModel modelReplying(String reply) {
        ChatModel model = mock(ChatModel.class);
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        when(model.call(any(Prompt.class))).thenReturn(response);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(response));
        return model;
    }

    private static String config(String modelCode, String apiKey) {
        return "{\"modelCode\":\"%s\",\"apiKey\":\"%s\"}".formatted(modelCode, apiKey);
    }
}
