package com.zdmj.conversationService.support;

import com.zdmj.conversationService.dto.MessageDTO;
import com.zdmj.conversationService.entity.Conversation;
import com.zdmj.resumeService.dto.AwardRequest;
import com.zdmj.resumeService.dto.CareerRequest;
import com.zdmj.resumeService.dto.EducationRequest;
import com.zdmj.resumeService.dto.ProjectExperienceRequest;
import com.zdmj.resumeService.dto.ResumeContentResponse;
import com.zdmj.resumeService.dto.ResumePersonalInfoDTO;
import com.zdmj.resumeService.dto.SkillResponse;
import com.zdmj.resumeService.dto.SkillItemDTO;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会话 context / config 解析与简历上下文格式化。
 */
public final class ConversationContextSupport {

    public static final String CONFIG_USE_SYSTEM_KNOWLEDGE = "useSystemKnowledge";
    public static final String CONTEXT_TYPE_RESUME = "resume";
    public static final String PROMPT_VAR_RESUME_CONTEXT = "resumeContext";
    private static final String EMPTY_RESUME_HINT = "（用户尚未填写简历信息，请结合通用求职建议作答，并提示用户完善简历以获得更精准建议。）";

    private ConversationContextSupport() {
    }

    public static Optional<Map<String, Object>> buildResumeContextEntry(ResumeContentResponse resume) {
        String text = formatResume(resume);
        if (!StringUtils.hasText(text)) {
            return Optional.empty();
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", CONTEXT_TYPE_RESUME);
        entry.put("content", text);
        return Optional.of(entry);
    }

    public static String extractResumeText(List<Map<String, Object>> context) {
        if (CollectionUtils.isEmpty(context)) {
            return "";
        }
        for (Map<String, Object> item : context) {
            if (item == null) {
                continue;
            }
            if (!CONTEXT_TYPE_RESUME.equals(String.valueOf(item.get("type")))) {
                continue;
            }
            Object content = item.get("content");
            if (content != null && StringUtils.hasText(String.valueOf(content))) {
                return String.valueOf(content).trim();
            }
        }
        return "";
    }

    public static Map<String, Object> buildChatPromptVars(Conversation conversation) {
        Map<String, Object> vars = new HashMap<>();
        String resumeText = conversation == null ? "" : extractResumeText(conversation.getContext());
        vars.put(PROMPT_VAR_RESUME_CONTEXT, StringUtils.hasText(resumeText) ? resumeText : EMPTY_RESUME_HINT);
        return vars;
    }

    public static boolean resolveUseSystemKnowledge(MessageDTO dto, Conversation conversation) {
        if (dto != null && dto.getUseSystemKnowledge() != null) {
            return Boolean.TRUE.equals(dto.getUseSystemKnowledge());
        }
        if (conversation == null || conversation.getConfig() == null) {
            return false;
        }
        Object raw = conversation.getConfig().get(CONFIG_USE_SYSTEM_KNOWLEDGE);
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        if (raw instanceof String text) {
            return "true".equalsIgnoreCase(text.trim()) || "1".equals(text.trim());
        }
        return false;
    }

    public static String formatResume(ResumeContentResponse resume) {
        if (resume == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();

        ResumePersonalInfoDTO personal = resume.getPersonalInfo();
        if (personal != null) {
            appendLine(sb, "姓名", personal.getName());
            appendLine(sb, "电话", personal.getPhone());
            appendLine(sb, "个人主页", personal.getHomepageUrl());
            appendLine(sb, "意向工作城市", personal.getPreferredWorkCity());
        }

        SkillResponse skill = resume.getSkill();
        if (skill != null && skill.getContent() != null && !skill.getContent().isEmpty()) {
            sb.append("\n## 技能\n");
            for (SkillItemDTO item : skill.getContent()) {
                if (item == null || !StringUtils.hasText(item.getType())) {
                    continue;
                }
                sb.append("- ").append(item.getType().trim());
                if (!CollectionUtils.isEmpty(item.getContent())) {
                    sb.append("：").append(String.join("、", item.getContent()));
                }
                sb.append('\n');
            }
        }

        appendEducationSection(sb, resume.getEducations());
        appendCareerSection(sb, resume.getCareers());
        appendProjectSection(sb, resume.getProjects());
        appendAwardSection(sb, resume.getAwards());

        return sb.toString().trim();
    }

    private static void appendEducationSection(StringBuilder sb, List<EducationRequest> educations) {
        if (CollectionUtils.isEmpty(educations)) {
            return;
        }
        sb.append("\n## 教育经历\n");
        for (EducationRequest edu : educations) {
            if (edu == null) {
                continue;
            }
            sb.append("- ");
            appendPart(sb, edu.getSchool());
            appendPart(sb, edu.getMajor());
            appendPart(sb, formatDegree(edu.getDegree()));
            appendPart(sb, joinRange(formatDate(edu.getStartDate()), formatDate(edu.getEndDate())));
            if (StringUtils.hasText(edu.getGpa())) {
                sb.append(" | GPA ").append(edu.getGpa().trim());
            }
            sb.append('\n');
        }
    }

    private static void appendCareerSection(StringBuilder sb, List<CareerRequest> careers) {
        if (CollectionUtils.isEmpty(careers)) {
            return;
        }
        sb.append("\n## 工作经历\n");
        for (CareerRequest career : careers) {
            if (career == null) {
                continue;
            }
            sb.append("- ");
            appendPart(sb, career.getCompany());
            appendPart(sb, career.getPosition());
            appendPart(sb, joinRange(formatDate(career.getStartDate()), formatDate(career.getEndDate())));
            if (StringUtils.hasText(career.getDetails())) {
                sb.append("：").append(career.getDetails().trim());
            }
            sb.append('\n');
        }
    }

    private static void appendProjectSection(StringBuilder sb, List<ProjectExperienceRequest> projects) {
        if (CollectionUtils.isEmpty(projects)) {
            return;
        }
        sb.append("\n## 项目经历\n");
        for (ProjectExperienceRequest project : projects) {
            if (project == null) {
                continue;
            }
            sb.append("- ");
            appendPart(sb, project.getName());
            appendPart(sb, project.getRole());
            appendPart(sb, joinRange(formatDate(project.getStartDate()), formatDate(project.getEndDate())));
            if (!CollectionUtils.isEmpty(project.getTechStack())) {
                sb.append(" | 技术栈：").append(String.join("、", project.getTechStack()));
            }
            if (StringUtils.hasText(project.getHighlights())) {
                sb.append("：").append(project.getHighlights().trim());
            } else if (StringUtils.hasText(project.getDescription())) {
                sb.append("：").append(project.getDescription().trim());
            }
            sb.append('\n');
        }
    }

    private static void appendAwardSection(StringBuilder sb, List<AwardRequest> awards) {
        if (CollectionUtils.isEmpty(awards)) {
            return;
        }
        sb.append("\n## 获奖经历\n");
        for (AwardRequest award : awards) {
            if (award == null) {
                continue;
            }
            sb.append("- ");
            appendPart(sb, award.getName());
            appendPart(sb, formatDate(award.getAwardDate()));
            if (award.getAwardType() != null) {
                sb.append("（类型=").append(award.getAwardType()).append("）");
            }
            if (StringUtils.hasText(award.getDescription())) {
                sb.append("：").append(award.getDescription().trim());
            }
            sb.append('\n');
        }
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        if (!sb.toString().contains("## 基本信息")) {
            sb.append("## 基本信息\n");
        }
        sb.append(label).append("：").append(value.trim()).append('\n');
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (!StringUtils.hasText(part)) {
            return;
        }
        if (sb.charAt(sb.length() - 1) != ' ' && sb.charAt(sb.length() - 1) != '-' && sb.charAt(sb.length() - 1) != '\n') {
            sb.append(" | ");
        }
        sb.append(part.trim());
    }

    private static String formatDate(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static String formatDegree(Integer degree) {
        if (degree == null) {
            return null;
        }
        return switch (degree) {
            case 1 -> "博士";
            case 2 -> "硕士";
            case 3 -> "本科";
            case 4 -> "大专";
            case 5 -> "高中";
            default -> "其他";
        };
    }

    private static String joinRange(String start, String end) {
        List<String> parts = new ArrayList<>(2);
        if (StringUtils.hasText(start)) {
            parts.add(start.trim());
        }
        if (StringUtils.hasText(end)) {
            parts.add(end.trim());
        }
        return parts.isEmpty() ? null : String.join(" - ", parts);
    }
}
