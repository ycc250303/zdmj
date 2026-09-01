package com.zdmj.resumeService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 简历导入：仅根据奖项目候选句判断后的结构化奖项。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeImportAwardsResponse {

    private List<ResumeImportParseResponse.AwardItem> awards = new ArrayList<>();
}
