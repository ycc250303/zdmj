package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 获奖信息请求
 */
@Data
public class AwardRequest {

    @NotNull(message = "获奖信息ID不能为空", groups = UpdateGroup.class)
    private Long id;

    /**
     * 奖项名称。创建时不能为空；更新时省略或为 null 保持原值。
     */
    @NotBlank(message = "奖项名称不能为空", groups = CreateGroup.class)
    private String name;

    @NotNull(message = "获奖时间不能为空", groups = CreateGroup.class)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate awardDate;

    private String description;
}
