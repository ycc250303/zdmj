package com.zdmj.userAuthService.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.userAuthService.entity.User;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SensitiveFieldOutputTest {
    @Test
    void generatedStringExcludesCredentials() throws Exception {
        Object[][] fields = {{User.class, "password"},
                {UserLoginRequest.class, "password"}, {UserRegisterRequest.class, "password"},
                {UserRegisterRequest.class, "verificationCode"}, {UserResetPasswordRequest.class, "newPassword"},
                {UserResetPasswordRequest.class, "verificationCode"}, {UserLoginResponse.class, "token"}};
        for (Object[] entry : fields) {
            Class<?> type = (Class<?>) entry[0];
            String name = (String) entry[1];
            Object object = type.getConstructor().newInstance();
            set(object, name, "credential-secret");
            assertFalse(object.toString().contains("credential-secret"), type.getSimpleName() + "." + name);
        }
    }

    @Test
    void loginTokenRemainsInHttpResponseAndPasswordDeserializes() throws Exception {
        var mapper = new ObjectMapper();
        var response = new UserLoginResponse();
        response.setToken("response-token");
        assertEquals("response-token", mapper.readTree(mapper.writeValueAsString(response)).get("token").asText());
        assertEquals("request-password", mapper.readValue("{\"password\":\"request-password\"}", UserLoginRequest.class).getPassword());
    }

    private static void set(Object object, String name, String value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
