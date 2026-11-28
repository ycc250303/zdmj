package com.zdmj.resumeService.api;

import java.util.List;

/**
 * 已保存画像的不可变公开快照。
 * @param targetRoleType 岗位方向
 * @param professionalSkills 专业技能
 * @param honorsAndAwards 获奖经历
 * @param innovationAbility 创新能力
 * @param learningAbility 学习能力
 * @param pressureResistance 抗压能力
 * @param communicationAbility 沟通能力
 * @param practicalAbility 实践能力
 * @param competitivenessScore 综合竞争力，范围0～100
 * @param scoreDetail 评分明细
 * @param strengths 优势亮点，保留空值及顺序
 * @param suggestions 改进建议，保留空值及顺序
 * @param summary 综合评价
 */
public record StudentProfileSnapshot(
        String targetRoleType,
        String professionalSkills,
        String honorsAndAwards,
        String innovationAbility,
        String learningAbility,
        String pressureResistance,
        String communicationAbility,
        String practicalAbility,
        Integer competitivenessScore,
        StudentCapabilityScoreDetail scoreDetail,
        List<String> strengths,
        List<StudentCapabilitySuggestion> suggestions,
        String summary) {
    /** 复制集合结构，保留 null 与元素语义。 */
    public StudentProfileSnapshot {
        strengths = strengths == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(strengths));
        suggestions = suggestions == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(suggestions));
    }
}
