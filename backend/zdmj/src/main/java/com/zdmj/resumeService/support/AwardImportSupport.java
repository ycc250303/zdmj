package com.zdmj.resumeService.support;

import com.zdmj.resumeService.dto.ResumeImportParseResultDTO;
import com.zdmj.resumeService.enums.AwardTypeEnum;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 简历导入时奖项名称、类型与原文补全规则。
 */
public final class AwardImportSupport {

    private static final Pattern SCHOLARSHIP_NAME = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9·（）()\\s]{2,48}(?:奖学金|助学金))");

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
        return name.replaceAll("^[·•\\-*\\d.\\s]+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String normalizeAwardNameKey(String name) {
        return name.replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * 当 LLM 漏提奖学金时，从原文中按「奖学金/助学金」关键词补全。
     */
    public static void supplementScholarshipsFromSourceText(
            String sourceText,
            List<ResumeImportParseResultDTO.AwardItem> awards,
            List<String> warnings,
            Function<ResumeImportParseResultDTO.AwardItem, java.time.LocalDate> dateParser,
            BiFunction<String, String, java.time.LocalDate> contextDateParser,
            Function<java.time.LocalDate, String> dateFormatter) {
        if (!StringUtils.hasText(sourceText)) {
            return;
        }
        Set<String> existing = awards.stream()
                .map(ResumeImportParseResultDTO.AwardItem::getName)
                .filter(StringUtils::hasText)
                .map(AwardImportSupport::normalizeAwardNameKey)
                .collect(Collectors.toSet());

        Matcher matcher = SCHOLARSHIP_NAME.matcher(sourceText);
        while (matcher.find()) {
            String name = cleanAwardName(matcher.group(1));
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String key = normalizeAwardNameKey(name);
            if (existing.contains(key)) {
                continue;
            }
            String context = sourceText.substring(
                    Math.max(0, matcher.start() - 24),
                    Math.min(sourceText.length(), matcher.end() + 24));

            ResumeImportParseResultDTO.AwardItem item = new ResumeImportParseResultDTO.AwardItem();
            item.setName(name);
            item.setAwardType(AwardTypeEnum.SCHOLARSHIP.getCode());

            java.time.LocalDate awardDate = dateParser.apply(item);
            if (awardDate == null) {
                awardDate = contextDateParser.apply(context, name);
            }
            if (awardDate == null) {
                continue;
            }
            item.setAwardDate(dateFormatter.apply(awardDate));
            awards.add(item);
            existing.add(key);
            warnings.add("已从原文补充奖学金：" + name);
        }
    }
}
