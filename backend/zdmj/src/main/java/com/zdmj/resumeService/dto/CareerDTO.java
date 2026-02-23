package com.zdmj.resumeService.dto;

import com.zdmj.common.model.BaseEntity;
import com.zdmj.common.validation.CreateGroup;
import com.zdmj.common.validation.UpdateGroup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 工作/实习经历DTO
 */
@Data
public class CareerDTO extends BaseEntity {
    /**
     * 工作/实习经历ID（主键，自增）
     */
    @NotNull(message = "工作/实习经历ID不能为空", groups = UpdateGroup.class)
    private Long id;

    /**
     * 公司名称
     */
    @NotBlank(message = "公司名称不能为空", groups = CreateGroup.class)
    private String company;

    /**
     * 职位名称
     */
    @NotBlank(message = "职位名称不能为空", groups = CreateGroup.class)
    private String position;

    /**
     * 入职时间
     */
    @NotNull(message = "入职时间不能为空", groups = CreateGroup.class)
    private LocalDate startDate;

    /**
     * 离职时间（在职可为空）
     */
    private LocalDate endDate;

    /**
     * 是否在简历中展示
     */
    private Boolean visible = true;

    /**
     * 工作职责/业绩（可富文本）
     */
    private String details;
}
