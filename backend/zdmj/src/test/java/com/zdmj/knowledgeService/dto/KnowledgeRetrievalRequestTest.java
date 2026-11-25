package com.zdmj.knowledgeService.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Arrays;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KnowledgeRetrievalRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @ParameterizedTest
    @ValueSource(strings = {"[1.9]", "[1.0]", "[1e0]", "[\"2\"]", "[-1]", "[0]", "[null]",
            "[9223372036854775808]", "[true]", "{}", "1", "\"1\""})
    void 非法编号拒绝解析(String ids) {
        assertThatThrownBy(() -> mapper.readValue("{\"query\":\"问题\",\"ragDocumentIds\":" + ids + "}",
                KnowledgeRetrievalRequest.class)).hasMessageContaining("ragDocumentIds");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"true\"", "\"false\"", "0", "1", "[]", "{}"})
    void 非布尔开关拒绝解析(String flag) {
        assertThatThrownBy(() -> mapper.readValue("{\"query\":\"问题\",\"useSystemKnowledge\":" + flag + "}",
                KnowledgeRetrievalRequest.class)).hasMessageContaining("useSystemKnowledge");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "true", "[]", "{}", "null", "\" \""})
    void 非法问题拒绝解析(String query) {
        assertThatThrownBy(() -> mapper.readValue("{\"query\":" + query + "}", KnowledgeRetrievalRequest.class))
                .hasMessageContaining("query");
    }

    @Test
    void 合法字段保留范围顺序重复值与缺省语义() throws Exception {
        var request = mapper.readValue("{\"query\":\"问题\",\"ragDocumentIds\":[2,1,2,9223372036854775807],\"useSystemKnowledge\":true}",
                KnowledgeRetrievalRequest.class);
        assertThat(request.ragDocumentIds()).containsExactly(2L, 1L, 2L, Long.MAX_VALUE);
        assertThat(request.useSystemKnowledge()).isTrue();
        for (String json : List.of("{\"query\":\"问题\"}",
                "{\"query\":\"问题\",\"ragDocumentIds\":null,\"useSystemKnowledge\":null}")) {
            var defaults = mapper.readValue(json, KnowledgeRetrievalRequest.class);
            assertThat(defaults.ragDocumentIds()).isNull();
            assertThat(defaults.useSystemKnowledge()).isFalse();
        }
        assertThat(mapper.readValue("{\"query\":\"问题\",\"ragDocumentIds\":[]}", KnowledgeRetrievalRequest.class)
                .ragDocumentIds()).isEmpty();
        assertThat(mapper.readValue(mapper.writeValueAsString(request), KnowledgeRetrievalRequest.class)).isEqualTo(request);
    }

    @Test
    void 构造时验证问题长度和编号约束() {
        assertThat(new KnowledgeRetrievalRequest("问".repeat(4000), List.of(1L), false).query()).hasSize(4000);
        assertThatThrownBy(() -> new KnowledgeRetrievalRequest("问".repeat(4001), null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KnowledgeRetrievalRequest("问题", Arrays.asList(1L, null), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KnowledgeRetrievalRequest("问题", List.of(-1L), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
