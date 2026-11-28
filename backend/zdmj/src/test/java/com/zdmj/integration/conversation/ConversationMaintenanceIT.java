package com.zdmj.integration.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.conversationService.dto.ChatStreamRequest;
import com.zdmj.conversationService.service.MessageService;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.DatabaseCleaner;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.MutableClock;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 验证会话维护的 HTTP 边界、分页隔离及应用时间写入。 */
@AutoConfigureMockMvc
class ConversationMaintenanceIT extends IntegrationTestBase {
    @Autowired private TestUsers users;
    @Autowired private TestJwt jwt;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;
    @Autowired private MessageService messages;

    @Autowired
    ConversationMaintenanceIT(DatabaseCleaner cleaner) { super(cleaner); }

    @ParameterizedTest
    @ValueSource(strings = {"标", "😀"})
    void 手工标题按码点限制_边界持久化且超长请求不写入(String unit) throws Exception {
        String token = jwt.authorize(users.createPair().userA());
        var api = new ApiClient(mvc);
        long id = ApiClient.dataId(api.post(token, "/conversations", "{}"));
        String title = unit.repeat(255);
        ApiClient.requireOk(api.exchange(put(ApiClient.PREFIX + "/conversations/" + id + "/title")
                .param("title", "  " + title + "  "), token, null));
        assertThat(jdbc.queryForObject("SELECT title FROM conversations WHERE id=?", String.class, id)).isEqualTo(title);
        ApiClient.assertProblem(api.exchange(put(ApiClient.PREFIX + "/conversations/" + id + "/title")
                .param("title", unit.repeat(256)), token, null), 400, 1001);
        assertThat(jdbc.queryForObject("SELECT title FROM conversations WHERE id=?", String.class, id)).isEqualTo(title);
    }

    @Test
    void 会话分页_相同时间按编号倒序且隔离用户与限制数量() throws Exception {
        var pair = users.createPair();
        String token = jwt.authorize(pair.userA());
        var api = new ApiClient(mvc);
        jdbc.update("INSERT INTO conversations(user_id,updated_at) SELECT ?,TIMESTAMP '2026-01-01' FROM generate_series(1,105)", pair.userA().getId());
        jdbc.update("INSERT INTO conversations(user_id,updated_at) VALUES (?,TIMESTAMP '2027-01-01')", pair.userB().getId());
        List<Long> expected = jdbc.queryForList("SELECT id FROM conversations WHERE user_id=? ORDER BY id DESC", Long.class, pair.userA().getId());
        String first = ApiClient.requireOk(api.get(token, "/conversations"));
        assertThat(JsonPath.<Integer>read(first, "$.data.total")).isEqualTo(105);
        assertThat(JsonPath.<Integer>read(first, "$.data.page")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(first, "$.data.limit")).isEqualTo(20);
        assertThat(JsonPath.<Integer>read(first, "$.data.totalPages")).isEqualTo(6);
        assertThat(ids(first)).containsExactlyElementsOf(expected.subList(0, 20));
        String second = ApiClient.requireOk(api.get(token, "/conversations?page=2&limit=20"));
        assertThat(ids(second)).containsExactlyElementsOf(expected.subList(20, 40));
        assertThat(ids(ApiClient.requireOk(api.get(token, "/conversations?page=0&limit=1000")))).hasSize(100);
        assertThat(ids(ApiClient.requireOk(api.get(token, "/conversations?page=7")))).isEmpty();
        assertThat(ids(ApiClient.requireOk(api.get(jwt.authorize(pair.userB()), "/conversations")))).hasSize(1);
        jdbc.update("DELETE FROM conversations WHERE user_id=?", pair.userB().getId());
        String empty = ApiClient.requireOk(api.get(jwt.authorize(pair.userB()), "/conversations"));
        assertThat(ids(empty)).isEmpty();
        assertThat(JsonPath.<Integer>read(empty, "$.data.total")).isZero();
    }

    @Test
    void 应用时钟_创建标题配置和消息时间准确且创建时间保持不变() throws Exception {
        var user = users.createPair().userA();
        String token = jwt.authorize(user);
        var api = new ApiClient(mvc);
        Instant start = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        clock.setInstant(start);
        long id = ApiClient.dataId(api.post(token, "/conversations", "{}"));
        LocalDateTime created = LocalDateTime.ofInstant(start, clock.getZone());
        assertThat(times(id).get("created_at")).isEqualTo(java.sql.Timestamp.valueOf(created));
        clock.setInstant(start.plusSeconds(10));
        ApiClient.requireOk(api.exchange(put(ApiClient.PREFIX + "/conversations/" + id + "/title").param("title", "标题"), token, null));
        assertThat(times(id).get("updated_at")).isEqualTo(java.sql.Timestamp.valueOf(created.plusSeconds(10)));
        clock.setInstant(start.plusSeconds(20));
        ApiClient.requireOk(api.put(token, "/conversations/" + id + "/config", "{\"ragDocumentIds\":[]}"));
        assertThat(times(id).get("updated_at")).isEqualTo(java.sql.Timestamp.valueOf(created.plusSeconds(20)));
        clock.setInstant(start.plusSeconds(30));
        messages.createStream(new CurrentActor(user.getId()), new ChatStreamRequest(id, "问题")).blockLast(Duration.ofSeconds(10));
        assertThat(times(id)).containsEntry("created_at", java.sql.Timestamp.valueOf(created))
                .containsEntry("updated_at", java.sql.Timestamp.valueOf(created.plusSeconds(30)))
                .containsEntry("last_message_at", java.sql.Timestamp.valueOf(created.plusSeconds(30)));
    }

    private Map<String,Object> times(long id) {
        return jdbc.queryForMap("SELECT created_at,updated_at,last_message_at FROM conversations WHERE id=?", id);
    }

    private List<Long> ids(String body) {
        return JsonPath.<List<Number>>read(body, "$.data.list[*].id").stream().map(Number::longValue).toList();
    }
}
