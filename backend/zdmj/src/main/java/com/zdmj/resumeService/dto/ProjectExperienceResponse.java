package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zdmj.resumeService.enums.ProjectStatusEnum;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 项目经历响应（镜像当前 ProjectExperience Entity JSON）
 */
@Data
public class ProjectExperienceResponse {

    private Long id;

    private Long userId;

    private String name;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private String role;

    private String description;

    private String contribution;

    private List<String> techStack;

    private String highlights;

    private String url;

    private Integer status;

    private String lookupResult;

    public ProjectStatusEnum getStatusEnum() {
        return ProjectStatusEnum.fromCode(this.status);
    }

    public void setStatusEnum(ProjectStatusEnum statusEnum) {
        this.status = statusEnum != null ? statusEnum.getCode() : null;
    }
}
