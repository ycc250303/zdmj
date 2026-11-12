package com.zdmj.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

class CurrentActorArgumentResolverTest {

    private final CurrentActorArgumentResolver resolver = new CurrentActorArgumentResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 参数解析_已认证身份_返回同一身份且仅支持身份参数() throws Exception {
        CurrentActor actor = new CurrentActor(8L);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actor, null, List.of()));
        MethodParameter parameter = parameter(0);
        assertThat(resolver.supportsParameter(parameter)).isTrue();
        assertThat(resolver.supportsParameter(parameter(1))).isFalse();
        assertThat(resolver.resolveArgument(parameter, null, null, null)).isSameAs(actor);
    }

    @Test
    void 参数解析_缺少认证或匿名身份_返回未登录() {
        assertNotLoggedIn();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated(new CurrentActor(8L), null));
        assertNotLoggedIn();
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "anonymous", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertNotLoggedIn();
    }

    @Test
    void 参数解析_已认证主体类型错误_暴露配置缺陷() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("unexpected", null, List.of()));
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    private void assertNotLoggedIn() {
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.USER_NOT_LOGIN.getCode());
    }

    private static MethodParameter parameter(int index) throws Exception {
        return new MethodParameter(CurrentActorArgumentResolverTest.class
                .getDeclaredMethod("endpoint", CurrentActor.class, String.class), index);
    }

    private void endpoint(CurrentActor actor, String text) {
    }
}
