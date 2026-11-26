package com.zdmj.resumeService.dto;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 工作/实习经历请求
 *
 * @param id 工作/实习经历ID（主键，自增，更新时不能为空）
 * @param company 公司名称（创建时不能为空）
 * @param position 职位名称（创建时不能为空）
 * @param startDate 入职时间（创建时不能为空）
 * @param endDate 离职时间（在职可为空）
 * @param details 工作职责/业绩（可富文本）
 */
public record CareerRequest(
        @NotNull(message = "工作/实习经历ID不能为空", groups = UpdateGroup.class)
        Long id,
        @NotBlank(message = "公司名称不能为空", groups = CreateGroup.class)
        String company,
        @NotBlank(message = "职位名称不能为空", groups = CreateGroup.class)
        String position,
        @NotNull(message = "入职时间不能为空", groups = CreateGroup.class)
        LocalDate startDate,
        LocalDate endDate,
        String details) {
}
