package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 获奖信息响应（镜像当前 Award Entity JSON）
 */
@Data
public class AwardResponse {

    private Long id;

    private Long userId;

    private Integer awardType;

    private String name;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate awardDate;

    private String description;
}
