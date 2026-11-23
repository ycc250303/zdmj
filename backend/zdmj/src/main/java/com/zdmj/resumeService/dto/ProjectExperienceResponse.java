package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zdmj.resumeService.enums.ProjectStatusEnum;

import java.time.LocalDate;
import java.util.List;

/**
 * 项目经历响应（镜像当前 ProjectExperience Entity JSON）
 *
 * @param id 项目经历编号，对应 project_experiences.id
 * @param userId 所属用户编号
 * @param name 项目名称
 * @param startDate 开始日期
 * @param endDate 结束日期
 * @param role 项目中的角色与职责
 * @param description 项目内容说明
 * @param contribution 项目贡献
 * @param techStack 项目使用的技术列表
 * @param highlights 项目亮点
 * @param url 项目地址
 * @param status 项目分析状态，对应 status：1 已提交、2 挖掘中、3 润色中、4 已完成
 * @param lookupResult 项目查询结果
 */
public record ProjectExperienceResponse(
        Long id,
        Long userId,
        String name,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate startDate,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate endDate,
        String role,
        String description,
        String contribution,
        List<String> techStack,
        List<String> highlights,
        String url,
        Integer status,
        String lookupResult) {

    public ProjectStatusEnum getStatusEnum() {
        return ProjectStatusEnum.fromCode(this.status);
    }

    /** 复制集合结构，保留既有空值和元素语义。 */
    public ProjectExperienceResponse {
        techStack = techStack == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(techStack));
        highlights = highlights == null ? null
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(highlights));
    }
}
