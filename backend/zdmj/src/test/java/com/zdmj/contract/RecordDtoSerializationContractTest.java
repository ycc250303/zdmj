package com.zdmj.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.conversationService.dto.ConversationRequest;
import com.zdmj.aiService.dto.ModelOption;
import com.zdmj.aiService.dto.UserLlmConfigResponse;
import com.zdmj.jobService.dto.JobRoleModelResponse;
import com.zdmj.knowledgeService.api.KnowledgeHit;
import com.zdmj.knowledgeService.dto.KnowledgeRetrievalRequest;
import com.zdmj.matchService.dto.JobStudentMatchResponse;
import com.zdmj.matchService.dto.KeywordMatchResult;
import com.zdmj.resumeService.dto.ResumeImportParseResponse;
import com.zdmj.resumeService.dto.StudentCapabilityProfileResponse;
import com.zdmj.userAuthService.dto.UserUpdateRequest;

class RecordDtoSerializationContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 请求集合_构造后修改来源_结构独立且保留空值和空列表语义() {
        var ids = new ArrayList<Long>();
        ids.add(7L);
        ids.add(7L);
        var request = new KnowledgeRetrievalRequest("查询", ids, false);
        ids.clear();
        assertThat(request.ragDocumentIds()).containsExactly(7L, 7L);
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
    void 模型结构化输出_字段别名和未知字段_保留子结构与集合语义() throws Exception {
        var parsed = mapper.readValue("""
                {"personalInfo":{"name":"张同学","website":"https://example.com",
                  "targetCity":"上海","extra":"ignored"},
                 "educations":[{"school":"某大学","degree":3,"extra":true}],
                 "careers":[{"company":"某公司","position":"开发工程师"}],
                 "projects":[{"name":"求职系统","techStack":["Java",null,"Java"],
                   "highlights":["实现知识检索"]}],
                 "honors":[{"awardName":"优秀学生","award_date":"2026-06"}],
                 "skills":[{"name":"Java","description":"熟悉并发编程"}]}
                """, ResumeImportParseResponse.class);
        assertThat(parsed.getPersonalInfo().name()).isEqualTo("张同学");
        assertThat(parsed.getPersonalInfo().homepageUrl()).isEqualTo("https://example.com");
        assertThat(parsed.getPersonalInfo().preferredWorkCity()).isEqualTo("上海");
        assertThat(parsed.getEducations().getFirst().getSchool()).isEqualTo("某大学");
        assertThat(parsed.getEducations().getFirst().getDegree()).isEqualTo(3);
        assertThat(parsed.getCareers().getFirst().getCompany()).isEqualTo("某公司");
        assertThat(parsed.getProjects().getFirst().getTechStack()).containsExactly("Java", null, "Java");
        assertThat(parsed.getProjects().getFirst().getHighlights()).containsExactly("实现知识检索");
        assertThat(parsed.getAwards().getFirst().getName()).isEqualTo("优秀学生");
        assertThat(parsed.getAwards().getFirst().getAwardDate()).isEqualTo("2026-06");
        assertThat(parsed.getSkills().getFirst().getDescription()).isEqualTo("熟悉并发编程");
        var json = mapper.valueToTree(parsed);
        assertThat(json.at("/personalInfo/homepageUrl").asText()).isEqualTo("https://example.com");
        assertThat(json.at("/awards/0/name").asText()).isEqualTo("优秀学生");
        assertThat(json.has("honors")).isFalse();
        assertThat(mapper.readValue("{}", ResumeImportParseResponse.class).getEducations()).isEmpty();
        assertThat(mapper.readValue("{\"educations\":null}", ResumeImportParseResponse.class)
                .getEducations()).isNull();

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
        assertThat(mapper.valueToTree(profile).at("/scoreDetail/projectExperienceScore").asInt()).isEqualTo(32);
        assertThat(mapper.valueToTree(profile).at("/suggestions/0/issue").asText()).isEqualTo("补充实践");
    }

    @Test
    void 关键词匹配结果_集合复制_保留顺序重复与空值并禁止结构修改() {
        var matched = new ArrayList<>(Arrays.asList("Java", null, "Java"));
        var missing = new ArrayList<>(List.of("Redis"));
        var result = new KeywordMatchResult(matched, missing, new BigDecimal("0.75"));
        matched.clear();
        missing.clear();
        assertThat(result.matched()).containsExactly("Java", null, "Java");
        assertThat(result.missing()).containsExactly("Redis");
        assertThatThrownBy(() -> result.matched().add("SQL")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.missing().clear()).isInstanceOf(UnsupportedOperationException.class);
        var absent = new KeywordMatchResult(null, null, null);
        assertThat(absent.matched()).isNull();
        assertThat(absent.missing()).isNull();
        assertThat(new KeywordMatchResult(List.of(), List.of(), BigDecimal.ZERO).matched()).isEmpty();
    }

    @Test
    void 公开响应子结构_保留来源字段名称与模型目录层次() {
        var hit = new KnowledgeHit(1L, 7L, 0, "正文", 0.8, "学习笔记", "技术", "/download/7");
        var hitJson = mapper.valueToTree(hit);
        assertThat(hitJson.get("metadata")).isEqualTo(mapper.valueToTree(Map.of(
                "DocumentId", "7", "source", "/download/7",
                "documentTitle", "学习笔记", "docCategory", "技术")));
        var referenceJson = mapper.valueToTree(hit.reference());
        assertThat(referenceJson.at("/metadata/documentTitle").asText()).isEqualTo("学习笔记");
        assertThat(referenceJson.at("/metadata/docCategory").asText()).isEqualTo("技术");
        assertThat(referenceJson.get("metadata").size()).isEqualTo(2);
        assertThat(referenceJson.has("downloadPath")).isFalse();

        var config = new UserLlmConfigResponse(false, null, null, null,
                List.of(new ModelOption("deepseek-flash", "DeepSeek")));
        var configJson = mapper.valueToTree(config);
        assertThat(configJson.at("/models/0/code").asText()).isEqualTo("deepseek-flash");
        assertThat(configJson.at("/models/0/displayName").asText()).isEqualTo("DeepSeek");
    }

    @Test
    void 岗位方向模型输出_保留字段与缺省值_交由业务校验() throws Exception {
        var result = mapper.readValue("""
                {"roleCode":"java-backend","confidence":0.8,"reason":"岗位描述"}
                """, JobRoleModelResponse.class);
        assertThat(result.roleCode()).isEqualTo("java-backend");
        assertThat(result.confidence()).isEqualTo(0.8);
        assertThat(result.reason()).isEqualTo("岗位描述");
        assertThat(mapper.readValue("{}", JobRoleModelResponse.class))
                .isEqualTo(new JobRoleModelResponse(null, null, null));
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
