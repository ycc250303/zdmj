package com.zdmj.resumeService.dto;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 简历请求（一用户一份，仅维护 skillId 关联）
 */
@Data
public class ResumeRequest {
    /**
     * 简历ID（更新时不能为空）
     */
    @NotNull(message = "简历ID不能为空", groups = UpdateGroup.class)
    private Long id;

    /**
     * 技能清单ID（创建时不能为空）
     */
    @NotNull(message = "技能清单ID不能为空", groups = CreateGroup.class)
    private Long skillId;
}
