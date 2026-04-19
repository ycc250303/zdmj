package com.zdmj.resumeService.dto;

import com.zdmj.common.util.PromptUtil.JobRole;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 简历角色检测DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResumeRoleDetectDTO {
    /**
     * 岗位角色
     */
    private JobRole role;

    /**
     * 置信度
     */
    private double confidence;

    /**
     * 原因
     */
    private String reason;
}
