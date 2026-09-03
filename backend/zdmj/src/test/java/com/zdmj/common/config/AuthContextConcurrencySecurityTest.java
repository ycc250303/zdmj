package com.zdmj.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.ProblemDetailHttpWriter;
import com.zdmj.common.security.JwtSessionStore;
import com.zdmj.userAuthService.util.JwtUtil;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthContextConcurrencySecurityTest {

    private static final String TEST_JWT_SECRET =
            "test-jwt-secret-key-for-jwt-token-generation-2024-very-long-secret-key";

    @Mock
    private JwtSessionStore jwtSessionStore;

    private ProblemDetailHttpWriter problemDetailHttpWriter;

    @BeforeAll
    static void initJwtSecret() {
        JwtUtil.initSecret(TEST_JWT_SECRET);
    }

    @BeforeEach
    void setUpWriter() {
        problemDetailHttpWriter = new ProblemDetailHttpWriter(new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
        SecurityContextHolder.clearContext();
    }

    private JwtAuthenticationFilter jwtFilter() {
        return new JwtAuthenticationFilter(jwtSessionStore, problemDetailHttpWriter);
    }

    @Test
    void validTokenAndRedisMatch_shouldAuthenticateAndCleanupAfterRequest()
            throws ServletException, IOException {
        JwtAuthenticationFilter jwtFilter = jwtFilter();
        RequestContextCleanupFilter cleanupFilter = new RequestContextCleanupFilter();

        Long userId = 1001L;
        String username = "alice";
        String token = JwtUtil.generateToken(userId, username);
        lenient().when(jwtSessionStore.find(userId)).thenReturn(Optional.of(token));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        cleanupFilter.doFilter(request, response, (req1, resp1) ->
                jwtFilter.doFilter(req1, resp1, (req2, resp2) -> {
                    assertEquals(userId, UserHolder.getUserId());
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    assertNotNull(authentication);
                    assertEquals(true, authentication.getPrincipal() instanceof UserContext);
                    UserContext principal = (UserContext) authentication.getPrincipal();
                    assertEquals(userId, principal.getUserId());
                    assertEquals(username, principal.getUsername());
                }));

        assertNull(UserHolder.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void validTokenButRedisMismatch_shouldNotAuthenticate()
            throws ServletException, IOException {
        JwtAuthenticationFilter jwtFilter = jwtFilter();
        RequestContextCleanupFilter cleanupFilter = new RequestContextCleanupFilter();

        Long userId = 1002L;
        String token = JwtUtil.generateToken(userId, "bob");
        lenient().when(jwtSessionStore.find(userId)).thenReturn(Optional.of("another-token"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        cleanupFilter.doFilter(request, response, (req1, resp1) ->
                jwtFilter.doFilter(req1, resp1, (req2, resp2) -> {
                    assertNull(UserHolder.get());
                    assertNull(SecurityContextHolder.getContext().getAuthentication());
                }));
    }

    @Test
    void redisUnavailable_shouldWrite503AndNotContinueChain() throws ServletException, IOException {
        JwtAuthenticationFilter jwtFilter = jwtFilter();

        Long userId = 1004L;
        String token = JwtUtil.generateToken(userId, "dave");
        when(jwtSessionStore.find(userId)).thenThrow(new RedisConnectionFailureException("down"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean continued = new AtomicBoolean(false);
        jwtFilter.doFilter(request, response, (req, resp) -> continued.set(true));

        assertFalse(continued.get());
        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("1012"));
        assertNull(UserHolder.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void sameThreadSequentialRequests_shouldNotLeakPreviousUser()
            throws ServletException, IOException {
        JwtAuthenticationFilter jwtFilter = jwtFilter();
        RequestContextCleanupFilter cleanupFilter = new RequestContextCleanupFilter();

        Long userId = 1003L;
        String token = JwtUtil.generateToken(userId, "charlie");
        lenient().when(jwtSessionStore.find(userId)).thenReturn(Optional.of(token));

        MockHttpServletRequest req1 = new MockHttpServletRequest();
        req1.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        cleanupFilter.doFilter(req1, resp1, (r1, p1) ->
                jwtFilter.doFilter(r1, p1, (r2, p2) -> assertEquals(userId, UserHolder.getUserId())));

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        cleanupFilter.doFilter(req2, resp2, (r1, p1) ->
                jwtFilter.doFilter(r1, p1, (r2, p2) -> {
                    assertNull(UserHolder.getUserId());
                    assertNull(SecurityContextHolder.getContext().getAuthentication());
                }));
    }

    @Test
    void concurrentMixedRequests_shouldNeverCrossUserContext() throws Exception {
        JwtAuthenticationFilter jwtFilter = jwtFilter();
        RequestContextCleanupFilter cleanupFilter = new RequestContextCleanupFilter();

        String tokenU1 = JwtUtil.generateToken(2001L, "u1");
        String tokenU2 = JwtUtil.generateToken(2002L, "u2");
        lenient().when(jwtSessionStore.find(2001L)).thenReturn(Optional.of(tokenU1));
        lenient().when(jwtSessionStore.find(2002L)).thenReturn(Optional.of(tokenU2));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            int total = 120;
            AtomicInteger failures = new AtomicInteger(0);
            List<Callable<Void>> tasks = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                final int index = i;
                tasks.add(() -> {
                    MockHttpServletRequest request = new MockHttpServletRequest();
                    if (index % 3 == 0) {
                        request.addHeader("Authorization", "Bearer " + tokenU1);
                    } else if (index % 3 == 1) {
                        request.addHeader("Authorization", "Bearer " + tokenU2);
                    }
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    cleanupFilter.doFilter(request, response, (req1, resp1) ->
                            jwtFilter.doFilter(req1, resp1, (req2, resp2) -> {
                                Long uid = UserHolder.getUserId();
                                if (index % 3 == 0 && !Long.valueOf(2001L).equals(uid)) {
                                    failures.incrementAndGet();
                                } else if (index % 3 == 1 && !Long.valueOf(2002L).equals(uid)) {
                                    failures.incrementAndGet();
                                } else if (index % 3 == 2 && uid != null) {
                                    failures.incrementAndGet();
                                }
                            }));

                    if (UserHolder.getUserId() != null
                            || SecurityContextHolder.getContext().getAuthentication() != null) {
                        failures.incrementAndGet();
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = pool.invokeAll(tasks);
            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    throw new RuntimeException("并发认证测试出现异常", e.getCause());
                }
            }

            assertEquals(0, failures.get(), "并发请求下出现用户上下文串扰");
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void cleanupFilter_shouldClearContextEvenWhenDownstreamThrows() {
        RequestContextCleanupFilter cleanupFilter = new RequestContextCleanupFilter();
        UserHolder.set(UserContext.of(999L, "stale"));
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "stale", null, List.of()));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(RuntimeException.class, () ->
                cleanupFilter.doFilter(request, response, (req, resp) -> {
                    throw new RuntimeException("boom");
                }));

        assertNull(UserHolder.getUserId());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
