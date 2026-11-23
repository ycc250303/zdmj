package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 获奖信息请求
 *
 * @param id 获奖编号；更新时必填，创建时不需要
 * @param name 奖项名称。创建时不能为空；更新时省略或为 null 保持原值。
 * @param awardDate 获奖日期，格式为 yyyy-MM-dd；创建时必填
 * @param description 奖项说明；更新时省略或为 null 保持原值
 */
public record AwardRequest(
        @NotNull(message = "获奖信息ID不能为空", groups = UpdateGroup.class)
        Long id,
        @NotBlank(message = "奖项名称不能为空", groups = CreateGroup.class)
        String name,
        @NotNull(message = "获奖时间不能为空", groups = CreateGroup.class)
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate awardDate,
        String description) {
}
