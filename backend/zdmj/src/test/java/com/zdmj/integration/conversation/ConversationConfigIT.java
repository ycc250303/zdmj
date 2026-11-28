package com.zdmj.integration.conversation;

import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.conversationService.dto.ConversationRequest;
import com.zdmj.conversationService.dto.ChatStreamRequest;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.conversationService.mapper.MessageMapper;
import com.zdmj.conversationService.service.ConversationService;
import com.zdmj.conversationService.service.MessageService;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.knowledgeService.api.KnowledgeRagService;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.DatabaseCleaner;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 使用真实数据库连接验证配置合并、事务回滚与会话行锁。 */
@AutoConfigureMockMvc
class ConversationConfigIT extends IntegrationTestBase {

    @Autowired
    private ConversationService service;
    @Autowired
    private MessageService messageService;
    @Autowired
    private TestUsers users;
    @Autowired
    private TestJwt jwt;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SqlSessionTemplate sqlSession;
    @MockitoSpyBean
    private ConversationMapper conversations;
    @MockitoSpyBean
    private MessageMapper messages;
    @MockitoSpyBean
    private KnowledgeRagService retrieval;

    @Autowired
    ConversationConfigIT(DatabaseCleaner cleaner) {
        super(cleaner);
    }

    @Test
    void 并发修改不同配置字段_两项修改均保留() throws Exception {
        var actor = new CurrentActor(users.createPair().userA().getId());
        long id = service.create(actor, new ConversationRequest(Map.of(
                "useSystemKnowledge", false, "ragDocumentIds", List.of(1L)))).id();
        var real = sqlSession.getMapper(ConversationMapper.class);
        var readers = new CountDownLatch(2);
        var reads = new AtomicInteger();
        doAnswer(invocation -> {
            var result = real.selectById((Long) invocation.getArgument(0));
            if (reads.incrementAndGet() <= 2) {
                readers.countDown();
                assertThat(readers.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(conversations).selectById(anyLong());

        try (var executor = Executors.newFixedThreadPool(2)) {
            var flag = executor.submit(() -> service.updateConfig(actor, id, Map.of("useSystemKnowledge", true)));
            var documents = executor.submit(() -> service.updateConfig(actor, id, Map.of("ragDocumentIds", List.of(2L, 2L))));
            flag.get(10, TimeUnit.SECONDS);
            documents.get(10, TimeUnit.SECONDS);
        }

        assertThat(service.getById(actor, id).config()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "useSystemKnowledge", true, "ragDocumentIds", List.of(2, 2)));
        assertThat(jdbc.queryForObject("SELECT message_count FROM conversations WHERE id=?", Integer.class, id)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 配置写入或提交前失败_配置和更新时间均回滚(boolean beforeCommit) throws Exception {
        var user = users.createPair().userA();
        var actor = new CurrentActor(user.getId());
        long id = service.create(actor, new ConversationRequest(Map.of("ragDocumentIds", List.of()))).id();
        jdbc.update("UPDATE conversations SET updated_at=TIMESTAMP '2020-01-01 00:00:00' WHERE id=?", id);
        var original = jdbc.queryForMap("SELECT config::text,updated_at FROM conversations WHERE id=?", id);
        var failure = new IllegalStateException("模拟配置保存失败");
        var real = sqlSession.getMapper(ConversationMapper.class);
        doAnswer(invocation -> {
            int rows = real.updateConfigByIdAndUserId(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3));
            if (beforeCommit) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        throw failure;
                    }
                });
                return rows;
            }
            throw failure;
        }).when(conversations).updateConfigByIdAndUserId(anyLong(), anyLong(), any(), any(LocalDateTime.class));

        if (beforeCommit) {
            var api = new ApiClient(mvc);
            ApiClient.assertProblem(api.put(jwt.authorize(user), "/conversations/" + id + "/config",
                    "{\"useSystemKnowledge\":true}"), 500, 1008);
        } else {
            assertThatThrownBy(() -> service.updateConfig(actor, id, Map.of("useSystemKnowledge", true)))
                    .isSameAs(failure);
        }
        assertThat(jdbc.queryForMap("SELECT config::text,updated_at FROM conversations WHERE id=?", id)).isEqualTo(original);
    }

