package com.zdmj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * 模块边界。当前违规写入 {@code src/test/resources/archunit_store}，新违规会使测试失败。
 * 修复一项依赖后，从对应冻结文件删除该行。边界重构完成后删除冻结存储，只保留严格规则。
 */
@AnalyzeClasses(packages = "com.zdmj", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

    private static final String[] MODULES = {
            "userAuthService",
            "resumeService",
            "jobService",
            "matchService",
            "knowledgeService",
            "conversationService",
            "careerReportService",
            "aiService"
    };

    private static final String[] INTERNAL_LAYERS = {
            "mapper",
            "entity",
            "service.impl",
            "support"
    };

    @ArchTest
    static final ArchRule common_does_not_depend_on_business_modules = FreezingArchRule.freeze(
            noClasses()
                    .that().resideInAPackage("com.zdmj.common..")
                    .should().dependOnClassesThat().resideInAnyPackage(modulePackages())
                    .as("common 不依赖业务模块"));

    @ArchTest
    static final ArchRule controllers_do_not_depend_on_mappers = FreezingArchRule.freeze(
            noClasses()
                    .that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..mapper..")
                    .as("Controller 不直接依赖 Mapper"));

    @ArchTest
    static final ArchRule user_auth_does_not_access_foreign_internals = freezeForeignInternals("userAuthService");

    @ArchTest
    static final ArchRule resume_does_not_access_foreign_internals = freezeForeignInternals("resumeService");

    @ArchTest
    static final ArchRule job_does_not_access_foreign_internals = freezeForeignInternals("jobService");

    @ArchTest
    static final ArchRule match_does_not_access_foreign_internals = freezeForeignInternals("matchService");

    @ArchTest
    static final ArchRule knowledge_does_not_access_foreign_internals = freezeForeignInternals("knowledgeService");

    @ArchTest
    static final ArchRule conversation_does_not_access_foreign_internals = freezeForeignInternals("conversationService");

    @ArchTest
    static final ArchRule career_report_does_not_access_foreign_internals = freezeForeignInternals("careerReportService");

    @ArchTest
    static final ArchRule ai_does_not_access_foreign_internals = freezeForeignInternals("aiService");

    @ArchTest
    static final ArchRule common_mappers_stay_inside_common = freezeMapperAccess("common");

    @ArchTest
    static final ArchRule user_auth_mappers_stay_inside_module = freezeMapperAccess("userAuthService");

    @ArchTest
    static final ArchRule resume_mappers_stay_inside_module = freezeMapperAccess("resumeService");

    @ArchTest
    static final ArchRule job_mappers_stay_inside_module = freezeMapperAccess("jobService");

    @ArchTest
    static final ArchRule match_mappers_stay_inside_module = freezeMapperAccess("matchService");

    @ArchTest
    static final ArchRule knowledge_mappers_stay_inside_module = freezeMapperAccess("knowledgeService");

    @ArchTest
    static final ArchRule conversation_mappers_stay_inside_module = freezeMapperAccess("conversationService");

    @ArchTest
    static final ArchRule career_report_mappers_stay_inside_module = freezeMapperAccess("careerReportService");

    @ArchTest
    static final ArchRule modules_are_free_of_cycles = FreezingArchRule.freeze(
            slices().matching("com.zdmj.(*)..")
                    .should().beFreeOfCycles()
                    .as("模块之间不存在循环依赖"));

    private static ArchRule freezeForeignInternals(String module) {
        return FreezingArchRule.freeze(noClasses()
                .that().resideInAPackage("com.zdmj." + module + "..")
                .should().dependOnClassesThat().resideInAnyPackage(foreignInternals(module))
                .as(module + " 不访问其他模块的 Mapper、Entity、实现类和适配层"));
    }

    private static ArchRule freezeMapperAccess(String module) {
        String mapperPackage = "common".equals(module)
                ? "com.zdmj.common..mapper.."
                : "com.zdmj." + module + ".mapper..";
        String[] allowed = "common".equals(module)
                ? new String[] {"com.zdmj.common.."}
                : new String[] {
                        "com.zdmj." + module + ".mapper..",
                        "com.zdmj." + module + ".service..",
                        "com.zdmj." + module + ".support.."
                };
        return FreezingArchRule.freeze(classes()
                .that().resideInAPackage(mapperPackage)
                .should().onlyBeAccessed().byAnyPackage(allowed)
                .as(module + " 的 Mapper 只由本模块应用服务或适配层访问"));
    }

    private static String[] modulePackages() {
        String[] packages = new String[MODULES.length];
        for (int i = 0; i < MODULES.length; i++) {
            packages[i] = "com.zdmj." + MODULES[i] + "..";
        }
        return packages;
    }

    private static String[] foreignInternals(String module) {
        int count = 0;
        for (String other : MODULES) {
            if (!other.equals(module)) {
                count++;
            }
        }
        String[] packages = new String[count * INTERNAL_LAYERS.length];
        int index = 0;
        for (String other : MODULES) {
            if (other.equals(module)) {
                continue;
            }
            for (String layer : INTERNAL_LAYERS) {
                packages[index++] = "com.zdmj." + other + "." + layer + "..";
            }
        }
        return packages;
    }
}
