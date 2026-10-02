package com.zdmj.common.config;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;

class RequestContextCleanupFilterTest {

    @AfterEach
    void clear() {
        UserHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void downstreamThrows_shouldStillClearBothContexts() {
        RequestContextCleanupFilter filter = new RequestContextCleanupFilter();
        UserHolder.set(UserContext.of(999L, "stale"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("stale", null));

        assertThrows(RuntimeException.class, () -> filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, response) -> {
                    throw new RuntimeException("boom");
                }));

        assertNull(UserHolder.getUserId());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
