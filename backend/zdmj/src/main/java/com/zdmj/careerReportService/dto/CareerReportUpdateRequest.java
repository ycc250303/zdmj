package com.zdmj.careerReportService.dto;

import java.util.Map;
import lombok.Data;

/**
 * 手动编辑报告请求体
 */
@Data
public class CareerReportUpdateRequest {

    /**
     * 手动编辑后的结构化正文。
     */
    private Map<String, Object> reportContent;
}