    @Test
    void 同一字段并发修改_按行锁顺序覆盖且响应包含各自写入结果() throws Exception {
        var actor = new CurrentActor(users.createPair().userA().getId());
        long id = createIdle(actor);
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = new AtomicInteger();
        holdNextLock(acquired, release, holder);

        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var first = executor.submit(() -> service.updateConfig(actor, id, Map.of("ragDocumentIds", List.of(1L))));
                assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> service.updateConfig(actor, id, Map.of("ragDocumentIds", List.of(2L, 2L))));
                awaitBlockedBy(holder.get());
                release.countDown();
                assertThat(first.get(10, TimeUnit.SECONDS).config().get("ragDocumentIds")).isEqualTo(List.of(1));
                assertThat(second.get(10, TimeUnit.SECONDS).config().get("ragDocumentIds")).isEqualTo(List.of(2, 2));
            } finally {
                release.countDown();
            }
        }
        assertThat(service.getById(actor, id).config().get("ragDocumentIds")).isEqualTo(List.of(2, 2));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 配置更新与首条消息串行执行_检索使用已提交配置且后续修改冻结(boolean configFirst) throws Exception {
        var user = users.createPair().userA();
        var actor = new CurrentActor(user.getId());
        long id = createIdle(actor);
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = new AtomicInteger();
        holdNextLock(acquired, release, holder);
        var patch = Map.<String, Object>of("ragDocumentIds", List.of(2L, 2L));

        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var first = configFirst
                        ? executor.submit(() -> service.updateConfig(actor, id, patch))
                        : executor.submit(() -> send(actor, id));
                assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
                var second = configFirst
                        ? executor.submit(() -> send(actor, id))
                        : executor.submit(() -> service.updateConfig(actor, id, patch));
                awaitBlockedBy(holder.get());
                release.countDown();
                first.get(10, TimeUnit.SECONDS);
                if (configFirst) {
                    second.get(10, TimeUnit.SECONDS);
                } else {
                    assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).cause()
                            .isInstanceOfSatisfying(BusinessException.class, failure -> assertThat(failure.getCode()).isEqualTo(9006));
                }
            } finally {
                release.countDown();
            }
        }

        verify(retrieval).streamAnswer(eq(actor), eq(id), any(), eq(configFirst ? List.of(2L, 2L) : List.of()), eq(false));
        assertThat(jdbc.queryForObject("SELECT message_count FROM conversations WHERE id=?", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=?", Integer.class, id)).isEqualTo(2);
        ApiClient.assertProblem(new ApiClient(mvc).put(jwt.authorize(user), "/conversations/" + id + "/config",
                "{\"useSystemKnowledge\":true}"), 409, 9006);
    }

    @Test
    void 首条消息准备回滚_等待中的配置更新继续成功() throws Exception {
        var actor = new CurrentActor(users.createPair().userA().getId());
        long id = createIdle(actor);
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = new AtomicInteger();
        holdNextLock(acquired, release, holder);
        var failure = new IllegalStateException("模拟助手占位写入失败");
        doThrow(failure).when(messages).insert(argThat((com.zdmj.conversationService.entity.Message message) -> message.getRole() == 2));

        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var sent = executor.submit(() -> send(actor, id));
                assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
                var updated = executor.submit(() -> service.updateConfig(actor, id, Map.of("useSystemKnowledge", true)));
                awaitBlockedBy(holder.get());
                release.countDown();
                assertThatThrownBy(() -> sent.get(10, TimeUnit.SECONDS)).hasCause(failure);
                assertThat(updated.get(10, TimeUnit.SECONDS).config().get("useSystemKnowledge")).isEqualTo(true);
            } finally {
                release.countDown();
            }
        }
        assertThat(jdbc.queryForObject("SELECT message_count FROM conversations WHERE id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT title FROM conversations WHERE id=?", String.class, id)).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 配置更新与删除串行执行_删除后更新返回不存在(boolean configFirst) throws Exception {
        var actor = new CurrentActor(users.createPair().userA().getId());
        long id = createIdle(actor);
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = new AtomicInteger();
        holdNextLock(acquired, release, holder);

        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var first = configFirst
                        ? executor.submit(() -> service.updateConfig(actor, id, Map.of("useSystemKnowledge", true)))
                        : executor.submit(() -> service.delete(actor, id));
                assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
                var second = configFirst
                        ? executor.submit(() -> service.delete(actor, id))
                        : executor.submit(() -> service.updateConfig(actor, id, Map.of("useSystemKnowledge", true)));
                awaitBlockedBy(holder.get());
                release.countDown();
                first.get(10, TimeUnit.SECONDS);
                if (configFirst) {
                    second.get(10, TimeUnit.SECONDS);
                } else {
                    assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).cause()
                            .isInstanceOfSatisfying(BusinessException.class, failure -> assertThat(failure.getCode()).isEqualTo(9003));
                }
            } finally {
                release.countDown();
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversations WHERE id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=?", Integer.class, id)).isZero();
    }

    @Test
    void 配置更新拒绝空请求和他人会话及不存在会话() throws Exception {
        var pair = users.createPair();
        var actor = new CurrentActor(pair.userA().getId());
        long id = createIdle(actor);
        var api = new ApiClient(mvc);
        String token = jwt.authorize(pair.userA());
        var original = service.getById(actor, id);
        ApiClient.assertProblem(api.put(token, "/conversations/" + id + "/config", "{}"), 400, 1001);
        ApiClient.assertProblem(api.put(jwt.authorize(pair.userB()), "/conversations/" + id + "/config",
                "{\"useSystemKnowledge\":true}"), 403, 1003);
        ApiClient.assertProblem(api.put(token, "/conversations/" + Long.MAX_VALUE + "/config",
                "{\"useSystemKnowledge\":true}"), 404, 9003);
        assertThat(service.getById(actor, id)).isEqualTo(original);
    }

    private long createIdle(CurrentActor actor) {
        return service.create(actor, new ConversationRequest(Map.of("ragDocumentIds", List.of()))).id();
    }

    private void send(CurrentActor actor, long id) {
        messageService.createStream(actor, new ChatStreamRequest(id, "测试问题")).collectList().block(Duration.ofSeconds(10));
    }

    /** 暂停首次锁定后的业务步骤，其他请求仍通过真实数据库连接竞争同一会话。 */
    private void holdNextLock(CountDownLatch acquired, CountDownLatch release, AtomicInteger holder) {
        var first = new AtomicBoolean(true);
        var real = sqlSession.getMapper(ConversationMapper.class);
        doAnswer(invocation -> {
            var conversation = real.selectOwnedForUpdate(invocation.getArgument(0), invocation.getArgument(1));
            if (first.compareAndSet(true, false)) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                holder.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                acquired.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return conversation;
        }).when(conversations).selectOwnedForUpdate(anyLong(), anyLong());
    }

    private void awaitBlockedBy(int holder) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)))",
                Boolean.class, holder)).isTrue());
    }
}
