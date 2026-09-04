package com.zdmj.resumeService.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AwardImportSupportTest {

    @Test
    void inferAwardTypeFromName_scholarship_shouldReturnOne() {
        assertEquals(1, AwardImportSupport.inferAwardTypeFromName("同济大学本科生奖学金"));
        assertEquals(1, AwardImportSupport.inferAwardTypeFromName("国家励志助学金"));
    }

    @Test
    void inferAwardTypeFromName_competition_shouldReturnTwo() {
        assertEquals(2, AwardImportSupport.inferAwardTypeFromName("全国大学生数学建模竞赛省一等奖"));
    }

    @Test
    void resolveAwardType_nameShouldOverrideWrongLlmType() {
        assertEquals(1, AwardImportSupport.resolveAwardType(2, "同济大学本科生奖学金"));
        assertEquals(2, AwardImportSupport.resolveAwardType(1, "蓝桥杯全国总决赛二等奖"));
    }

    @Test
    void cleanAwardName_shouldStripHonorificPrefix() {
        assertEquals("同济大学本科优秀学生奖学金二等奖",
                AwardImportSupport.cleanAwardName("获同济大学本科优秀学生奖学金二等奖"));
        assertEquals("国家奖学金", AwardImportSupport.cleanAwardName("获得国家奖学金"));
        assertEquals("2024年同济大学本科生奖学金",
                AwardImportSupport.cleanAwardName("2024年同济大学本科生奖学金"));
    }
}
