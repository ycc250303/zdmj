package com.zdmj.userAuthService.support;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.userAuthService.enums.VerificationCodeScene;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VerificationCodeSceneResolverTest {

    @Test
    void parseOptionalType_whenNull_shouldReturnNull() {
        assertNull(VerificationCodeSceneResolver.parseOptionalType(null));
    }

    @Test
    void parseOptionalType_whenRegister_shouldReturnRegister() {
        assertEquals(VerificationCodeScene.REGISTER, VerificationCodeSceneResolver.parseOptionalType("register"));
    }

    @Test
    void parseOptionalType_whenBlank_shouldThrow1001() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> VerificationCodeSceneResolver.parseOptionalType("  "));
        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
    }

    @Test
    void parseOptionalType_whenInvalid_shouldThrow1001() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> VerificationCodeSceneResolver.parseOptionalType("invalid"));
        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
    }
}
