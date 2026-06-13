package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 获奖信息 DTO
 */
@Data
public class AwardDTO {

    @NotNull(message = "获奖信息ID不能为空", groups = UpdateGroup.class)
    private Long id;

    /** 1=奖学金, 2=竞赛获奖, 3=其他类型 */
    @NotNull(message = "奖项类型不能为空", groups = CreateGroup.class)
    @Min(value = 1, message = "奖项类型不能小于1", groups = { CreateGroup.class, UpdateGroup.class })
    @Max(value = 3, message = "奖项类型不能大于3", groups = { CreateGroup.class, UpdateGroup.class })
    private Integer awardType;

    @NotBlank(message = "奖项名称不能为空", groups = CreateGroup.class)
    private String name;

    @NotNull(message = "获奖时间不能为空", groups = CreateGroup.class)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate awardDate;

    private String description;
}
