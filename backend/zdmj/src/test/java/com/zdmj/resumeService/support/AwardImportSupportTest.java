package com.zdmj.resumeService.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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

    @Test
    void detectCandidateSentences_shouldKeepCompleteSentencesNotTruncatedNames() {
        String source = """
                GPA: 4.43/5.00，获同济大学本科优秀学生奖学金二等奖
                主修数据结构与算法、操作系统
                以项目负责人身份获 2025 年中国高校计算机大赛智能交互创新赛全国一等奖、AIGC 创新赛全国三等奖
                挑战杯全国决赛第3名
                """;

        List<String> candidates = AwardImportSupport.detectCandidateSentences(source);

        assertEquals(3, candidates.size());
        assertTrue(candidates.get(0).contains("GPA: 4.43/5.00"));
        assertTrue(candidates.get(0).contains("奖学金二等奖"));
        assertFalse(candidates.get(0).equals("同济大学本科优秀学生奖学金"));
        assertTrue(candidates.get(1).contains("全国一等奖") && candidates.get(1).contains("全国三等奖"));
        assertTrue(candidates.get(2).contains("第3名"));
        assertTrue(candidates.stream().noneMatch(s -> s.contains("主修数据结构")));
    }

    @Test
    void buildAwardsJudgeUserMessage_shouldNumberCompleteSentences() {
        String source = "GPA: 4.43/5.00，获同济大学本科优秀学生奖学金二等奖";
        List<String> candidates = AwardImportSupport.detectCandidateSentences(source);
        String message = AwardImportSupport.buildAwardsJudgeUserMessage(candidates);

        assertTrue(message.startsWith(AwardImportSupport.CANDIDATE_SECTION_HEADER));
        assertTrue(message.contains("1. GPA: 4.43/5.00，获同济大学本科优秀学生奖学金二等奖"));
        assertTrue(message.contains("必须拆成多条"));
        assertTrue(message.contains("禁止套用"));
        assertFalse(message.contains("【简历原文】"));
    }

    @Test
    void buildAwardsJudgeUserMessage_noHint_shouldAskEmptyAwards() {
        String message = AwardImportSupport.buildAwardsJudgeUserMessage(List.of());
        assertTrue(message.contains("未检出可能含奖项的句子"));
    }
}
