package com.zdmj.integration.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.conversationService.dto.ChatStreamRequest;
import com.zdmj.conversationService.mapper.MessageMapper;
import com.zdmj.conversationService.service.MessageService;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.DatabaseCleaner;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

/** 通过真实 SSE、模型替身和 PostgreSQL 验证生成全周期的日志关联。 */
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConversationTraceIT extends IntegrationTestBase {

    @Autowired private MockMvc mvc;
    @Autowired private TestUsers users;
    @Autowired private TestJwt jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SqlSessionTemplate sessions;
    @Autowired private MessageService messages;
    @LocalServerPort private int port;
    @MockitoSpyBean(name = "testChatModel") private ChatModel model;
    @MockitoSpyBean private MessageMapper mapper;

    private final List<String> modelTraces = new CopyOnWriteArrayList<>();
    private final List<String> saveTraces = new CopyOnWriteArrayList<>();

    @Autowired
    ConversationTraceIT(DatabaseCleaner cleaner) {
        super(cleaner);
    }

    @AfterEach
    void callerContextRemainsClean() {
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void 完整HTTP回复_检索模型回调及提交日志关联响应编号(CapturedOutput output) throws Exception {
        String token = jwt.authorize(users.createPair().userA());
        long id = createConversation(token);
        stubModel(false);
        captureSaveTrace();

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request(token, id), HttpResponse.BodyHandlers.ofString());
            String traceId = traceId(response);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("event:delta", "追踪回复");
            assertThat(modelTraces).isNotEmpty().containsOnly(traceId);
            assertThat(saveTraces).containsExactly(traceId);
            assertThat(jdbc.queryForObject("SELECT generation_status FROM messages WHERE conversation_id=? AND role=2",
                    Integer.class, id)).isEqualTo(2);
            assertGenerationLog(output, traceId, id, "会话生成保存完成", "status=COMPLETED");
            assertThat(output.getAll().lines().filter(line -> line.contains("[traceId=" + traceId + "]")))
                    .anyMatch(line -> line.contains("RAG 检索完成"))
                    .anyMatch(line -> line.contains("HTTP 请求进入异步处理"))
                    .noneMatch(line -> line.contains("operationId="));
        }
    }

    @Test
    void 首个片段前模型失败_异步错误响应保持原请求编号(CapturedOutput output) throws Exception {
        String token = jwt.authorize(users.createPair().userA());
        long id = createConversation(token);
        doReturn(Flux.error(new IllegalStateException("trace-test-model-failure")))
                .when(model).stream(any(Prompt.class));
        captureSaveTrace();

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request(token, id), HttpResponse.BodyHandlers.ofString());
            String traceId = traceId(response);
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(JsonPath.<String>read(response.body(), "$.traceId")).isEqualTo(traceId);
            assertThat(saveTraces).containsExactly(traceId);
            assertGenerationLog(output, traceId, id, "会话生成保存完成", "status=FAILED");
        }
    }

    @Test
    void HTTP客户端断开_后续取消保存继续关联原请求(CapturedOutput output) throws Exception {
        String token = jwt.authorize(users.createPair().userA());
        long id = createConversation(token);
        stubModel(true);
        captureSaveTrace();

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request(token, id), HttpResponse.BodyHandlers.ofInputStream());
            String traceId = traceId(response);
            try (var body = response.body()) {
                assertThat(body.readNBytes(16)).isNotEmpty();
            }
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(jdbc.queryForObject("SELECT generation_status FROM messages WHERE conversation_id=? AND role=2",
                        Integer.class, id)).isEqualTo(4);
                assertThat(saveTraces).containsExactly(traceId);
                assertGenerationLog(output, traceId, id, "会话生成保存完成", "status=CANCELLED");
            });
            assertThat(modelTraces).isNotEmpty().containsOnly(traceId);
        }
    }

    @Test
    void 删除中的生成_原生成清理与删除请求保持各自编号(CapturedOutput output) throws Exception {
        String token = jwt.authorize(users.createPair().userA());
        long id = createConversation(token);
        stubModel(true);

        try (var client = HttpClient.newHttpClient()) {
            var stream = client.send(request(token, id), HttpResponse.BodyHandlers.ofInputStream());
            String generationTrace = traceId(stream);
            try (var body = stream.body()) {
                assertThat(body.readNBytes(16)).isNotEmpty();
                var deletion = client.send(HttpRequest.newBuilder(uri("/conversations/" + id))
                        .header("Authorization", "Bearer " + token).DELETE().build(), HttpResponse.BodyHandlers.ofString());
                String deletionTrace = traceId(deletion);
                assertThat(deletion.statusCode()).isEqualTo(200);
                assertThat(deletionTrace).isNotEqualTo(generationTrace);
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                        assertGenerationLog(output, generationTrace, id, "会话生成结束", "reason=DELETED"));
                assertThat(output.getAll().lines().filter(line -> line.contains("[traceId=" + deletionTrace + "]")))
                        .anyMatch(line -> line.contains("method=DELETE") && line.contains("status=200"))
                        .noneMatch(line -> line.contains("reason=DELETED"));
                assertThat(jdbc.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=?", Integer.class, id)).isZero();
            }
        }
    }

    @Test
    void 并发HTTP生成_工作线程中的编号分别属于各自请求(CapturedOutput output) throws Exception {
        String token = jwt.authorize(users.createPair().userA());
        long first = createConversation(token);
        long second = createConversation(token);
        stubModel(false);
        captureSaveTrace();

        try (var pool = Executors.newFixedThreadPool(2); var client = HttpClient.newHttpClient()) {
            var a = pool.submit(() -> client.send(request(token, first), HttpResponse.BodyHandlers.ofString()));
            var b = pool.submit(() -> client.send(request(token, second), HttpResponse.BodyHandlers.ofString()));
            var responseA = a.get(15, TimeUnit.SECONDS);
            var responseB = b.get(15, TimeUnit.SECONDS);
            String traceA = traceId(responseA);
            String traceB = traceId(responseB);
            assertThat(responseA.statusCode()).isEqualTo(200);
            assertThat(responseB.statusCode()).isEqualTo(200);
            assertThat(traceA).isNotEqualTo(traceB);
            assertThat(modelTraces).containsOnly(traceA, traceB).contains(traceA, traceB);
            assertThat(saveTraces).containsExactlyInAnyOrder(traceA, traceB);
            assertGenerationLog(output, traceA, first, "会话生成保存完成", "status=COMPLETED");
            assertGenerationLog(output, traceB, second, "会话生成保存完成", "status=COMPLETED");
        }
    }

    @Test
    void 模型执行线程复用_请求完成后底层线程没有追踪编号残留() throws Exception {
        String token = jwt.authorize(users.createPair().userA());
        long id = createConversation(token);
        try (var executor = Executors.newSingleThreadScheduledExecutor()) {
            var scheduler = reactor.core.scheduler.Schedulers.fromExecutorService(executor);
            try {
                doReturn(Flux.just(reply()).delayElements(Duration.ofMillis(20), scheduler)
                        .doOnNext(value -> modelTraces.add(String.valueOf(MDC.get("traceId")))))
                        .when(model).stream(any(Prompt.class));
                try (var client = HttpClient.newHttpClient()) {
                    var response = client.send(request(token, id), HttpResponse.BodyHandlers.ofString());
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(modelTraces).containsExactly(traceId(response));
                }
                assertThat(executor.submit(() -> MDC.get("traceId")).get(5, TimeUnit.SECONDS)).isNull();
            } finally {
                scheduler.dispose();
            }
        }
    }

    @Test
    void 取消后的保存失败_外部取消线程的编号不会覆盖原生成编号(CapturedOutput output) throws Exception {
        var user = users.createPair().userA();
        long id = createConversation(jwt.authorize(user));
        stubModel(true);
        var firstDelta = new java.util.concurrent.CountDownLatch(1);
        String traceId = "0123456789abcdef0123456789abcdef";
        doAnswer(invocation -> {
            saveTraces.add(String.valueOf(MDC.get("traceId")));
            throw new IllegalStateException("trace-test-save-failure");
        }).when(mapper).finishAssistant(any(), any(), any(), any(), anyInt());

        var subscription = messages.createStream(new CurrentActor(user.getId()), new ChatStreamRequest(id, "追踪测试"))
                .contextWrite(context -> context.put("traceId", traceId))
                .subscribe(event -> firstDelta.countDown());
        try {
            assertThat(firstDelta.await(5, TimeUnit.SECONDS)).isTrue();
            MDC.put("traceId", "cancel-thread-trace");
            subscription.dispose();
            assertThat(MDC.get("traceId")).isEqualTo("cancel-thread-trace");
        } finally {
            MDC.remove("traceId");
            subscription.dispose();
        }
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(saveTraces).containsExactly(traceId);
            assertGenerationLog(output, traceId, id, "会话结束保存失败", "targetStatus=4");
        });
        assertThat(jdbc.queryForObject("SELECT generation_status FROM messages WHERE conversation_id=? AND role=2",
                Integer.class, id)).isEqualTo(1);
    }

    private void stubModel(boolean endless) {
        doAnswer(invocation -> {
            modelTraces.add(String.valueOf(MDC.get("traceId")));
            Flux<ChatResponse> values = endless ? Flux.interval(Duration.ofMillis(30)).map(index -> reply())
                    : Flux.just(reply()).delayElements(Duration.ofMillis(30));
            return values.doOnNext(value -> modelTraces.add(String.valueOf(MDC.get("traceId"))));
        }).when(model).stream(any(Prompt.class));
    }

    private ChatResponse reply() {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("追踪回复"))));
    }

    private void captureSaveTrace() {
        var real = sessions.getMapper(MessageMapper.class);
        doAnswer(invocation -> {
            saveTraces.add(String.valueOf(MDC.get("traceId")));
            return real.finishAssistant(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                    invocation.getArgument(3), invocation.getArgument(4));
        }).when(mapper).finishAssistant(any(), any(), any(), any(), anyInt());
    }

    private long createConversation(String token) throws Exception {
        return ApiClient.dataId(new ApiClient(mvc).post(token, "/conversations",
                "{\"config\":{\"useSystemKnowledge\":true,\"ragDocumentIds\":[]}}"));
    }

    private HttpRequest request(String token, long id) {
        return HttpRequest.newBuilder(uri("/messages/chat")).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .header("Accept", "text/event-stream,application/problem+json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"conversationId\":" + id + ",\"message\":\"追踪测试\"}"))
                .build();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + ApiClient.PREFIX + path);
    }

    private String traceId(HttpResponse<?> response) {
        String id = response.headers().firstValue("X-Trace-Id").orElseThrow();
        assertThat(id).matches("[0-9a-f]{32}");
        return id;
    }

    private void assertGenerationLog(CapturedOutput output, String traceId, long id, String event, String state) {
        assertThat(output.getAll().lines().filter(line -> line.contains("[traceId=" + traceId + "]")))
                .anyMatch(line -> line.contains(event) && line.contains(state)
                        && line.contains("conversationId=" + id + " "));
    }
}
