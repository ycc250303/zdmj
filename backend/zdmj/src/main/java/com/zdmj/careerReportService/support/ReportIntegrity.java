package com.zdmj.careerReportService.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.util.StringUtils;

import com.zdmj.careerReportService.dto.CareerReportCheckResponse;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/** 报告章节、行动计划与证据的纯规则检查，不访问模型或数据库。 */
public final class ReportIntegrity {

    private ReportIntegrity() {
    }

    /**
     * 检查必备章节、短中期行动项与证据形状，按缺失项计算完整度。
     *
     * @param content 报告正文，不能为空
     * @return 本地检查结果，包含完整的问题列表、评分及风险等级
     */
    public static CareerReportCheckResponse check(Map<String, Object> content) {
        Objects.requireNonNull(content, "报告正文不能为空");
        List<String> missing = new ArrayList<>();
        String[][] sections = {
                {"careerExploration", "职业探索"}, {"careerGoals", "职业目标"},
                {"careerPath", "职业路径"}, {"actionPlan", "行动计划"}, {"evaluationPlan", "评估计划"}
        };
        for (String[] section : sections) {
            if (!hasContent(content.get(section[0]))) {
                missing.add(section[1]);
            }
        }
        List<String> nonActionable = new ArrayList<>();
        if (content.get("actionPlan") instanceof Map<?, ?> plan) {
            checkActions(plan.get("shortTerm"), "shortTerm", nonActionable);
            checkActions(plan.get("midTerm"), "midTerm", nonActionable);
        } else {
            nonActionable.add("actionPlan");
        }
        Object evidence = content.get("evidence");
        List<String> weakEvidence = evidence instanceof Map<?, ?> || evidence instanceof List<?>
                ? List.of() : List.of("evidence");
        int score = 100 - missing.size() * 12 - nonActionable.size() * 8 - weakEvidence.size() * 6;
        return result(score, missing.isEmpty() && nonActionable.size() <= 1,
                missing, nonActionable, weakEvidence);
    }

    /**
     * 合并本地与模型检查，问题列表去重，评分取平均，任一检查未通过则整体未通过。
     *
     * @param local 完整的本地结果
     * @param model 模型结果，必须提供通过标记和 0～100 的完整度
     * @return 合并结果，风险等级由合并评分计算
     * @throws BusinessException 模型结果缺失或完整度非法时终止本次检查
     */
    public static CareerReportCheckResponse merge(CareerReportCheckResponse local, CareerReportCheckResponse model) {
        if (model == null || model.getPassed() == null || model.getCompletenessScore() == null
                || model.getCompletenessScore() < 0 || model.getCompletenessScore() > 100) {
            throw new BusinessException(ErrorCode.CAREER_REPORT_INVALID, "报告完整性检查结果不符合要求");
        }
        return result((local.getCompletenessScore() + model.getCompletenessScore()) / 2,
                Boolean.TRUE.equals(local.getPassed()) && model.getPassed(),
                mergeItems(local.getMissingSections(), model.getMissingSections()),
                mergeItems(local.getNonActionableItems(), model.getNonActionableItems()),
                mergeItems(local.getWeakEvidenceItems(), model.getWeakEvidenceItems()));
    }

    /**
     * 生成持久化质量标记，字段与报告响应中的 qualityFlags 保持一致。
     *
     * @param check 本地检查或合并检查结果
     * @return 三类问题列表及风险等级
     */
    public static Map<String, Object> qualityFlags(CareerReportCheckResponse check) {
        Map<String, Object> flags = new LinkedHashMap<>();
        flags.put("missingSections", check.getMissingSections());
        flags.put("nonActionableItems", check.getNonActionableItems());
        flags.put("weakEvidenceItems", check.getWeakEvidenceItems());
        flags.put("riskLevel", check.getRiskLevel());
        return flags;
    }

    private static CareerReportCheckResponse result(int score, boolean passed, List<String> missing,
            List<String> nonActionable, List<String> weakEvidence) {
        int boundedScore = Math.max(0, Math.min(100, score));
        CareerReportCheckResponse result = new CareerReportCheckResponse();
        result.setCompletenessScore(boundedScore);
        result.setPassed(passed);
        result.setRiskLevel(boundedScore >= 80 ? "low" : boundedScore >= 60 ? "medium" : "high");
        result.setMissingSections(missing);
        result.setNonActionableItems(nonActionable);
        result.setWeakEvidenceItems(weakEvidence);
        return result;
    }

    private static List<String> mergeItems(List<String> left, List<String> right) {
        return Stream.of(left, right).filter(Objects::nonNull).flatMap(List::stream)
                .filter(Objects::nonNull).distinct().toList();
    }

    private static boolean hasContent(Object value) {
        return switch (value) {
            case null -> false;
            case String text -> StringUtils.hasText(text);
            case List<?> list -> !list.isEmpty();
            case Map<?, ?> map -> !map.isEmpty();
            default -> true;
        };
    }

    private static boolean hasAnyKey(Map<?, ?> map, String... keys) {
        return Stream.of(keys).map(map::get)
                .filter(value -> value instanceof String || value instanceof List<?> || value instanceof Map<?, ?>)
                .anyMatch(ReportIntegrity::hasContent);
    }

    private static void checkActions(Object bucket, String name, List<String> issues) {
        if (bucket instanceof List<?> list && !list.isEmpty()) {
            for (int index = 0; index < list.size(); index++) {
                String itemName = name + "#" + (index + 1);
                if (!(list.get(index) instanceof Map<?, ?> item)) {
                    issues.add(itemName);
                    continue;
                }
                if (!hasAnyKey(item, "cycle", "period", "timeWindow")) {
                    issues.add(itemName + ":missingCycle");
                }
                if (!hasAnyKey(item, "deliverable", "milestone", "output")) {
                    issues.add(itemName + ":missingDeliverable");
                }
            }
        } else if (bucket instanceof Map<?, ?> map) {
            if (!hasAnyKey(map, "tasks", "items")) {
                issues.add(name + ":missingTasks");
            }
        } else {
            issues.add(name);
        }
    }
}
