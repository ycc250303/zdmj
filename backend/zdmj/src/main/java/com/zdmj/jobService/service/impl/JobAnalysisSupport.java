package com.zdmj.jobService.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.jobService.dto.JobListItemResponse;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.util.StringUtils;

public final class JobAnalysisSupport {

    private JobAnalysisSupport() {
    }

    public static String buildJobContext(JobListItemResponse job, String intro) {
        return """
                %s
                岗位名称：%s
                公司名称：%s
                工作地点：%s
                薪资：%s
                岗位描述：%s
                岗位职责：%s
                岗位要求：%s
                关键词：%s
                公司行业：%s
                """.formatted(
                intro,
                valueOrNA(job.getJobName()),
                valueOrNA(job.getCompanyName()),
                valueOrNA(job.getLocation()),
                valueOrNA(job.getSalary()),
                valueOrNA(job.getDescription()),
                joinList(job.getJobDuties()),
                joinList(job.getJobRequirements()),
                joinList(job.getKeywords()),
                joinList(job.getCompanyIndustries()));
    }

    public static String toJson(Object value, ObjectMapper objectMapper, Logger log, String warnMessage) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("{}: {}", warnMessage, e.getMessage());
            return null;
        }
    }

    private static String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "未提供";
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((a, b) -> a + "；" + b)
                .orElse("未提供");
    }

    private static String valueOrNA(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未提供";
    }
}
