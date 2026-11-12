package com.zdmj.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.ProblemDetailHttpWriter;
import com.zdmj.common.security.JwtSessionStore;
import com.zdmj.common.security.JwtTokenService;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtSessionStore sessionStore;
    @Mock private JwtTokenService tokenService;
    @Mock private ProblemDetailHttpWriter problemWriter;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(sessionStore, tokenService, problemWriter);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 认证成功_建立受约束身份_交给后续请求链() throws Exception {
        when(tokenService.validateToken("token")).thenReturn(true);
        when(tokenService.getUserIdFromToken("token")).thenReturn(8L);
        when(tokenService.getUsernameFromToken("token")).thenReturn("user");
        when(sessionStore.find(8L)).thenReturn(Optional.of("token"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication.isAuthenticated()).isTrue();
            assertThat(authentication.getPrincipal()).isEqualTo(new CurrentActor(8L));
            assertThat(authentication.getCredentials()).isNull();
        });
    }

    @Test
    void 认证令牌_用户编号非正数_不建立身份且不查询会话() throws Exception {
        when(tokenService.validateToken("token")).thenReturn(true);
        when(tokenService.getUserIdFromToken("token")).thenReturn(-1L);
        when(tokenService.getUsernameFromToken("token")).thenReturn("user");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "token");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());

        verify(sessionStore, never()).find(anyLong());
    }
}
