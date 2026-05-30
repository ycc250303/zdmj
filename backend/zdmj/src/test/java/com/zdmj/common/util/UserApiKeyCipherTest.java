package com.zdmj.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import com.zdmj.common.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserApiKeyCipherTest {

    private static final String TEST_HEX_KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void encryptDecrypt_roundTrip() {
        TextEncryptor encryptor = UserApiKeyCipher.createEncryptor(TEST_HEX_KEY, false);
        String plain = "sk-test-api-key-12345678";

        String ciphertext = UserApiKeyCipher.encrypt(encryptor, plain);
        String decrypted = UserApiKeyCipher.decrypt(encryptor, ciphertext);

        assertNotEquals(plain, ciphertext);
        assertEquals(plain, decrypted);
    }

    @Test
    void mask_showsPrefixAndSuffix() {
        assertEquals("sk-****5678", UserApiKeyCipher.mask("sk-test-api-key-5678"));
        assertEquals("****", UserApiKeyCipher.mask("short"));
        assertEquals("", UserApiKeyCipher.mask(""));
    }

    @Test
    void createEncryptor_requiresKeyWhenConfigured() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> UserApiKeyCipher.createEncryptor("", true));
        assertTrue(ex.getMessage().contains("APP_AI_USER_KEY_ENCRYPTION_KEY"));
    }
}
