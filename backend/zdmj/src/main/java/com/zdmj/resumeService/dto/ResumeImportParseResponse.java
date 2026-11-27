package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 简历导入结构化识别结果（字段对应 educations / careers / project_experiences / skills / users 表，不含 id）。
 *
 * <p>模型解析完成后会规范化经历、日期与列表内容，因此外层及经历条目保留可变字段。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeImportParseResponse {

    /** 个人基本信息，对应 users 可导入字段，允许缺省。 */
    private ResumeImportPersonalInfo personalInfo;

    /** 教育经历，缺省为空列表，显式 null 由导入业务规范化。 */
    private List<ResumeImportEducationItem> educations = new ArrayList<>();

    /** 工作与实习经历，缺省为空列表，显式 null 由导入业务规范化。 */
    private List<ResumeImportCareerItem> careers = new ArrayList<>();

    /** 项目经历，缺省为空列表，显式 null 由导入业务规范化。 */
    private List<ResumeImportProjectItem> projects = new ArrayList<>();

    /** 奖项经历，接受 honors、honorsAndAwards、honors_and_awards 别名。 */
    @JsonAlias({ "honors", "honorsAndAwards", "honors_and_awards" })
    private List<ResumeImportAwardItem> awards = new ArrayList<>();

    /** 技能条目，缺省为空列表，显式 null 由导入业务规范化。 */
    private List<ResumeImportSkillItem> skills = new ArrayList<>();

    /** 模型识别警告，缺省为空列表。 */
    private List<String> warnings = new ArrayList<>();
}
