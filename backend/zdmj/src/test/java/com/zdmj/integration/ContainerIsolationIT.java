package com.zdmj.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zdmj.common.security.JwtSessionStore;
import com.zdmj.testsupport.DatabaseCleaner;
import com.zdmj.testsupport.ExternalAiStubConfig;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestMailSender;
import com.zdmj.testsupport.TestObjectStorage;
import com.zdmj.testsupport.TestUsers;

class ContainerIsolationIT extends IntegrationTestBase {

    private final TestUsers testUsers;
    private final TestJwt testJwt;
    private final JwtSessionStore jwtSessionStore;
    private final JdbcTemplate jdbcTemplate;
    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final TestMailSender testMailSender;
    private final TestObjectStorage testObjectStorage;

    @Autowired
    ContainerIsolationIT(DatabaseCleaner databaseCleaner, TestUsers testUsers, TestJwt testJwt,
            JwtSessionStore jwtSessionStore, JdbcTemplate jdbcTemplate, ChatModel chatModel,
            EmbeddingModel embeddingModel, TestMailSender testMailSender, TestObjectStorage testObjectStorage) {
        super(databaseCleaner);
        this.testUsers = testUsers;
        this.testJwt = testJwt;
        this.jwtSessionStore = jwtSessionStore;
        this.jdbcTemplate = jdbcTemplate;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.testMailSender = testMailSender;
        this.testObjectStorage = testObjectStorage;
    }

    @Test
    void 隔离环境_创建两名用户并登录_主键由数据库生成且令牌写入Redis() {
        TestUsers.Pair pair = testUsers.createPair();
        assertThat(pair.userA().getId()).isNotNull();
        assertThat(pair.userB().getId()).isNotEqualTo(pair.userA().getId());

        String token = testJwt.authorize(pair.userA());
        assertThat(jwtSessionStore.find(pair.userA().getId())).contains(token);
        assertThat(chatModel.call("ping")).isEqualTo(ExternalAiStubConfig.FIXED_REPLY);
        assertThat(embeddingModel.embed("hello")).hasSize(ExternalAiStubConfig.DIMENSION);
        assertThat(testMailSender.sentMessages()).isEmpty();
        assertThat(testObjectStorage.calls()).isEmpty();
    }

    @Test
    void 清理_写入用户和登录态之后再次清理_表与Redis恢复为空() {
        TestUsers.Pair pair = testUsers.createPair();
        long userId = pair.userA().getId();
        testJwt.authorize(pair.userA());

        databaseCleaner.clean();

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class);
        assertThat(count).isZero();
        assertThat(jwtSessionStore.find(userId)).isEmpty();
    }
}
