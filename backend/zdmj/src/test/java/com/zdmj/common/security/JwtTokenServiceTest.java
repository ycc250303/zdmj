package com.zdmj.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final String TEST_JWT_SECRET =
            "test-jwt-secret-key-for-jwt-token-generation-2024-very-long-secret-key";

    private final JwtTokenService jwtTokenService = new JwtTokenService(TEST_JWT_SECRET);

    @Test
    void generateParseValidateAndGetExpiration_shouldWorkAsExpected() {
        Long userId = 1001L;
        String username = "alice";

        String token = jwtTokenService.generateToken(userId, username);
        Long parsedUserId = jwtTokenService.getUserIdFromToken(token);
        String parsedUsername = jwtTokenService.getUsernameFromToken(token);
        Date expiration = jwtTokenService.getExpirationDateFromToken(token);

        assertEquals(3, token.split("\\.").length);
        assertEquals(userId, parsedUserId);
        assertEquals(username, parsedUsername);
        assertEquals(true, expiration.getTime() > System.currentTimeMillis());
        assertTrue(jwtTokenService.validateToken(token));
    }

    @Test
    void invalidToken_shouldReturnNullOrFalse() {
        String invalidToken = "invalid.jwt.token";

        assertNull(jwtTokenService.getClaimsFromToken(invalidToken));
        assertNull(jwtTokenService.getUserIdFromToken(invalidToken));
        assertNull(jwtTokenService.getUsernameFromToken(invalidToken));
        assertFalse(jwtTokenService.validateToken(invalidToken));
    }
}
