package com.zdmj.resumeService.dto;

import java.time.LocalDate;

/**
 * 工作/实习经历响应（镜像当前 Career Entity JSON）
 *
 * @param id 工作经历编号，对应 careers.id
 * @param userId 所属用户编号
 * @param company 公司名称
 * @param position 工作职位
 * @param startDate 入职日期
 * @param endDate 离职日期；在职时可为空
 * @param details 工作职责与业绩
 */
public record CareerResponse(
        Long id,
        Long userId,
        String company,
        String position,
        LocalDate startDate,
        LocalDate endDate,
        String details) {
}
