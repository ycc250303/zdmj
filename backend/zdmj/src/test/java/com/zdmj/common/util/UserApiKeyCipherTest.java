package com.zdmj.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserApiKeyCipherTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void createEncryptor_missingKey_shouldFail() {
        BusinessException ex = assertThrows(BusinessException.class, () -> UserApiKeyCipher.createEncryptor(null));
        assertEquals(ErrorCode.USER_LLM_API_KEY_DECRYPT_FAILED.getCode(), ex.getCode());
        assertThrows(BusinessException.class, () -> UserApiKeyCipher.createEncryptor("  "));
    }

    @Test
    void createEncryptor_invalidHex_shouldFail() {
        BusinessException odd = assertThrows(BusinessException.class,
                () -> UserApiKeyCipher.createEncryptor("abc"));
        assertEquals(ErrorCode.USER_LLM_API_KEY_DECRYPT_FAILED.getCode(), odd.getCode());
        assertThrows(BusinessException.class, () -> UserApiKeyCipher.createEncryptor("not-hex-zz"));
    }

    @Test
    void encryptDecrypt_shouldRoundTrip() {
        TextEncryptor encryptor = UserApiKeyCipher.createEncryptor(KEY);
        String cipher = UserApiKeyCipher.encrypt(encryptor, "sk-test-secret");
        assertNotEquals("sk-test-secret", cipher);
        assertEquals("sk-test-secret", UserApiKeyCipher.decrypt(encryptor, cipher));
    }

    @Test
    void mask_shouldHideMiddle() {
        assertEquals("", UserApiKeyCipher.mask(null));
        assertEquals("****", UserApiKeyCipher.mask("short"));
        String masked = UserApiKeyCipher.mask("sk-abcdefghijklmn");
        assertTrue(masked.startsWith("sk-"));
        assertTrue(masked.contains("****"));
    }
}
