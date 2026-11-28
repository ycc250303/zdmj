package com.zdmj.careerReportService.dto;

import java.util.Map;
import lombok.Data;

/** 模型反序列化填充的报告正文，生成与润色共用；由业务服务检查非空约束。 */
@Data
public class LlmReportPayload {
    /** 结构化报告正文，生成或润色成功时必须为非空对象。 */
    private Map<String, Object> reportContent;
}
