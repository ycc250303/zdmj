package com.zdmj.userAuthService.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    @Test
    void generateToken_and_validate_shouldReturnTrueAndExtractClaims() {
        Long userId = 123L;
        String username = "jwt_user";

        String token = JwtUtil.generateToken(userId, username);

        assertNotNull(token);
        assertTrue(JwtUtil.validateToken(token));
        assertEquals(userId, JwtUtil.getUserIdFromToken(token));
        assertEquals(username, JwtUtil.getUsernameFromToken(token));
    }

    @Test
    void getClaimsFromToken_invalidToken_shouldReturnNull() {
        Claims claims = JwtUtil.getClaimsFromToken("invalid.jwt.token");
        assertNull(claims);
    }

    @Test
    void getUserIdFromToken_invalidToken_shouldReturnNull() {
        assertNull(JwtUtil.getUserIdFromToken("invalid.jwt.token"));
    }

    @Test
    void getUsernameFromToken_invalidToken_shouldReturnNull() {
        assertNull(JwtUtil.getUsernameFromToken("invalid.jwt.token"));
    }

    @Test
    void validateToken_invalidToken_shouldReturnFalse() {
        assertFalse(JwtUtil.validateToken("invalid.jwt.token"));
    }

    @Test
    void getExpirationDateFromToken_validToken_shouldReturnFutureDate() {
        String token = JwtUtil.generateToken(1L, "future_user");
        Date expiration = JwtUtil.getExpirationDateFromToken(token);
        assertNotNull(expiration);
        assertTrue(expiration.after(new Date()));
    }

    @Test
    void getExpirationDateFromToken_invalidToken_shouldReturnNull() {
        assertNull(JwtUtil.getExpirationDateFromToken("invalid.jwt.token"));
    }
}
