package com.zdmj.common.util;

import org.junit.jupiter.api.Test;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserApiKeyCipherTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void constructor_missingKey_shouldFail() {
        BusinessException ex = assertThrows(BusinessException.class, () -> new UserApiKeyCipher(null));
        assertEquals(ErrorCode.USER_LLM_API_KEY_DECRYPT_FAILED.getCode(), ex.getCode());
        assertThrows(BusinessException.class, () -> new UserApiKeyCipher("  "));
    }

    @Test
    void constructor_invalidHex_shouldFail() {
        BusinessException odd = assertThrows(BusinessException.class,
                () -> new UserApiKeyCipher("abc"));
        assertEquals(ErrorCode.USER_LLM_API_KEY_DECRYPT_FAILED.getCode(), odd.getCode());
        assertThrows(BusinessException.class, () -> new UserApiKeyCipher("not-hex-zz"));
    }

    @Test
    void encryptDecrypt_shouldRoundTrip() {
        UserApiKeyCipher cipher = new UserApiKeyCipher(KEY);
        String ciphertext = cipher.encrypt("sk-test-secret");
        assertNotEquals("sk-test-secret", ciphertext);
        assertEquals("sk-test-secret", cipher.decrypt(ciphertext));
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
