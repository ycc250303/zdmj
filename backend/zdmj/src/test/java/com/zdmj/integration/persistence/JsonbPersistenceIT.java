package com.zdmj.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.conversationService.mapper.ConversationMapper;
import com.zdmj.jobService.entity.Company;
import com.zdmj.jobService.entity.Job;
import com.zdmj.jobService.mapper.CompanyMapper;
import com.zdmj.jobService.mapper.JobMapper;
import com.zdmj.resumeService.entity.Resume;
import com.zdmj.resumeService.entity.Skill;
import com.zdmj.resumeService.mapper.ResumeMapper;
import com.zdmj.resumeService.mapper.SkillMapper;
import com.zdmj.resumeService.service.SkillService;
import com.zdmj.testsupport.IntegrationTestBase;
import com.zdmj.testsupport.TestUsers;
import com.zdmj.userAuthService.entity.User;

class JsonbPersistenceIT extends IntegrationTestBase {

    private final TestUsers testUsers;
    private final JdbcTemplate jdbcTemplate;
    private final JobMapper jobMapper;
    private final CompanyMapper companyMapper;
    private final ResumeMapper resumeMapper;
    private final SkillMapper skillMapper;
    private final ConversationMapper conversationMapper;
    private final SkillService skillService;

    @Autowired
    JsonbPersistenceIT(com.zdmj.testsupport.DatabaseCleaner databaseCleaner, TestUsers testUsers,
            JdbcTemplate jdbcTemplate, JobMapper jobMapper, CompanyMapper companyMapper, ResumeMapper resumeMapper,
            SkillMapper skillMapper, ConversationMapper conversationMapper, SkillService skillService) {
        super(databaseCleaner);
        this.testUsers = testUsers;
        this.jdbcTemplate = jdbcTemplate;
        this.jobMapper = jobMapper;
        this.companyMapper = companyMapper;
        this.resumeMapper = resumeMapper;
        this.skillMapper = skillMapper;
        this.conversationMapper = conversationMapper;
        this.skillService = skillService;
    }

    @Test
    void JSONB往返_标量数组结构化对象与空值_类型和泛型保持不变() {
        User user = testUsers.createPair().userA();
        Company company = new Company();
        company.setName("星云科技");
        company.setIndustries(List.of("互联网", "企业服务"));
        companyMapper.insert(company);

        Job job = new Job();
        job.setJobName("后端工程师");
        job.setCompanyId(company.getId());
        job.setCompanyName(company.getName());
        job.setDescription("负责服务端开发");
        job.setLocation("上海");
        job.setSalaryMin(20000);
        job.setSalaryMax(30000);
        job.setSalaryType(2);
        job.setLink("");
        job.setKeywords(List.of("Java", "PostgreSQL"));
        job.setContent(List.of("设计接口"));
        job.setRequirements(List.of());
        jobMapper.insert(job);

        Job loadedJob = jobMapper.selectById(job.getId());
        assertThat(loadedJob.getKeywords()).containsExactly("Java", "PostgreSQL");
        assertThat(loadedJob.getKeywords().get(0)).isInstanceOf(String.class);
        assertThat(loadedJob.getContent()).containsExactly("设计接口");
        assertThat(loadedJob.getRequirements()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT jsonb_typeof(keywords) FROM jobs WHERE id = ?", String.class, job.getId())).isEqualTo("array");

        jdbcTemplate.update("UPDATE jobs SET keywords = NULL WHERE id = ?", job.getId());
        assertThat(jobMapper.selectById(job.getId()).getKeywords()).isNull();

        Company loadedCompany = companyMapper.selectById(company.getId());
        assertThat(loadedCompany.getIndustries()).containsExactly("互联网", "企业服务");

        Resume resume = new Resume();
        resume.setUserId(user.getId());
        resume.setProjects(List.of(3_000_000_000L, 2L));
        resume.setEducations(List.of());
        resumeMapper.insert(resume);
        Resume loadedResume = resumeMapper.selectById(resume.getId());
        assertThat(loadedResume.getProjects()).containsExactly(3_000_000_000L, 2L);
        assertThat(loadedResume.getProjects().get(0)).isInstanceOf(Long.class);
        assertThat(loadedResume.getEducations()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT projects::text FROM resumes WHERE id = ?", String.class, resume.getId()))
                .contains("3000000000");

        Skill skill = new Skill();
        skill.setUserId(user.getId());
        skill.setContent("[{\"type\":\"开发语言\",\"content\":[\"Java\"]}]");
        skillMapper.insert(skill);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT jsonb_typeof(content) FROM skills WHERE id = ?", String.class, skill.getId()))
                .isEqualTo("array");
        assertThat(skillService.toResponse(skillMapper.selectById(skill.getId())).getContent())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getType()).isEqualTo("开发语言");
                    assertThat(item.getContent()).containsExactly("Java");
                });

        Conversation conversation = new Conversation();
        conversation.setUserId(user.getId());
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("useSystemKnowledge", Boolean.FALSE);
        config.put("label", "固定配置");
        conversation.setConfig(config);
        conversation.setContext(List.of(Map.of("type", "knowledge_base", "name", "资料")));
        conversationMapper.insert(conversation);
        Conversation loadedConversation = conversationMapper.selectById(conversation.getId());
        assertThat(loadedConversation.getConfig()).containsEntry("label", "固定配置");
        assertThat(loadedConversation.getConfig().get("useSystemKnowledge")).isEqualTo(false);
        assertThat(loadedConversation.getContext()).hasSize(1);
        assertThat(loadedConversation.getContext().get(0)).containsEntry("name", "资料");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT jsonb_typeof(config) FROM conversations WHERE id = ?", String.class, conversation.getId()))
                .isEqualTo("object");
    }

    @Test
    void JSONB写入_非法文本对象和错误数组_数据库拒绝或读取为空列表() {
        User user = testUsers.createPair().userA();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO skills (user_id, content) VALUES (?, CAST(? AS jsonb))", user.getId(), "{"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO skills (user_id, content) VALUES (?, CAST(? AS jsonb))",
                user.getId(), "{\"type\":\"x\"}"))
                .isInstanceOf(DataAccessException.class);

        jdbcTemplate.update(
                "INSERT INTO skills (user_id, content) VALUES (?, CAST(? AS jsonb))",
                user.getId(), "[1,2]");
        Long skillId = jdbcTemplate.queryForObject(
                "SELECT id FROM skills WHERE user_id = ?", Long.class, user.getId());
        assertThat(skillService.toResponse(skillMapper.selectById(skillId)).getContent()).isEmpty();
    }
}
