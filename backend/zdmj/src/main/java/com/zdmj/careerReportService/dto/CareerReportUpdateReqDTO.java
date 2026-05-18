package com.zdmj.careerReportService.dto;

import java.util.Map;
import lombok.Data;

/**
 * 手动编辑报告请求 DTO
 */
@Data
public class CareerReportUpdateReqDTO {

    /**
     * 手动编辑后的结构化正文。
     */
    private Map<String, Object> reportContent;
}
