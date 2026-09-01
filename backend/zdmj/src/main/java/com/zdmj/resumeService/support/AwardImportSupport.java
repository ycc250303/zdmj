package com.zdmj.resumeService.support;

import com.zdmj.resumeService.enums.AwardTypeEnum;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 简历导入：先按关键词检出可能含奖项的完整句子，再交给 LLM 判断并结构化。
 */
public final class AwardImportSupport {

    public static final String CANDIDATE_SECTION_HEADER = "【奖项目候选句】";

    /**
     * 奖项线索：xx等奖、奖学金、第 x 名，以及常见同义说法。
     */
    private static final Pattern AWARD_HINT = Pattern.compile(
            "奖学金|助学金|等奖|获奖|第[一二三四五六七八九十百\\d]+名|冠军|亚军|季军|金奖|银奖|铜奖",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[。；;!?！？])");

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

    /**
     * 从简历原文切出可能表示奖项的完整句子（不去截奖项名称）。
     */
    public static List<String> detectCandidateSentences(String sourceText) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(sourceText)) {
            return result;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String line : sourceText.split("\\R+")) {
            for (String raw : SENTENCE_END.split(line)) {
                String sentence = stripListMarker(raw);
                if (sentence.length() < 4 || !AWARD_HINT.matcher(sentence).find()) {
                    continue;
                }
                if (seen.add(normalizeAwardNameKey(sentence))) {
                    result.add(sentence);
                }
            }
        }
        return result;
    }

    /**
     * 将候选完整句编成编号列表，交给 LLM 判断其中哪些是奖项。
     */
    public static String buildAwardsJudgeUserMessage(List<String> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append(CANDIDATE_SECTION_HEADER).append('\n');
        if (candidates == null || candidates.isEmpty()) {
            sb.append("（未检出可能含奖项的句子。awards 输出空数组。）\n");
            return sb.toString();
        }
        sb.append("只根据下列完整句子填写 awards[]。句中出现奖学金、xx等奖、第x名就必须各抽一条，");
        sb.append("即使同句还有 GPA、课程或项目描述。一句可含多条获奖（顿号/逗号分隔），必须拆成多条，禁止合并成一个 name。");
        sb.append("awardDate 只取该奖自己原文中的时间，没有则 null，禁止套用教育/实习/项目或其他奖项的日期。");
        sb.append("不是奖项则跳过；同一奖项只一条。\n");
        for (int i = 0; i < candidates.size(); i++) {
            sb.append(i + 1).append(". ").append(candidates.get(i)).append('\n');
        }
        return sb.toString();
    }

    private static String stripListMarker(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.replaceAll("^[·•\\-*\\s]+", "")
                .replaceFirst("^\\d{1,2}[.、．)]\\s*", "")
                .trim();
    }
}
