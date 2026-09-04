package com.zdmj.resumeService.support;

import com.zdmj.resumeService.enums.AwardTypeEnum;
import org.springframework.util.StringUtils;

/**
 * 简历导入奖项后处理：类型校正、名称清洗、去重键。
 */
public final class AwardImportSupport {

    private AwardImportSupport() {
    }

    public static Integer inferAwardTypeFromName(String name) {
        if (!StringUtils.hasText(name)) {
            return AwardTypeEnum.OTHER.getCode();
        }
        if (containsScholarshipKeyword(name)) {
            return AwardTypeEnum.SCHOLARSHIP.getCode();
        }
        if (name.matches(".*(竞赛|比赛|大赛|杯|挑战赛|Contest).*")) {
            return AwardTypeEnum.COMPETITION.getCode();
        }
        return AwardTypeEnum.OTHER.getCode();
    }

    public static Integer resolveAwardType(Integer llmType, String name) {
        Integer fromName = inferAwardTypeFromName(name);
        if (fromName.equals(AwardTypeEnum.SCHOLARSHIP.getCode())
                || fromName.equals(AwardTypeEnum.COMPETITION.getCode())) {
            return fromName;
        }
        if (llmType != null && AwardTypeEnum.fromCode(llmType) != null) {
            return llmType;
        }
        return AwardTypeEnum.OTHER.getCode();
    }

    public static boolean containsScholarshipKeyword(String name) {
        return name.contains("奖学金") || name.contains("助学金");
    }

    public static String cleanAwardName(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        return name.replaceAll("^[·•\\-*\\s]+", "")
                .replaceFirst("^\\d{1,2}[.、．)]\\s*", "")
                .replaceFirst("^(?:获得|荣获|获)", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String normalizeAwardNameKey(String name) {
        return cleanAwardName(name).replaceAll("\\s+", "").toLowerCase();
    }
}
