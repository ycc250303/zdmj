package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** 简历导入的奖项，模型解析后需要规范化奖项名称与日期。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeImportAwardItem {
    /** 奖项名称，接受 awardName、title、honor 别名，供简历导入展示。 */
    @JsonAlias({ "awardName", "title", "honor" })
    private String name;
    /** 获奖日期文本，接受 date、award_date 别名，供业务规范化。 */
    @JsonAlias({ "date", "award_date" })
    private String awardDate;
    /** 奖项说明，供简历导入展示。 */
    private String description;
}
