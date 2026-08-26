package com.zdmj.resumeService.dto;

import lombok.Data;

import java.util.List;

/**
 * 简历响应（镜像当前 Resume Entity JSON）
 */
@Data
public class ResumeResponse {

    private Long id;

    private Long userId;

    private Long skillId;

    private List<Long> projects;

    private List<Long> careers;

    private List<Long> educations;

    private List<Long> awards;

    private List<Long> resumeMatchedIds;
}
