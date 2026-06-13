package com.zdmj.resumeService.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
