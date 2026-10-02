package com.zdmj.testsupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zdmj.common.ai.UserLlmRouter;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.common.security.RedisJwtSessionStore;
import com.zdmj.common.util.UserApiKeyCipher;
import com.zdmj.userAuthService.mapper.UserLlmConfigMapper;
import com.zdmj.userAuthService.mapper.UserMapper;

import reactor.core.publisher.Flux;

/**
 * 固定的对话、流式输出与 1024 维向量，不访问外部模型。
 */
@TestConfiguration
public class ExternalAiStubConfig {

    public static final String FIXED_REPLY = "测试模型固定回复";
    public static final String FIXED_MATCH_SUMMARY = "固定匹配摘要";
    public static final int FIXED_MATCH_SCORE = 80;
    public static final int DIMENSION = 1024;

    static final String FIXED_MATCH_JSON = """
            {
              "summary": "%s",
              "matchedHighlights": ["固定亮点"],
              "criticalGaps": ["固定差距"],
              "matchedKeywords": [],
              "missingKeywords": [],
              "dimensions": {
                "basic": {"score": %d, "jobSide": "学历", "studentSide": "本科", "gap": "无", "evidence": ["固定证据"]},
                "professionalSkill": {"score": %d, "jobSide": "技能", "studentSide": "Java", "gap": "无", "evidence": ["固定证据"]},
                "professionalQuality": {"score": %d, "jobSide": "沟通", "studentSide": "清晰", "gap": "无", "evidence": ["固定证据"]},
                "developmentPotential": {"score": %d, "jobSide": "学习", "studentSide": "主动", "gap": "无", "evidence": ["固定证据"]}
              }
            }
            """.formatted(FIXED_MATCH_SUMMARY, FIXED_MATCH_SCORE, FIXED_MATCH_SCORE, FIXED_MATCH_SCORE,
            FIXED_MATCH_SCORE);

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock(DateTimeUtil.getDefaultZoneId());
    }

    @Bean
    @Primary
    ChatModel testChatModel() {
        return new FixedChatModel();
    }

    public static void clearModelTransactionObservations() {
        FixedChatModel.TRANSACTION_ACTIVE.clear();
    }

    public static List<Boolean> modelTransactionObservations() {
        return List.copyOf(FixedChatModel.TRANSACTION_ACTIVE);
    }

    /**
     * 业务对话经 {@link UserLlmRouter} 取客户端，不使用全局 ChatModel。
     * 测试替身只替换取客户端的方法，模型目录与配置校验仍走生产实现。
     */
    @Bean
    @Primary
    UserLlmRouter testUserLlmRouter(UserLlmConfigMapper userLlmConfigMapper, UserApiKeyCipher userApiKeyCipher,
            ChatMemory chatMemory, ChatModel chatModel) {
        return new StubUserLlmRouter(userLlmConfigMapper, userApiKeyCipher, chatMemory, chatModel);
    }

    @Bean
    @Primary
    EmbeddingModel testEmbeddingModel() {
        return new FixedEmbeddingModel();
    }

    @Bean
    @Primary
    TestMailSender testMailSender() {
        return new TestMailSender();
    }

    @Bean
    TestObjectStorage testObjectStorage() {
        return new TestObjectStorage();
    }

    @Bean
    DatabaseCleaner databaseCleaner(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        return new DatabaseCleaner(jdbcTemplate, redisTemplate);
    }

    @Bean
    TestUsers testUsers(UserMapper userMapper) {
        return new TestUsers(userMapper);
    }

    @Bean
    @Primary
    ToggleJwtSessionStore jwtSessionStore(RedisJwtSessionStore redisJwtSessionStore) {
        return new ToggleJwtSessionStore(redisJwtSessionStore);
    }

    @Bean
    TestJwt testJwt(ToggleJwtSessionStore jwtSessionStore) {
        return new TestJwt(jwtSessionStore);
    }

    static final class FixedChatModel implements ChatModel {

        static final List<Boolean> TRANSACTION_ACTIVE = Collections.synchronizedList(new ArrayList<>());

        @Override
        public ChatResponse call(Prompt prompt) {
            markTransaction();
            StringBuilder text = new StringBuilder();
            if (prompt != null && prompt.getInstructions() != null) {
                for (Message message : prompt.getInstructions()) {
                    if (message.getText() != null) {
                        text.append(message.getText()).append('\n');
                    }
                }
            }
            if (text.indexOf("请输出 JSON") >= 0) {
                return response(FIXED_MATCH_JSON);
            }
            return response(FIXED_REPLY);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            markTransaction();
            return Flux.just(response("测试"), response("模型"), response("固定回复"));
        }

        private static void markTransaction() {
            TRANSACTION_ACTIVE.add(TransactionSynchronizationManager.isActualTransactionActive());
        }

        private static ChatResponse response(String text) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        }
    }

    static final class FixedEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            List<String> inputs = request.getInstructions();
            for (int i = 0; i < inputs.size(); i++) {
                embeddings.add(new Embedding(vector(), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector();
        }

        @Override
        public int dimensions() {
            return DIMENSION;
        }

        private static float[] vector() {
            float[] values = new float[DIMENSION];
            values[0] = 1.0F;
            return values;
        }
    }

    static final class StubUserLlmRouter extends UserLlmRouter {

        private final ChatClient plain;
        private final ChatClient withMemory;

        StubUserLlmRouter(UserLlmConfigMapper userLlmConfigMapper, UserApiKeyCipher userApiKeyCipher,
                ChatMemory chatMemory, ChatModel chatModel) {
            super(userLlmConfigMapper, userApiKeyCipher, chatMemory);
            this.plain = ChatClient.builder(chatModel).build();
            this.withMemory = ChatClient.builder(chatModel)
                    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .build();
        }

        @Override
        public ChatClient getChatClient(Long userId) {
            return plain;
        }

        @Override
        public ChatClient getChatClientWithMemory(Long userId) {
            return withMemory;
        }
    }
}
