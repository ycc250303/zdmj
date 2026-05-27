package com.zdmj.userAuthService.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private static final String TEST_JWT_SECRET =
            "test-jwt-secret-key-for-jwt-token-generation-2024-very-long-secret-key";

    @BeforeAll
    static void initSecret() {
        JwtUtil.initSecret(TEST_JWT_SECRET);
    }

    @Test
    void generateParseValidateAndGetExpiration_shouldWorkAsExpected() {
        Long userId = 1001L;
        String username = "alice";

        String token = JwtUtil.generateToken(userId, username);
        Long parsedUserId = JwtUtil.getUserIdFromToken(token);
        String parsedUsername = JwtUtil.getUsernameFromToken(token);
        Date expiration = JwtUtil.getExpirationDateFromToken(token);

        assertEquals(3, token.split("\\.").length);
        assertEquals(userId, parsedUserId);
        assertEquals(username, parsedUsername);
        assertEquals(true, expiration.getTime() > System.currentTimeMillis());
        assertTrue(JwtUtil.validateToken(token));
    }

    @Test
    void invalidToken_shouldReturnNullOrFalse() {
        String invalidToken = "invalid.jwt.token";

        assertNull(JwtUtil.getClaimsFromToken(invalidToken));
        assertNull(JwtUtil.getUserIdFromToken(invalidToken));
        assertNull(JwtUtil.getUsernameFromToken(invalidToken));
        assertFalse(JwtUtil.validateToken(invalidToken));
    }
}
