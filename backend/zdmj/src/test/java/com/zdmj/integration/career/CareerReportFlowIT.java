package com.zdmj.integration.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.zdmj.aiService.api.ModelGateway;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.knowledgeService.api.KnowledgeRagService;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.zdmj.testsupport.ApiClient;
import com.zdmj.testsupport.ExternalAiStubConfig;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestJwt;
import com.zdmj.testsupport.TestUsers;
import com.zdmj.userAuthService.entity.User;

/**
 * 职业发展报告的生成、润色、手工保存、完整性检查，以及覆盖失败和并发。
 */
@AutoConfigureMockMvc
class CareerReportFlowIT extends IntegrationTestBase {

    @MockitoSpyBean(name = "testModelGateway")
    private ModelGateway modelGateway;

    @Autowired
    private KnowledgeRagService knowledge;

    @MockitoSpyBean
    private KnowledgeVectorMapper vectors;

    @Autowired
    private org.mybatis.spring.SqlSessionTemplate sqlSession;

    private static final String MANUAL_BODY = """
            {
              "reportContent": {
                "careerExploration": "手工探索",
                "careerGoals": ["手工目标"],
                "careerPath": ["手工路径"],
                "actionPlan": {
                  "shortTerm": [{"task": "补齐技能", "cycle": "2周", "deliverable": "清单"}],
                  "midTerm": [{"task": "投递", "cycle": "4周", "deliverable": "记录"}]
                },
                "evaluationPlan": {"cycle": "双周"},
                "evidence": ["手工证据"]
              }
            }
            """;

