package com.zdmj.integration.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.time.Duration;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.ExternalAiStubConfig;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;

@AutoConfigureMockMvc
@org.springframework.boot.test.context.SpringBootTest(webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
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

        ExternalAiStubConfig.clearModelTransactionObservations();
        String stream = send(token, conversationId, "你好");
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

        assertThat(ExternalAiStubConfig.modelTransactionObservations()).hasSize(1);

        String conversation = ApiClient.requireOk(api.get(token, "/conversations/" + conversationId));
        assertThat(JsonPath.<String>read(conversation, "$.data.title")).isEqualTo("你好");
        send(token, conversationId, "第二个问题");
        assertThat(jdbc.queryForList("SELECT sequence FROM messages WHERE conversation_id=? ORDER BY sequence",
                Integer.class, conversationId)).containsExactly(1, 2, 3, 4);
        assertThat(jdbc.queryForObject("SELECT message_count FROM conversations WHERE id=?", Integer.class, conversationId)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT title FROM conversations WHERE id=?", String.class, conversationId)).isEqualTo("你好");
        assertThat(jdbc.queryForList("SELECT generation_status FROM messages WHERE role=2 AND conversation_id=? ORDER BY sequence",
                Integer.class, conversationId)).containsExactly(2, 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM messages a JOIN messages q ON q.id=a.reply_to_message_id WHERE a.conversation_id=? AND a.user_id=q.user_id AND a.conversation_id=q.conversation_id AND q.role=1", Integer.class, conversationId)).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "UPDATE messages SET generation_status=NULL,reply_to_message_id=NULL WHERE conversation_id=? AND role=2", conversationId))
                .hasMessageContaining("ck_messages_generation");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO messages(conversation_id,user_id,role,content,sequence,reply_to_message_id,generation_status)
                SELECT conversation_id,user_id,role,content,5,reply_to_message_id,generation_status
                FROM messages WHERE conversation_id=? AND sequence=2
                """, conversationId)).hasMessageContaining("uk_messages_reply_to");
    }
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean(name = "testChatModel")
    private org.springframework.ai.chat.model.ChatModel model;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired
    private org.mybatis.spring.SqlSessionTemplate sqlSession;

    @Test
    void 已保存的失败问答_下一轮继续任务时进入真实模型输入() throws Exception {
        var user = testUsers.createPair().userA();
        String token = testJwt.authorize(user);
        long id = ApiClient.dataId(api.post(token, "/conversations", """
                {"config":{"useSystemKnowledge":false,"ragDocumentIds":[]}}
                """));
        jdbc.update("INSERT INTO messages (conversation_id,user_id,role,content,sequence) VALUES (?,?,1,?,1)",
                id, user.getId(), "请分析三个项目");
        jdbc.update("""
                INSERT INTO messages (conversation_id,user_id,role,content,sequence,reply_to_message_id,generation_status)
                SELECT ?,?,2,?,2,id,3 FROM messages WHERE conversation_id=? AND sequence=1
                """, id, user.getId(), "第一个项目建议补充指标", id);
        jdbc.update("UPDATE conversations SET message_count=2 WHERE id=?", id);

        send(token, id, "继续未完成的任务");
        var captor = org.mockito.ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        org.mockito.Mockito.verify(model).stream(captor.capture());
        assertThat(captor.getValue().getInstructions()).extracting(org.springframework.ai.chat.messages.Message::getText)
                .containsSubsequence("请分析三个项目", "第一个项目建议补充指标", "继续未完成的任务");
    }

    @Autowired
    private com.zdmj.conversationService.service.MessageService messageService;
    @Autowired
    private com.zdmj.conversationService.service.ConversationService conversationService;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private com.zdmj.conversationService.mapper.MessageMapper messages;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {3, 4, 5})
    void 历史窗口保留最近20轮_空回复保留问题_用户与会话相互隔离(int state) throws Exception {
        var pair = testUsers.createPair();
        String token = testJwt.authorize(pair.userA());
        long id = createConversation(token);
        long other = createConversation(token);
        for (int turn = 1; turn <= 21; turn++) {
            seedTurn(id, pair.userA().getId(), turn, "问题" + turn, turn == 21 ? "" : "回复" + turn, state);
        }
        seedTurn(other, pair.userA().getId(), 1, "其他会话的隐私", "其他回复", 2);
        long foreign = createConversation(testJwt.authorize(pair.userB()));
        seedTurn(foreign, pair.userB().getId(), 1, "其他用户的隐私", "他人回复", 2);
        jdbc.update("UPDATE conversations SET message_count=42 WHERE id=?", id);
        send(token, id, "继续未完成的任务");
        var captured = org.mockito.ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        org.mockito.Mockito.verify(model).stream(captured.capture());
        var prompt = captured.getValue();
        var text = prompt.getInstructions().stream()
                .filter(m -> m.getMessageType() != org.springframework.ai.chat.messages.MessageType.SYSTEM)
                .map(org.springframework.ai.chat.messages.Message::getText).toList();
        assertThat(text).hasSize(40).startsWith("问题2", "回复2").endsWith("问题21", "继续未完成的任务");
        assertThat(text).doesNotContain("问题1", "回复1", "其他会话的隐私", "其他用户的隐私", "");
        assertThat(prompt.getSystemMessage().getText()).contains(switch (state) {
            case 3 -> "生成失败";
            case 4 -> "生成已取消";
            default -> "完整性未知";
        });
        assertThat(jdbc.queryForObject("SELECT to_regclass('spring_ai_chat_memory')", String.class)).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void 生成失败或取消_保存部分回复和状态_下一次请求仍能继续(boolean failed) throws Exception {
        var user = testUsers.createPair().userA();
        String token = testJwt.authorize(user);
        long id = createConversation(token);
        var part = new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(
                new org.springframework.ai.chat.model.Generation(
                        new org.springframework.ai.chat.messages.AssistantMessage("部分结果"))));
        reactor.core.publisher.Flux<org.springframework.ai.chat.model.ChatResponse> tail = failed
                ? reactor.core.publisher.Flux.error(new IllegalStateException("模拟模型中断"))
                : reactor.core.publisher.Flux.never();
        org.mockito.Mockito.doReturn(reactor.core.publisher.Flux.just(part).concatWith(tail))
                .when(model).stream(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class));
        var flow = messageService.createStream(new com.zdmj.common.context.CurrentActor(user.getId()), chatRequest(id, "原始任务"));
        if (failed) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> flow.collectList().block())
                    .hasMessageContaining("模拟模型中断");
        } else {
            var firstDelta = new java.util.concurrent.CountDownLatch(1);
            var subscription = flow.subscribe(event -> firstDelta.countDown());
            assertThat(firstDelta.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            subscription.dispose();
        }
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(jdbc.queryForMap("SELECT content,generation_status FROM messages WHERE conversation_id=? AND role=2", id))
                        .containsEntry("content", "部分结果").containsEntry("generation_status", failed ? 3 : 4));
        org.mockito.Mockito.reset(model);
        send(token, id, "继续");
        var captured = org.mockito.ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        org.mockito.Mockito.verify(model).stream(captured.capture());
        assertThat(captured.getValue().getInstructions()).extracting(org.springframework.ai.chat.messages.Message::getText)
                .containsSubsequence("原始任务", "部分结果", "继续");
    }

    @Test
    void 正在生成时并发发送_仅建立一轮问答_删除后回调不会重建消息() throws Exception {
        var users = testUsers.createPair();
        var user = users.userA();
        var actor = new com.zdmj.common.context.CurrentActor(user.getId());
        long id = createConversation(testJwt.authorize(user));
        var upstream = reactor.core.publisher.Sinks.many().unicast()
                .<org.springframework.ai.chat.model.ChatResponse>onBackpressureBuffer();
        var upstreamCancelled = new java.util.concurrent.atomic.AtomicBoolean();
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        var rejected = new java.util.concurrent.CompletableFuture<Throwable>();
        var started = new java.util.concurrent.CountDownLatch(1);
        var completed = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doReturn(upstream.asFlux().doOnSubscribe(ignored -> started.countDown())
                .doOnCancel(() -> upstreamCancelled.set(true))).when(model)
                .stream(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class));
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<reactor.core.Disposable> start = () -> {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return messageService.createStream(actor, chatRequest(id, "任务"))
                        .doOnComplete(completed::countDown).subscribe(ignored -> { }, rejected::complete);
            };
            var a = pool.submit(start);
            var b = pool.submit(start);
            var subscriptions = java.util.List.of(a.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    b.get(10, java.util.concurrent.TimeUnit.SECONDS));
            try {
                assertThat(rejected.get(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(com.zdmj.common.exception.BusinessException.class)
                        .extracting(error -> ((com.zdmj.common.exception.BusinessException) error).getErrorCode())
                        .isEqualTo(com.zdmj.common.exception.ErrorCode.CONVERSATION_GENERATING);
                assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(jdbc.queryForList("SELECT sequence FROM messages WHERE conversation_id=? ORDER BY sequence", Integer.class, id))
                        .containsExactly(1, 2);
                assertThat(jdbc.queryForObject("SELECT message_count FROM conversations WHERE id=?", Integer.class, id)).isEqualTo(2);
                String otherToken = testJwt.authorize(users.userB());
                assertChatProblem(otherToken, id, 403, 1003);
                ApiClient.assertProblem(api.delete(otherToken, "/conversations/" + id), 403, 1003);
                assertThat(upstreamCancelled.get()).isFalse();
                assertChatProblem(testJwt.authorize(user), id, 409, 9007);
                conversationService.delete(actor, id);
                assertThat(upstreamCancelled.get()).isTrue();
                assertThat(completed.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=?", Integer.class, id)).isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM conversations WHERE id=?", Integer.class, id)).isZero();
            } finally {
                subscriptions.forEach(reactor.core.Disposable::dispose);
            }
        }
    }

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactions;

    @Test
    void 未订阅不建立问答_不存在和他人会话返回原有错误契约() throws Exception {
        var users = testUsers.createPair();
        String token = testJwt.authorize(users.userA());
        long id = createConversation(token);
        messageService.createStream(new com.zdmj.common.context.CurrentActor(users.userA().getId()), chatRequest(id, "问题"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=?", Integer.class, id)).isZero();
        org.mockito.Mockito.verifyNoInteractions(model);
        assertChatProblem(token, Long.MAX_VALUE, 404, 9003);
        assertChatProblem(testJwt.authorize(users.userB()), id, 403, 1003);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=?", Integer.class, id)).isZero();
    }

    @Test
    void 删除事务回滚_生成继续且完整回复可以保存() throws Exception {
        var user = testUsers.createPair().userA();
        var actor = new com.zdmj.common.context.CurrentActor(user.getId());
        long id = createConversation(testJwt.authorize(user));
        var started = new java.util.concurrent.CountDownLatch(1);
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        var upstream = reactor.core.publisher.Sinks.many().unicast()
                .<org.springframework.ai.chat.model.ChatResponse>onBackpressureBuffer();
        org.mockito.Mockito.doReturn(upstream.asFlux().doOnSubscribe(ignored -> started.countDown())
                .doOnCancel(() -> cancelled.set(true))).when(model)
                .stream(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class));
        var completed = messageService.createStream(actor, chatRequest(id, "问题")).collectList().toFuture();
        try {
            assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            transactions.executeWithoutResult(status -> {
                conversationService.delete(actor, id);
                assertThat(cancelled.get()).isFalse();
                status.setRollbackOnly();
            });
            assertThat(cancelled.get()).isFalse();
            upstream.tryEmitNext(new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(
                    new org.springframework.ai.chat.model.Generation(new org.springframework.ai.chat.messages.AssistantMessage("回滚后继续")))))
                    .orThrow();
            upstream.tryEmitComplete().orThrow();
            assertThat(completed.get(5, java.util.concurrent.TimeUnit.SECONDS)).hasSize(1);
            assertThat(jdbc.queryForMap("SELECT content,generation_status FROM messages WHERE conversation_id=? AND role=2", id))
                    .containsEntry("content", "回滚后继续").containsEntry("generation_status", 2);
            assertThat(jdbc.queryForObject("SELECT message_count FROM conversations WHERE id=?", Integer.class, id)).isEqualTo(2);
        } finally {
            completed.cancel(true);
        }
    }

    @Test
    void 准备事务提交前取消_已提交问答保存取消状态且不启动模型() throws Exception {
        var user = testUsers.createPair().userA();
        long id = createConversation(testJwt.authorize(user));
        var inserted = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var realMapper = sqlSession.getMapper(com.zdmj.conversationService.mapper.MessageMapper.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            com.zdmj.conversationService.entity.Message message = invocation.getArgument(0);
            int rows = realMapper.insert(message);
            inserted.countDown();
            // 控制准备事务窗口，保证取消与数据库提交重叠；中断由取消清理处理。
            boolean released = false;
            while (!released) {
                try {
                    released = release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    if (!released) throw new IllegalStateException("等待准备事务释放超时");
                } catch (InterruptedException expected) {
                    // 取消会中断准备线程，继续完成已开始的短事务以检验提交后的清理。
                }
            }
            return rows;
        }).when(messages).insert(org.mockito.ArgumentMatchers.argThat(
                (com.zdmj.conversationService.entity.Message message) -> message.getRole() == 2));
        var subscription = messageService.createStream(new com.zdmj.common.context.CurrentActor(user.getId()),
                chatRequest(id, "问题")).subscribe();
        try {
            assertThat(inserted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            subscription.dispose();
            release.countDown();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("SELECT generation_status FROM messages WHERE conversation_id=? AND role=2",
                            Integer.class, id)).isEqualTo(4));
            assertThat(jdbc.queryForObject("SELECT message_count FROM conversations WHERE id=?", Integer.class, id)).isEqualTo(2);
            org.mockito.Mockito.verifyNoInteractions(model);
        } finally {
            release.countDown();
            subscription.dispose();
        }
    }

    @Test
    void 取消后终态保存失败_保留生成中状态且拒绝下一次生成() throws Exception {
        var user = testUsers.createPair().userA();
        var actor = new com.zdmj.common.context.CurrentActor(user.getId());
        long id = createConversation(testJwt.authorize(user));
        var delta = new java.util.concurrent.CountDownLatch(1);
        var failed = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doReturn(reactor.core.publisher.Flux.just(new org.springframework.ai.chat.model.ChatResponse(
                java.util.List.of(new org.springframework.ai.chat.model.Generation(
                        new org.springframework.ai.chat.messages.AssistantMessage("部分内容")))))
                .concatWith(reactor.core.publisher.Flux.never())).when(model)
                .stream(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            failed.countDown();
            throw new IllegalStateException("模拟取消保存失败");
        }).when(messages).finishAssistant(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(user.getId()),
                org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.eq("部分内容"), org.mockito.ArgumentMatchers.eq(4));
        var subscription = messageService.createStream(actor, chatRequest(id, "问题")).subscribe(event -> delta.countDown());
        try {
            assertThat(delta.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            subscription.dispose();
            assertThat(failed.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.createStream(actor, chatRequest(id, "继续"))
                            .collectList().block(Duration.ofSeconds(5)))
                    .isInstanceOf(com.zdmj.common.exception.BusinessException.class)
                    .extracting(error -> ((com.zdmj.common.exception.BusinessException) error).getErrorCode())
                    .isEqualTo(com.zdmj.common.exception.ErrorCode.CONVERSATION_GENERATING);
            assertThat(jdbc.queryForMap("SELECT content,generation_status FROM messages WHERE conversation_id=? AND role=2", id))
                    .containsEntry("content", "").containsEntry("generation_status", 1);
        } finally {
            subscription.dispose();
            conversationService.delete(actor, id);
        }
    }

    @Test
    void HTTP客户端断开_取消上游并保存已经接收的内容() throws Exception {
        var user = testUsers.createPair().userA();
        String token = testJwt.authorize(user);
        long id = createConversation(token);
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doReturn(reactor.core.publisher.Flux.interval(Duration.ofMillis(30))
                .map(sequence -> new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(
                        new org.springframework.ai.chat.model.Generation(new org.springframework.ai.chat.messages.AssistantMessage("增量")))))
                .doOnCancel(() -> cancelled.set(true))).when(model)
                .stream(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class));
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                        "http://127.0.0.1:" + port + ApiClient.PREFIX + "/messages/chat"))
                .timeout(Duration.ofSeconds(10))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        "{\"conversationId\":" + id + ",\"message\":\"问题\"}"))
                .build();
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
            assertThat(response.statusCode()).isEqualTo(200);
            try (var body = response.body()) {
                assertThat(body.readNBytes(16)).isNotEmpty();
            }
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(cancelled.get()).isTrue();
                var answer = jdbc.queryForMap("SELECT content,generation_status FROM messages WHERE conversation_id=? AND role=2", id);
                assertThat(answer).containsEntry("generation_status", 4);
                assertThat((String) answer.get("content")).contains("增量");
            });
        }
    }

    @Test
    void 终态保存持有行锁时删除_两项事务完成且没有遗留消息() throws Exception {
        var user = testUsers.createPair().userA();
        var actor = new com.zdmj.common.context.CurrentActor(user.getId());
        long id = createConversation(testJwt.authorize(user));
        var saving = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var deleting = new java.util.concurrent.CountDownLatch(1);
        var realMapper = sqlSession.getMapper(com.zdmj.conversationService.mapper.MessageMapper.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            int rows = realMapper.finishAssistant(invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4));
            saving.countDown();
            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("等待终态事务释放超时");
            return rows;
        }).when(messages).finishAssistant(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(user.getId()),
                org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(2));
        var completed = messageService.createStream(actor, chatRequest(id, "问题")).collectList().toFuture();
        try (var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            assertThat(saving.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var deleted = pool.submit(() -> {
                deleting.countDown();
                conversationService.delete(actor, id);
            });
            try {
                assertThat(deleting.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> deleted.get(100, java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally {
                release.countDown();
            }
            completed.get(5, java.util.concurrent.TimeUnit.SECONDS);
            deleted.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=?", Integer.class, id)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM conversations WHERE id=?", Integer.class, id)).isZero();
        } finally {
            release.countDown();
            completed.cancel(true);
        }
    }

    @Test
    void 助手占位写入失败_整轮消息与计数和标题共同回滚() throws Exception {
        var user = testUsers.createPair().userA();
        long id = createConversation(testJwt.authorize(user));
        var before = jdbc.queryForMap("SELECT message_count,title FROM conversations WHERE id=?", id);
        org.mockito.Mockito.doThrow(new IllegalStateException("模拟助手写入失败")).when(messages)
                .insert(org.mockito.ArgumentMatchers.argThat((com.zdmj.conversationService.entity.Message m) -> m.getRole() == 2));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.createStream(
                new com.zdmj.common.context.CurrentActor(user.getId()), chatRequest(id, "问题")).collectList().block(Duration.ofSeconds(5)))
                .hasMessage("模拟助手写入失败");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForMap("SELECT message_count,title FROM conversations WHERE id=?", id)).isEqualTo(before);
        org.mockito.Mockito.verify(model, org.mockito.Mockito.never())
                .stream(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void 终态写入或提交失败_数据库回滚且内存保持生成中(boolean commitFailure) throws Exception {
        var user = testUsers.createPair().userA();
        long id = createConversation(testJwt.authorize(user));
        var realMapper = sqlSession.getMapper(com.zdmj.conversationService.mapper.MessageMapper.class);
        var failure = new IllegalStateException("模拟终态保存失败");
        org.mockito.Mockito.doAnswer(invocation -> {
            int rows = realMapper.finishAssistant(invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4));
            if (commitFailure) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void beforeCommit(boolean readOnly) {
                                throw failure;
                            }
                        });
                return rows;
            }
            throw failure;
        }).when(messages).finishAssistant(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());

        var flow = messageService.createStream(new com.zdmj.common.context.CurrentActor(user.getId()), chatRequest(id, "问题"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> flow.collectList().block(java.time.Duration.ofSeconds(5)))
                .isSameAs(failure);
        assertThat(jdbc.queryForMap("SELECT content,generation_status FROM messages WHERE conversation_id=? AND role=2", id))
                .containsEntry("content", "").containsEntry("generation_status", 1);
        var inserted = org.mockito.ArgumentCaptor.forClass(com.zdmj.conversationService.entity.Message.class);
        org.mockito.Mockito.verify(messages, org.mockito.Mockito.times(2)).insert(inserted.capture());
        var answer = inserted.getAllValues().stream().filter(message -> message.getRole() == 2).findFirst().orElseThrow();
        assertThat(answer.getGenerationStatus()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT message_count FROM conversations WHERE id=?", Integer.class, id)).isEqualTo(2);
        org.mockito.Mockito.verify(messages).finishAssistant(answer.getId(), user.getId(), id,
                ExternalAiStubConfig.FIXED_REPLY, 2);
        conversationService.delete(new com.zdmj.common.context.CurrentActor(user.getId()), id);
    }

    @Test
    void 启动恢复_中断回复标记失败_保留已存内容并允许继续() throws Exception {
        var user = testUsers.createPair().userA();
        String token = testJwt.authorize(user);
        long id = createConversation(token);
        seedTurn(id, user.getId(), 1, "未完成的任务", "已保存的进度", 1);
        jdbc.update("UPDATE conversations SET message_count=2 WHERE id=?", id);
        ((com.zdmj.conversationService.service.impl.MessageServiceImpl) messageService).recoverInterruptedGenerations();
        assertThat(jdbc.queryForMap("SELECT content,generation_status FROM messages WHERE role=2"))
                .containsEntry("content", "已保存的进度").containsEntry("generation_status", 3);
        send(token, id, "继续");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "{\"ragDocumentIds\":\"10\"}", "{\"ragDocumentIds\":10}", "{\"ragDocumentIds\":true}",
            "{\"ragDocumentIds\":{}}", "{\"ragDocumentIds\":[\"10\"]}", "{\"ragDocumentIds\":[10.0]}",
            "{\"ragDocumentIds\":[true]}", "{\"ragDocumentIds\":[1,null]}", "{\"ragDocumentIds\":[0]}",
            "{\"ragDocumentIds\":[-1]}", "{\"ragDocumentIds\":[9223372036854775808]}",
            "{\"ragDocumentIds\":[-9223372036854775809]}", "{\"useSystemKnowledge\":\"true\"}",
            "{\"useSystemKnowledge\":0}", "{\"useSystemKnowledge\":1}", "{\"useSystemKnowledge\":[]}"
    })
    void 非法检索配置_创建和更新返回参数错误且数据库保持原值(String config) throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        long id = createConversation(token);
        String original = jdbc.queryForObject("SELECT config::text FROM conversations WHERE id=?", String.class, id);
        var created = api.post(token, "/conversations", "{\"config\":" + config + "}");
        ApiClient.assertProblem(created, 400, 1001);
        String key = config.contains("ragDocumentIds") ? "ragDocumentIds" : "useSystemKnowledge";
        assertThat(JsonPath.<String>read(created.getResponse().getContentAsString(), "$.detail")).contains(key);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversations", Integer.class)).isEqualTo(1);

        var updated = api.put(token, "/conversations/" + id + "/config", config);
        ApiClient.assertProblem(updated, 400, 1001);
        assertThat(JsonPath.<String>read(updated.getResponse().getContentAsString(), "$.detail")).contains(key);
        assertThat(jdbc.queryForObject("SELECT config::text FROM conversations WHERE id=?", String.class, id)).isEqualTo(original);
        org.mockito.Mockito.verifyNoInteractions(model);
    }

    @Test
    void 合法检索配置_局部更新保留省略字段_明确空值恢复缺省_空数组保留() throws Exception {
        String token = testJwt.authorize(testUsers.createPair().userA());
        long id = ApiClient.dataId(api.post(token, "/conversations", """
                {"config":{"useSystemKnowledge":true,"ragDocumentIds":[1,9223372036854775807,1],"model":"ignored"}}
                """));
        String path = "/conversations/" + id + "/config";
        String initial = ApiClient.requireOk(api.get(token, "/conversations/" + id));
        assertThat(JsonPath.<java.util.Map<String, Object>>read(initial, "$.data.config")).doesNotContainKey("model");
        ApiClient.requireOk(api.put(token, path, "{\"useSystemKnowledge\":false}"));
        String stored = jdbc.queryForObject("SELECT config::text FROM conversations WHERE id=?", String.class, id);
        assertThat(JsonPath.<java.util.List<Number>>read(stored, "$.ragDocumentIds"))
                .extracting(Number::longValue).containsExactly(1L, Long.MAX_VALUE, 1L);
        assertThat(JsonPath.<Boolean>read(stored, "$.useSystemKnowledge")).isFalse();

        ApiClient.requireOk(api.put(token, path, "{\"ragDocumentIds\":[]}"));
        stored = jdbc.queryForObject("SELECT config::text FROM conversations WHERE id=?", String.class, id);
        assertThat(JsonPath.<java.util.List<?>>read(stored, "$.ragDocumentIds")).isEmpty();
        assertThat(JsonPath.<Boolean>read(stored, "$.useSystemKnowledge")).isFalse();
        ApiClient.requireOk(api.put(token, path, "{\"useSystemKnowledge\":true}"));
        ApiClient.requireOk(api.put(token, path, "{\"ragDocumentIds\":null,\"useSystemKnowledge\":null}"));
        assertThat(jdbc.queryForMap("SELECT config->'ragDocumentIds' AS ids, config->>'useSystemKnowledge' AS flag FROM conversations WHERE id=?", id))
                .containsEntry("ids", null).containsEntry("flag", "false");

        long defaultId = ApiClient.dataId(api.post(token, "/conversations", "{\"config\":{}}"));
        long nullId = ApiClient.dataId(api.post(token, "/conversations", "{\"config\":{\"ragDocumentIds\":null,\"useSystemKnowledge\":null}}"));
        assertThat(jdbc.queryForObject("SELECT config = (SELECT config FROM conversations WHERE id=?) FROM conversations WHERE id=?",
                Boolean.class, defaultId, nullId)).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{\"ragDocumentIds\":\"invalid\"}", "{\"useSystemKnowledge\":1}"})
    void 已存非法配置_更新返回系统错误_生成立即失败且不调用模型(String config) throws Exception {
        var user = testUsers.createPair().userA();
        String token = testJwt.authorize(user);
        long id = createConversation(token);
        jdbc.update("UPDATE conversations SET config=CAST(? AS jsonb) WHERE id=?", config, id);
        String original = jdbc.queryForObject("SELECT config::text FROM conversations WHERE id=?", String.class, id);
        ApiClient.assertProblem(api.put(token, "/conversations/" + id + "/config",
                "{\"useSystemKnowledge\":false,\"ragDocumentIds\":[]}"), 500, 1008);
        assertThat(jdbc.queryForObject("SELECT config::text FROM conversations WHERE id=?", String.class, id)).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=?", Integer.class, id)).isZero();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.createStream(
                new com.zdmj.common.context.CurrentActor(user.getId()), chatRequest(id, "测试问题")).collectList().block())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("conversationId=" + id)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verifyNoInteractions(model);
        assertThat(jdbc.queryForObject("SELECT generation_status FROM messages WHERE conversation_id=? AND role=2",
                Integer.class, id)).isEqualTo(3);
    }

    private void assertChatProblem(String token, long id, int status, int code) throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                        "http://127.0.0.1:" + port + ApiClient.PREFIX + "/messages/chat"))
                .timeout(Duration.ofSeconds(10))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE + "," + MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        "{\"conversationId\":" + id + ",\"message\":\"问题\"}"))
                .build();
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
            assertThat(response.headers().firstValue("content-type").orElseThrow()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            assertThat(JsonPath.<Integer>read(response.body(), "$.code")).isEqualTo(code);
        }
    }

    private long createConversation(String token) throws Exception {
        return ApiClient.dataId(api.post(token, "/conversations", "{\"config\":{\"useSystemKnowledge\":false,\"ragDocumentIds\":[]}}"));
    }

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    private String send(String token, long id, String question) throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + ApiClient.PREFIX + "/messages/chat"))
                .timeout(java.time.Duration.ofSeconds(10))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        "{\"conversationId\":" + id + ",\"message\":\"" + question + "\"}"))
                .build();
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            return response.body();
        }
    }

    private com.zdmj.conversationService.dto.ChatStreamRequest chatRequest(long id, String question) {
        var request = new com.zdmj.conversationService.dto.ChatStreamRequest(id, question);

        return request;
    }

    private void seedTurn(long id, long userId, int turn, String question, String reply, int state) {
        long questionId = jdbc.queryForObject("INSERT INTO messages (conversation_id,user_id,role,content,sequence) VALUES (?,?,1,?,?) RETURNING id",
                Long.class, id, userId, question, turn * 2 - 1);
        jdbc.update("INSERT INTO messages (conversation_id,user_id,role,content,sequence,reply_to_message_id,generation_status) VALUES (?,?,2,?,?,?,?)",
                id, userId, reply, turn * 2, questionId, state);
    }
}
