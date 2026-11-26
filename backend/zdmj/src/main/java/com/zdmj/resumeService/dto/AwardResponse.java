package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * 获奖信息响应（镜像当前 Award Entity JSON）
 *
 * @param id 获奖编号，对应 awards.id
 * @param userId 所属用户编号
 * @param name 奖项名称
 * @param awardDate 获奖日期
 * @param description 奖项说明
 */
public record AwardResponse(
        Long id,
        Long userId,
        String name,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate awardDate,
        String description) {
}
