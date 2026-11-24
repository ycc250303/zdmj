package com.zdmj.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.conversationService.dto.ConversationRequest;
import com.zdmj.knowledgeService.dto.KnowledgeRetrievalRequest;
import com.zdmj.matchService.dto.JobStudentMatchResponse;
import com.zdmj.resumeService.dto.ResumeImportParseResponse;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.userAuthService.dto.UserUpdateRequest;

class RecordDtoSerializationContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 请求集合_构造后修改来源_结构独立且保留空值和空列表语义() {
        var ids = new ArrayList<Long>();
        ids.add(7L);
        ids.add(null);
        var request = new KnowledgeRetrievalRequest("查询", ids, false);
        ids.clear();
        assertThat(request.ragDocumentIds()).containsExactly(7L, null);
        assertThatThrownBy(() -> request.ragDocumentIds().add(8L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new KnowledgeRetrievalRequest("查询", null, false).ragDocumentIds()).isNull();
        assertThat(new KnowledgeRetrievalRequest("查询", List.of(), false).ragDocumentIds()).isEmpty();

        var config = new LinkedHashMap<String, Object>();
        config.put("optional", null);
        var conversation = new ConversationRequest(config);
        config.put("later", true);
        assertThat(conversation.config()).containsEntry("optional", null).doesNotContainKey("later");
        assertThat(mapper.valueToTree(conversation).toString()).isEqualTo("{\"config\":{\"optional\":null}}");
        assertThatThrownBy(() -> conversation.config().put("later", true))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 模型结构化输出_字段别名和未知字段_仍可解析内部记录() throws Exception {
        var parsed = mapper.readValue("""
                {"personalInfo":{"name":"张同学","website":"https://example.com",
                  "targetCity":"上海","extra":"ignored"}}
                """, ResumeImportParseResponse.class);
        assertThat(parsed.getPersonalInfo().homepageUrl()).isEqualTo("https://example.com");
        assertThat(parsed.getPersonalInfo().preferredWorkCity()).isEqualTo("上海");
        assertThat(parsed.getEducations()).isEmpty();

        var profile = mapper.readValue("""
                {"scoreDetail":{"jobMatchTechDepthScore":32,"projectPracticeScore":16,
                  "structureExpressionScore":12,"professionalPotentialScore":8,"extra":true},
                 "suggestions":[{"category":"技能缺失","issue":"补充实践","unknown":1}]}
                """, StudentCapabilityProfileResponse.class);
        assertThat(profile.getScoreDetail().projectExperienceScore()).isEqualTo(32);
        assertThat(profile.getScoreDetail().skillMatchScore()).isEqualTo(16);
        assertThat(profile.getScoreDetail().structureClarityScore()).isEqualTo(12);
        assertThat(profile.getScoreDetail().expressionProfessionalismScore()).isEqualTo(8);
        assertThat(profile.getSuggestions().getFirst().issue()).isEqualTo("补充实践");
    }

    @Test
    void 匹配模型输出_缺少后端字段_允许解析并保留维度对象的处理能力() throws Exception {
        var result = mapper.readValue("""
                {"targetRoleType":"java-backend","dimensions":{"basic":{"score":70}},"extra":true}
                """, JobStudentMatchResponse.class);
        assertThat(result.id()).isNull();
        assertThat(result.jobId()).isNull();
        assertThat(result.overallScore()).isNull();
        assertThat(result.dimensions().get("basic").getScore()).isEqualTo(70);
        result.dimensions().get("basic").setScore(80);
        assertThat(result.dimensions().get("basic").getScore()).isEqualTo(80);
    }

    @Test
    void 用户更新_旧字段别名_读取后以规范字段写出() throws Exception {
        var request = mapper.readValue("{\"homepage\":\"https://example.com\"}", UserUpdateRequest.class);
        var json = mapper.readTree(mapper.writeValueAsString(request));
        assertThat(request.website()).isEqualTo("https://example.com");
        assertThat(json.get("website").asText()).isEqualTo("https://example.com");
        assertThat(json.has("homepage")).isFalse();
    }
}
