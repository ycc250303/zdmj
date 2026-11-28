package com.zdmj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

/** 严格校验模块公开接口、持久化访问和依赖方向。 */
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

    @ArchTest
    static final ArchRule conversation_stream_has_no_manual_subscription = noClasses()
            .that().resideInAPackage("com.zdmj.conversationService..")
            .should().callMethodWhere(com.tngtech.archunit.base.DescribedPredicate.describe("手工订阅 Reactor 流",
                    call -> call.getTargetOwner().getPackageName().startsWith("reactor.")
                            && (call.getName().equals("subscribe") || call.getName().equals("subscribeWith"))));

    @ArchTest
    static final ArchRule business_receives_identity_explicitly = noClasses()
            .that().resideInAnyPackage("..service..", "..support..", "..controller..",
                    "com.zdmj.common.context..", "com.zdmj.common.storage..", "com.zdmj.common.util..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "org.springframework.security.core.context.SecurityContextHolder")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.ThreadLocal")
            .orShould().dependOnClassesThat().haveFullyQualifiedName(
                    "org.springframework.web.context.request.RequestContextHolder");

    @ArchTest
    static final ArchRule user_auth_does_not_depend_on_ai = noClasses()
            .that().resideInAPackage("com.zdmj.userAuthService..")
            .should().dependOnClassesThat().resideInAPackage("com.zdmj.aiService..");

    @ArchTest
    static final ArchRule ai_does_not_depend_on_user_auth = noClasses()
            .that().resideInAPackage("com.zdmj.aiService..")
            .should().dependOnClassesThat().resideInAPackage("com.zdmj.userAuthService..");

    @ArchTest
    static final ArchRule ai_mappers_stay_inside_module = classes()
            .that().resideInAPackage("com.zdmj.aiService.mapper..")
            .should().onlyBeAccessed().byAnyPackage("com.zdmj.aiService.mapper..",
                    "com.zdmj.aiService.service..", "com.zdmj.aiService.support..");

    @ArchTest
    static final ArchRule ai_does_not_depend_on_job_types = noClasses()
            .that().resideInAPackage("com.zdmj.aiService..")
            .should().dependOnClassesThat().resideInAPackage("com.zdmj.jobService..");

    @ArchTest
    static final ArchRule embedding_model_stays_inside_ai = noClasses()
            .that().resideOutsideOfPackage("com.zdmj.aiService..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "org.springframework.ai.embedding.EmbeddingModel")
            .as("只有 aiService 依赖 EmbeddingModel");

    @ArchTest
    static final ArchRule prompt_loader_does_not_depend_on_business = noClasses()
            .that().resideInAPackage("com.zdmj.common.prompt..")
            .should().dependOnClassesThat().resideInAnyPackage(modulePackages());

    @ArchTest
    static final ArchRule role_detection_implementation_stays_inside_job = noClasses()
            .that().resideOutsideOfPackage("com.zdmj.jobService..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.zdmj.jobService.service.impl.JobRoleDetectionServiceImpl");

    @ArchTest
    static final ArchRule common_does_not_depend_on_business_modules = noClasses()
            .that().resideInAPackage("com.zdmj.common..")
            .should().dependOnClassesThat().resideInAnyPackage(modulePackages())
            .as("common 不依赖业务模块");

    @ArchTest
    static final ArchRule controllers_do_not_depend_on_mappers = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..mapper..")
            .as("Controller 不直接依赖 Mapper");

    @ArchTest
    static final ArchRule user_auth_does_not_access_foreign_internals = foreignInternalsRule("userAuthService");

    @ArchTest
    static final ArchRule resume_does_not_access_foreign_internals = foreignInternalsRule("resumeService");

    @ArchTest
    static final ArchRule job_does_not_access_foreign_internals = foreignInternalsRule("jobService");

    @ArchTest
    static final ArchRule match_does_not_access_foreign_internals = foreignInternalsRule("matchService");

    @ArchTest
    static final ArchRule knowledge_does_not_access_foreign_internals = foreignInternalsRule("knowledgeService");

    @ArchTest
    static final ArchRule conversation_does_not_access_foreign_internals = foreignInternalsRule("conversationService");

    @ArchTest
    static final ArchRule career_report_does_not_access_foreign_internals = foreignInternalsRule("careerReportService");

    @ArchTest
    static final ArchRule ai_does_not_access_foreign_internals = foreignInternalsRule("aiService");

    @ArchTest
    static final ArchRule user_auth_mappers_stay_inside_module = mapperAccessRule("userAuthService");

    @ArchTest
    static final ArchRule resume_mappers_stay_inside_module = mapperAccessRule("resumeService");

    @ArchTest
    static final ArchRule job_mappers_stay_inside_module = mapperAccessRule("jobService");

    @ArchTest
    static final ArchRule match_mappers_stay_inside_module = mapperAccessRule("matchService");

    @ArchTest
    static final ArchRule knowledge_mappers_stay_inside_module = classes()
            .that().resideInAPackage("com.zdmj.knowledgeService.mapper..")
            .should().onlyBeAccessed().byAnyPackage("com.zdmj.knowledgeService.mapper..",
                    "com.zdmj.knowledgeService.service..", "com.zdmj.knowledgeService.support..");

    @ArchTest
    static final ArchRule conversation_mappers_stay_inside_module = mapperAccessRule("conversationService");

    @ArchTest
    static final ArchRule career_report_mappers_stay_inside_module = mapperAccessRule("careerReportService");

    @ArchTest
    static final ArchRule modules_are_free_of_cycles = slices().matching("com.zdmj.(*)..")
            .should().beFreeOfCycles()
            .as("模块之间不存在循环依赖");

    @ArchTest
    static void public_interfaces_hide_internal_types(com.tngtech.archunit.core.domain.JavaClasses classes) {
        for (String module : MODULES) {
            publicInterfaceRule(module).check(classes);
        }
    }

    static ArchRule foreignInternalsRule(String module) {
        return noClasses()
                .that().resideInAPackage("com.zdmj." + module + "..")
                .should().dependOnClassesThat(DescribedPredicate.describe("其他模块的内部类型", target -> {
                    for (String other : MODULES) {
                        if (!other.equals(module) && isInternal(target, other)) {
                            return true;
                        }
                    }
                    return false;
                }))
                .as(module + " 仅通过其他模块的 api 协作");
    }

    /** 尚无公开能力的模块允许不存在 api 类型；一旦存在即检查全部公开类型。 */
    static ArchRule publicInterfaceRule(String module) {
        return noClasses()
                .that().resideInAPackage("com.zdmj." + module + ".api..")
                .should().dependOnClassesThat(DescribedPredicate.describe("本模块的内部类型",
                        target -> isInternal(target, module)))
                .allowEmptyShould(true)
                .as(module + " 的 api 不泄漏内部类型");
    }

    private static boolean isInternal(JavaClass target, String module) {
        String prefix = "com.zdmj." + module + ".";
        return (target.getPackageName().equals("com.zdmj." + module) || target.getPackageName().startsWith(prefix))
                && !target.getPackageName().equals(prefix + "api")
                && !target.getPackageName().startsWith(prefix + "api.");
    }

    private static ArchRule mapperAccessRule(String module) {
        String mapperPackage = "com.zdmj." + module + ".mapper..";
        return classes()
                .that().resideInAPackage(mapperPackage)
                .should().onlyBeAccessed().byAnyPackage(mapperPackage,
                        "com.zdmj." + module + ".service..", "com.zdmj." + module + ".support..")
                .as(module + " 的 Mapper 只由本模块应用服务或适配层访问");
    }

    private static String[] modulePackages() {
        String[] packages = new String[MODULES.length];
        for (int i = 0; i < MODULES.length; i++) {
            packages[i] = "com.zdmj." + MODULES[i] + "..";
        }
        return packages;
    }

}
