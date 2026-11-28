package com.zdmj.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.zdmj.resumeService.api.InternalLeakFixture;
import com.zdmj.resumeService.service.ForeignInternalDependencyFixture;
import com.zdmj.resumeService.service.PublicDependencyFixture;
import org.junit.jupiter.api.Test;

class ModuleArchitectureRuleTest {
    @Test
    void 外部依赖_引用其他模块内部各层_全部拒绝() {
        var classes = new ClassFileImporter().importClasses(ForeignInternalDependencyFixture.class);
        var result = ModuleArchitectureTest.foreignInternalsRule("resumeService").evaluate(classes);
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anySatisfy(detail -> assertThat(detail).contains("JobRequest"))
                .anySatisfy(detail -> assertThat(detail).contains("JobService>"))
                .anySatisfy(detail -> assertThat(detail).contains("JobMapper"))
                .anySatisfy(detail -> assertThat(detail).contains("entity.Job"))
                .anySatisfy(detail -> assertThat(detail).contains("JobServiceImpl"))
                .anySatisfy(detail -> assertThat(detail).contains("JobEmploymentEnum"));
        assertThatThrownBy(() -> ModuleArchitectureTest.foreignInternalsRule("resumeService").check(classes))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void 公开接口_引用本模块内部响应_拒绝() {
        var classes = new ClassFileImporter().importClasses(InternalLeakFixture.class);
        assertThatThrownBy(() -> ModuleArchitectureTest.publicInterfaceRule("resumeService").check(classes))
                .isInstanceOf(AssertionError.class).hasMessageContaining("StudentCapabilityProfileResponse");
    }

    @Test
    void 外部依赖_只引用公开接口和公共身份_通过() {
        var classes = new ClassFileImporter().importClasses(PublicDependencyFixture.class);
        assertThatCode(() -> ModuleArchitectureTest.foreignInternalsRule("resumeService").check(classes))
                .doesNotThrowAnyException();
    }
}
