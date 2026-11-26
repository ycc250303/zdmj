package com.zdmj.userAuthService.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.userAuthService.entity.User;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SensitiveFieldOutputTest {
    @Test
    void generatedStringExcludesCredentials() {
        var user = new User();
        user.setPassword("password-secret");
        Object[] values = {user,
                new UserLoginRequest("alice", "password-secret"),
                new UserRegisterRequest("alice", "password-secret", "alice@test.com", "verification-secret"),
                new UserResetPasswordRequest("alice@test.com", "verification-secret", "new-password-secret"),
                new UserLoginResponse("token-secret", null)};
        for (Object value : values) {
            String output = value.toString();
            assertFalse(output.contains("password-secret"), value.getClass().getSimpleName());
            assertFalse(output.contains("verification-secret"), value.getClass().getSimpleName());
            assertFalse(output.contains("token-secret"), value.getClass().getSimpleName());
        }
    }

    @Test
    void loginTokenRemainsInHttpResponseAndPasswordDeserializes() throws Exception {
        var mapper = new ObjectMapper();
        var response = new UserLoginResponse("response-token", null);
        assertEquals("response-token", mapper.readTree(mapper.writeValueAsString(response)).get("token").asText());
        assertEquals("request-password", mapper.readValue("{\"password\":\"request-password\"}", UserLoginRequest.class).password());
    }
}
