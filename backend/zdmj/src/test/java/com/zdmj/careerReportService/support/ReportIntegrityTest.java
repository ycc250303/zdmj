package com.zdmj.careerReportService.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.zdmj.careerReportService.dto.CareerReportCheckResponse;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

class ReportIntegrityTest {

    @Test
    void 完整报告_检查结果通过且保留质量标记契约() {
        CareerReportCheckResponse check = ReportIntegrity.check(completeReport());

        assertThat(check.getPassed()).isTrue();
        assertThat(check.getCompletenessScore()).isEqualTo(100);
        assertThat(ReportIntegrity.qualityFlags(check)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "missingSections", List.of(), "nonActionableItems", List.of(),
                "weakEvidenceItems", List.of(), "riskLevel", "low"));
    }

    @ParameterizedTest
    @MethodSource("missingValues")
    void 必备章节为空_标记缺失并保留原有评分(Object emptyValue) {
        Map<String, Object> report = completeReport();
        report.put("careerGoals", emptyValue);

        CareerReportCheckResponse check = ReportIntegrity.check(report);

        assertThat(check.getPassed()).isFalse();
        assertThat(check.getMissingSections()).containsExactly("职业目标");
        assertThat(check.getCompletenessScore()).isEqualTo(88);
    }

    static Stream<Object> missingValues() {
        return Stream.of(null, "  ", List.of(), Map.of());
    }

    @Test
    void 行动项使用周期和交付物别名_列表与分组形式都能校验() {
        Map<String, Object> report = completeReport();
        report.put("actionPlan", Map.of(
                "shortTerm", List.of(
                        Map.of("period", "两周", "milestone", "作品"),
                        Map.of("timeWindow", "四周", "output", List.of("报告"))),
                "midTerm", Map.of("items", List.of("复盘"))));

        assertThat(ReportIntegrity.check(report).getNonActionableItems()).isEmpty();
        assertThat(ReportIntegrity.check(report).getCompletenessScore()).isEqualTo(100);
    }

    @Test
    void 行动项形状或字段无效_标记具体位置并拒绝通过() {
        Map<String, Object> report = completeReport();
        report.put("actionPlan", Map.of(
                "shortTerm", List.of("模糊建议", Map.of("cycle", 2, "deliverable", " ")),
                "midTerm", Map.of("tasks", List.of())));

        CareerReportCheckResponse check = ReportIntegrity.check(report);

        assertThat(check.getNonActionableItems()).containsExactly(
                "shortTerm#1", "shortTerm#2:missingCycle", "shortTerm#2:missingDeliverable", "midTerm:missingTasks");
        assertThat(check.getPassed()).isFalse();
        assertThat(check.getCompletenessScore()).isEqualTo(68);
        assertThat(check.getRiskLevel()).isEqualTo("medium");
    }

    @Test
    void 空报告_返回完整缺失列表及低完整度() {
        CareerReportCheckResponse check = ReportIntegrity.check(Map.of());

        assertThat(check.getMissingSections()).containsExactly("职业探索", "职业目标", "职业路径", "行动计划", "评估计划");
        assertThat(check.getNonActionableItems()).containsExactly("actionPlan");
        assertThat(check.getWeakEvidenceItems()).containsExactly("evidence");
        assertThat(check.getCompletenessScore()).isEqualTo(26);
        assertThat(check.getRiskLevel()).isEqualTo("high");
    }

    @ParameterizedTest
    @MethodSource("missingValues")
    void 行动阶段为空_记录阶段名称(Object emptyValue) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("shortTerm", emptyValue);
        plan.put("midTerm", List.of(Map.of("cycle", "四周", "output", "清单")));
        Map<String, Object> report = completeReport();
        report.put("actionPlan", plan);

        String issue = emptyValue instanceof Map<?, ?> ? "shortTerm:missingTasks" : "shortTerm";
        assertThat(ReportIntegrity.check(report).getNonActionableItems()).containsExactly(issue);
    }

    @Test
    void 合并检查_问题按出现顺序去重并保留任一未通过结论() {
        CareerReportCheckResponse local = ReportIntegrity.check(Map.of());
        CareerReportCheckResponse model = new CareerReportCheckResponse();
        model.setPassed(true);
        model.setCompletenessScore(80);
        model.setMissingSections(Arrays.asList("职业探索", "引用说明", null));
        model.setNonActionableItems(List.of("actionPlan", "验收标准"));

        CareerReportCheckResponse check = ReportIntegrity.merge(local, model);

        assertThat(check.getPassed()).isFalse();
        assertThat(check.getCompletenessScore()).isEqualTo(53);
        assertThat(check.getMissingSections()).containsExactly("职业探索", "职业目标", "职业路径", "行动计划", "评估计划", "引用说明");
        assertThat(check.getNonActionableItems()).containsExactly("actionPlan", "验收标准");
        assertThat(check.getWeakEvidenceItems()).containsExactly("evidence");
    }

    @ParameterizedTest
    @MethodSource("invalidModelChecks")
    void 模型检查缺少必要字段或评分越界_立即终止(CareerReportCheckResponse model) {
        assertThatThrownBy(() -> ReportIntegrity.merge(ReportIntegrity.check(completeReport()), model))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CAREER_REPORT_INVALID));
    }

    static Stream<CareerReportCheckResponse> invalidModelChecks() {
        Stream<CareerReportCheckResponse> scores = Stream.of(-1, 101, null).map(score -> {
            CareerReportCheckResponse check = new CareerReportCheckResponse();
            check.setPassed(true);
            check.setCompletenessScore(score);
            return check;
        });
        CareerReportCheckResponse missingPassed = new CareerReportCheckResponse();
        missingPassed.setCompletenessScore(80);
        return Stream.concat(Stream.of(null, missingPassed), scores);
    }

    private static Map<String, Object> completeReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("careerExploration", "岗位分析");
        report.put("careerGoals", List.of("目标"));
        report.put("careerPath", List.of("路径"));
        report.put("actionPlan", Map.of(
                "shortTerm", List.of(Map.of("cycle", "两周", "deliverable", "作品")),
                "midTerm", List.of(Map.of("cycle", "四周", "deliverable", "投递清单"))));
        report.put("evaluationPlan", Map.of("cycle", "双周"));
        report.put("evidence", Map.of("technicalEvidence", List.of("项目经历")));
        return report;
    }
}