    private final ApiClient api;
    private final TestUsers testUsers;
    private final TestJwt testJwt;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    CareerReportFlowIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, MockMvc mockMvc, TestUsers testUsers,
            TestJwt testJwt, JdbcTemplate jdbcTemplate) {
        super(databaseCleaner);
        this.api = new ApiClient(mockMvc);
        this.testUsers = testUsers;
        this.testJwt = testJwt;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 生成润色手工保存与完整性检查_始终只有一行且模型调用不在事务中() throws Exception {
        User owner = testUsers.createPair().userA();
        String token = testJwt.authorize(owner);
        long jobId = createJob(token);
        seedInputs(owner.getId(), jobId);

        String generated = ApiClient.requireOk(api.post(token, "/career-reports/jobs/" + jobId, "{}"));
        long reportId = JsonPath.<Number>read(generated, "$.data.id").longValue();
        assertThat(reportData(generated)).doesNotContainKeys("version", "latest");
        assertThat(JsonPath.<String>read(generated, "$.data.promptName")).isEqualTo("career-report/generate");
        assertThat(JsonPath.<String>read(generated, "$.data.reportContent.careerGoals[0]")).isEqualTo("生成目标");
        assertReport(reportId, owner.getId(), jobId, "career-report/generate", "生成目标", 100, 2);
        assertThat(countReports(owner.getId(), jobId)).isEqualTo(1);

        String polished = ApiClient.requireOk(api.post(token, "/career-reports/" + reportId + "/polish", "{}"));
        assertThat(JsonPath.<Number>read(polished, "$.data.id").longValue()).isEqualTo(reportId);
        assertThat(JsonPath.<String>read(polished, "$.data.reportContent.careerGoals[0]")).isEqualTo("润色目标");
        assertThat(JsonPath.<String>read(polished, "$.data.promptName")).isEqualTo("career-report/polish");
        assertReport(reportId, owner.getId(), jobId, "career-report/polish", "润色目标", 100, 2);
        assertThat(countReports(owner.getId(), jobId)).isEqualTo(1);

        String saved = ApiClient.requireOk(api.put(token, "/career-reports/" + reportId, MANUAL_BODY));
        assertThat(JsonPath.<Number>read(saved, "$.data.id").longValue()).isEqualTo(reportId);
        assertThat(JsonPath.<String>read(saved, "$.data.reportContent.careerGoals[0]")).isEqualTo("手工目标");
        assertThat(JsonPath.<String>read(saved, "$.data.promptName")).isEqualTo("career-report/polish");
        assertReport(reportId, owner.getId(), jobId, "career-report/polish", "手工目标", 100, 2);
        assertThat(countReports(owner.getId(), jobId)).isEqualTo(1);

        String checked = ApiClient.requireOk(api.post(token, "/career-reports/" + reportId + "/integrity-check", "{}"));
        assertThat(JsonPath.<Boolean>read(checked, "$.data.passed")).isTrue();
        assertThat(JsonPath.<Integer>read(checked, "$.data.completenessScore")).isEqualTo(90);
        assertReport(reportId, owner.getId(), jobId, "career-report/polish", "手工目标", 90, 2);
        assertThat(countReports(owner.getId(), jobId)).isEqualTo(1);

        String current = ApiClient.requireOk(api.get(token, "/career-reports/jobs/" + jobId));
        assertThat(JsonPath.<Number>read(current, "$.data.id").longValue()).isEqualTo(reportId);
        assertThat(JsonPath.<Integer>read(current, "$.data.completenessScore")).isEqualTo(90);
        assertThat(ExternalAiStubConfig.modelTransactionObservations()).isNotEmpty().containsOnly(false);
    }

    @Test
    void 覆盖写入失败_原正文仍在() throws Exception {
        User owner = testUsers.createPair().userA();
        String token = testJwt.authorize(owner);
        long jobId = createJob(token);
        long reportId = seedReport(owner.getId(), jobId, "旧正文");
        jdbcTemplate.execute("""
                ALTER TABLE career_development_reports
                ADD CONSTRAINT career_reports_reject_overwrite
                CHECK (report_content::text NOT LIKE '%手工目标%')
                """);
        try {
            ApiClient.assertProblem(api.put(token, "/career-reports/" + reportId, MANUAL_BODY), 500, 1008);
            assertThat(countReports(owner.getId(), jobId)).isEqualTo(1);
            assertReport(reportId, owner.getId(), jobId, "career-report/generate", "旧正文", 40, 1);
        } finally {
            jdbcTemplate.execute(
                    "ALTER TABLE career_development_reports DROP CONSTRAINT IF EXISTS career_reports_reject_overwrite");
        }
    }

    @Test
    void 润色返回空结果_接口返回业务错误且原报告不变() throws Exception {
        User owner = testUsers.createPair().userA();
        String token = testJwt.authorize(owner);
        long jobId = createJob(token);
        long reportId = seedReport(owner.getId(), jobId, "旧正文");
        doReturn(null).when(modelGateway).generateStructured(any(), any(), any());

        ApiClient.assertProblem(api.post(token, "/career-reports/" + reportId + "/polish", "{}"), 400, 12004);

        assertReport(reportId, owner.getId(), jobId, "career-report/generate", "旧正文", 40, 1);
        assertThat(countReports(owner.getId(), jobId)).isEqualTo(1);
    }

    @Test
    void 完整性检查失败_接口返回错误且不更新质量状态() throws Exception {
        User owner = testUsers.createPair().userA();
        String token = testJwt.authorize(owner);
        long jobId = createJob(token);
        long reportId = seedReport(owner.getId(), jobId, "旧正文");
        String flags = jdbcTemplate.queryForObject(
                "SELECT quality_flags::text FROM career_development_reports WHERE id = ?", String.class, reportId);
        doThrow(new IllegalStateException("模型检查失败"))
                .when(modelGateway).generateStructured(any(), any(), any());

        ApiClient.assertProblem(api.post(token, "/career-reports/" + reportId + "/integrity-check", "{}"), 500, 1008);

        assertReport(reportId, owner.getId(), jobId, "career-report/generate", "旧正文", 40, 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT quality_flags::text FROM career_development_reports WHERE id = ?", String.class, reportId))
                .isEqualTo(flags);
    }

    @Test
    void 完整性检查返回空结果_接口返回业务错误且原报告不变() throws Exception {
        User owner = testUsers.createPair().userA();
        String token = testJwt.authorize(owner);
        long jobId = createJob(token);
        long reportId = seedReport(owner.getId(), jobId, "旧正文");
        doReturn(null).when(modelGateway).generateStructured(any(), any(), any());

        ApiClient.assertProblem(api.post(token, "/career-reports/" + reportId + "/integrity-check", "{}"), 400, 12004);

        assertReport(reportId, owner.getId(), jobId, "career-report/generate", "旧正文", 40, 1);
    }

    @Test
    void 并发保存_结束后仍只有一行() throws Exception {
        User owner = testUsers.createPair().userA();
        String token = testJwt.authorize(owner);
        long jobId = createJob(token);
        long reportId = seedReport(owner.getId(), jobId, "并发基线");
        int writers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch ready = new CountDownLatch(writers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Long>> savedIds = new ArrayList<>();
            for (int i = 0; i < writers; i++) {
                int index = i;
                savedIds.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    String body = MANUAL_BODY.replace("手工目标", "并发目标" + index);
                    String response = ApiClient.requireOk(api.put(token, "/career-reports/" + reportId, body));
                    return JsonPath.<Number>read(response, "$.data.id").longValue();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Long> savedId : savedIds) {
                assertThat(savedId.get(30, TimeUnit.SECONDS)).isEqualTo(reportId);
            }
            assertThat(countReports(owner.getId(), jobId)).isEqualTo(1);
            String stored = jdbcTemplate.queryForObject("""
                    SELECT report_content::text FROM career_development_reports
                    WHERE user_id = ? AND job_id = ?
                    """, String.class, owner.getId(), jobId);
            assertThat(stored).contains("并发目标");
            assertThat(stored).doesNotContain("并发基线");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 新报告与历史来源均按授权范围返回白名单且提示词不含文件标识() throws Exception {
        TestUsers.Pair users = testUsers.createPair();
        User owner = users.userA();
        String token = testJwt.authorize(owner);
        long jobId = createJob(token);
        seedInputs(owner.getId(), jobId);
        long privateBase = jdbcTemplate.queryForObject("SELECT id FROM knowledge_bases WHERE user_id=? AND scope=1", Long.class, owner.getId());
        long otherBase = createBase(users.userB().getId(), 1);
        long learningBase = createBase(0L, 3);
        long systemBase = createBase(0L, 2);
        long own = seedKnowledge(privateBase, owner.getId(), "本人标题", "本人片段", "learning_path");
        long learning = seedKnowledge(learningBase, 0L, "学习路线标题", "路线片段", "learning_path");
        long other = seedKnowledge(otherBase, users.userB().getId(), "他人秘密标题", "他人秘密片段", "learning_path");
        long system = seedKnowledge(systemBase, 0L, "系统通用标题", "系统通用片段", "learning_path");
        var captured = new java.util.concurrent.atomic.AtomicReference<String>();
        org.mockito.Mockito.doAnswer(invocation -> {
            var request = invocation.getArgument(1, com.zdmj.aiService.api.ModelRequest.class);
            captured.set(request.message());
            return invocation.callRealMethod();
        }).when(modelGateway).generateStructured(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        String generated = ApiClient.requireOk(api.post(token, "/career-reports/jobs/" + jobId, "{}"));
        long reportId = JsonPath.<Number>read(generated, "$.data.id").longValue();
        assertThat(JsonPath.<List<Number>>read(generated, "$.data.knowledgeSources[*].documentId"))
                .extracting(Number::longValue).containsExactlyInAnyOrder(own, learning);
        assertThat(generated).doesNotContain("sourceFile", "pendingKey", "retiredFile", "user-", "他人秘密", "系统通用片段");
        assertThat(captured.get()).contains("本人片段", "路线片段")
                .doesNotContain("sourceFile", "pendingKey", "retiredFile", "user-", "他人秘密", "系统通用片段");
        String stored = jdbcTemplate.queryForObject("SELECT knowledge_sources::text FROM career_development_reports WHERE id=?", String.class, reportId);
        assertThat(stored).doesNotContain("sourceFile", "pendingKey", "retiredFile", "downloadPath", "user-");

        var snapshots = new java.util.ArrayList<Map<String, Object>>();
        for (long id : List.of(own, learning, other, system, 999999L)) {
            snapshots.add(Map.of("documentId", id, "chunkIndex", 0, "score", 0.8,
                    "title", "user-" + owner.getId() + "/secret.md", "snippet", "历史片段",
                    "metadata", Map.of("sourceFile", "user-1/secret.md", "pendingKey", "秘密", "retiredFile", "秘密")));
        }
        String legacy = new ObjectMapper().writeValueAsString(snapshots);
        jdbcTemplate.update("UPDATE career_development_reports SET knowledge_sources=?::jsonb WHERE id=?", legacy, reportId);
        for (String response : List.of(
                ApiClient.requireOk(api.get(token, "/career-reports/jobs/" + jobId)),
                ApiClient.requireOk(api.post(token, "/career-reports/" + reportId + "/polish", "{}")),
                ApiClient.requireOk(api.put(token, "/career-reports/" + reportId, MANUAL_BODY)))) {
            assertThat(JsonPath.<List<Number>>read(response, "$.data.knowledgeSources[*].documentId"))
                    .extracting(Number::longValue).containsExactly(own, learning);
            assertThat(JsonPath.<List<String>>read(response, "$.data.knowledgeSources[*].title"))
                    .containsExactly("本人标题", "学习路线标题");
            assertThat(response).doesNotContain("sourceFile", "pendingKey", "retiredFile", "user-", "他人秘密");
            assertThat(JsonPath.<List<String>>read(response, "$.data.knowledgeSources[*].snippet"))
                    .containsExactly("历史片段", "历史片段");
        }
        ApiClient.assertProblem(api.put(testJwt.authorize(users.userB()), "/career-reports/" + reportId, MANUAL_BODY), 404, 12001);
        jdbcTemplate.update("DELETE FROM knowledge_documents WHERE id=?", own);
        String deleted = ApiClient.requireOk(api.get(token, "/career-reports/jobs/" + jobId));
        assertThat(JsonPath.<List<Number>>read(deleted, "$.data.knowledgeSources[*].documentId"))
                .extracting(Number::longValue).containsExactly(learning);
    }

    @Test
    void 检索快照与并发替换互不混用且错误关联向量无法被检索() throws Exception {
        User owner = testUsers.createPair().userA();
        String token = testJwt.authorize(owner);
        long base = createBase(owner.getId(), 1);
        long document = seedKnowledge(base, owner.getId(), "原标题", "原片段", "learning_path");
        org.mockito.Mockito.doAnswer(invocation -> {
            Object result = sqlSession.getMapper(KnowledgeVectorMapper.class).searchBySimilarity(
                    invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3));
            // 查询返回后由另一数据库连接提交替换登记，返回值必须仍为原查询快照。
            jdbcTemplate.update("UPDATE knowledge_documents SET file_status=0,title='新标题',metadata=metadata || '{\"pendingKey\":\"user-1/new.md\"}'::jsonb WHERE id=?", document);
            return result;
        }).when(vectors).searchBySimilarity(org.mockito.ArgumentMatchers.eq(owner.getId()), org.mockito.ArgumentMatchers.eq(base), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        var hits = knowledge.retrieveLearningPaths(new CurrentActor(owner.getId()), "学习需求");
        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().title()).isEqualTo("原标题");
        assertThat(hits.getFirst().content()).isEqualTo("原片段");
        var references = knowledge.resolveReportReferences(new CurrentActor(owner.getId()), List.of(hits.getFirst().reference()));
        assertThat(references.getFirst().title()).isEqualTo("原标题");
        assertThat(references.getFirst().downloadPath()).isNull();
        String closed = ApiClient.requireOk(api.post(token, "/knowledge/retrievals", "{\"query\":\"学习\",\"useSystemKnowledge\":false}"));
        assertThat(JsonPath.<List<?>>read(closed, "$.data.hits")).isEmpty();
        org.mockito.Mockito.reset(vectors);
        jdbcTemplate.update("UPDATE knowledge_documents SET file_status=1 WHERE id=?", document);
        long wrongBase = createBase(0L, 3);
        jdbcTemplate.update("UPDATE knowledge_vectors SET knowledge_id=? WHERE document_id=?", wrongBase, document);
        assertThat(knowledge.retrieveLearningPaths(new CurrentActor(owner.getId()), "学习需求")).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "{\"documentId\":1.9}", "{\"documentId\":\"1\"}", "{\"documentId\":-1}",
            "{\"documentId\":9223372036854775808}", "{\"documentId\":1,\"score\":\"0.8\"}",
            "{\"documentId\":1,\"chunkIndex\":0.1}", "{\"documentId\":1,\"title\":{}}", "null"})
    void 已存来源结构非法时返回系统异常(String source) throws Exception {
        User owner = testUsers.createPair().userA();
        String token = testJwt.authorize(owner);
        long job = createJob(token);
        long report = seedReport(owner.getId(), job, "目标");
        jdbcTemplate.update("UPDATE career_development_reports SET knowledge_sources=?::jsonb WHERE id=?", "[" + source + "]", report);
        ApiClient.assertProblem(api.get(token, "/career-reports/jobs/" + job), 500, 1008);
    }

    private long createBase(Long userId, int scope) {
        return jdbcTemplate.queryForObject("INSERT INTO knowledge_bases(user_id,scope) VALUES (?,?) RETURNING id", Long.class, userId, scope);
    }

    private long seedKnowledge(long base, Long owner, String title, String content, String category) {
        long id = jdbcTemplate.queryForObject("""
                INSERT INTO knowledge_documents(knowledge_id,user_id,type,content,title,file_status,embedding_status,metadata)
                VALUES (?,?,1,?, ?,1,3,jsonb_build_object('docCategory',?::text,'sourceFile',?::text,'pendingKey','secret','retiredFile','secret')) RETURNING id
                """, Long.class, base, owner, "user-" + owner + "/private.md", title, category, "user-" + owner + "/private.md");
        String vector = "[1," + String.join(",", java.util.Collections.nCopies(1023, "0")) + "]";
        jdbcTemplate.update("""
                INSERT INTO knowledge_vectors(knowledge_id,document_id,user_id,embedding,content,chunk_index,metadata)
                VALUES (?,?,?,?::vector,?,0,'{"sourceFile":"internal.md","pendingKey":"secret"}'::jsonb)
                """, base, id, owner, vector, content);
        return id;
    }

    private long createJob(String token) throws Exception {
        return ApiClient.dataId(api.post(token, "/jobs", """
                {"jobName":"后端工程师","companyName":"测试企业","description":"负责应用开发",
                 "location":"上海","salaryType":2,"salaryMin":10000,"salaryMax":20000}
                """));
    }

    private void seedInputs(Long userId, long jobId) {
        jdbcTemplate.update("""
                INSERT INTO student_capability_profiles (user_id, professional_skills, target_role_type, prompt_name)
                VALUES (?, 'Java', 'java-backend', 'resume-analysis/java-backend')
                """, userId);
        jdbcTemplate.update("""
                INSERT INTO job_student_matches (
                    user_id, job_id, overall_score, summary, target_role_type, prompt_name)
                VALUES (?, ?, 80, '固定匹配摘要', 'java-backend', 'job-student-match/java-backend')
                """, userId, jobId);
        jdbcTemplate.update("INSERT INTO knowledge_bases (user_id, scope) VALUES (?, 1)", userId);
    }

    private long seedReport(Long userId, long jobId, String goal) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO career_development_reports (
                    user_id, job_id, report_content, status, completeness_score, prompt_name)
                VALUES (?, ?, ?::jsonb, 1, 40, 'career-report/generate')
                RETURNING id
                """, Long.class, userId, jobId,
                "{\"careerExploration\":\"" + goal + "\",\"careerGoals\":[\"" + goal + "\"]}");
    }

    private void assertReport(long reportId, Long userId, long jobId, String promptName, String goal, int score,
            int status) {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT user_id FROM career_development_reports WHERE id = ?
                """, Long.class, reportId)).isEqualTo(userId);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT job_id FROM career_development_reports WHERE id = ?
                """, Long.class, reportId)).isEqualTo(jobId);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT prompt_name FROM career_development_reports WHERE id = ?
                """, String.class, reportId)).isEqualTo(promptName);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT report_content::text FROM career_development_reports WHERE id = ?
                """, String.class, reportId)).contains(goal);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT completeness_score FROM career_development_reports WHERE id = ?
                """, Integer.class, reportId)).isEqualTo(score);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM career_development_reports WHERE id = ?
                """, Integer.class, reportId)).isEqualTo(status);
    }

    private int countReports(Long userId, long jobId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM career_development_reports WHERE user_id = ? AND job_id = ?
                """, Integer.class, userId, jobId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> reportData(String body) {
        return JsonPath.read(body, "$.data");
    }
}
