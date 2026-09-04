package com.zdmj.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/**
 * 用户 LLM API Key 对称加密（Spring Security Encryptors.text）。
 * <p>必须配置 {@code APP_AI_USER_KEY_ENCRYPTION_KEY}（偶数位 hex），无源码内默认口令。
 */
@Component
public class UserApiKeyCipher {

    /** Spring Encryptors.text 要求 password/salt 均为偶数位 hex 字符串 */
    private static final String SALT = "7a646d6a757365726c6c6d00";

    private final TextEncryptor textEncryptor;

    public UserApiKeyCipher(@Value("${app.ai.user-llm.encryption-key:}") String encryptionKey) {
        this.textEncryptor = Encryptors.text(resolvePassword(encryptionKey), SALT);
    }

    /**
     * 加密用户 API Key（落库前调用）
     *
     * @param plainText 明文 Key
     * @return 密文
     */
    public String encrypt(String plainText) {
        try {
            return textEncryptor.encrypt(plainText);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.USER_LLM_API_KEY_ENCRYPT_FAILED, e);
        }
    }

    /**
     * 解密用户 API Key（路由解析或回显掩码时调用）
     *
     * @param ciphertext 密文
     * @return 明文 Key
     */
    public String decrypt(String ciphertext) {
        try {
            return textEncryptor.decrypt(ciphertext);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.USER_LLM_API_KEY_DECRYPT_FAILED, e);
        }
    }

    public static String mask(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private static String resolvePassword(String configuredKey) {
        if (!StringUtils.hasText(configuredKey)) {
            throw new BusinessException(
                    ErrorCode.USER_LLM_API_KEY_DECRYPT_FAILED,
                    "APP_AI_USER_KEY_ENCRYPTION_KEY 未配置（须为偶数位 hex 字符串）");
        }
        String password = configuredKey.trim();
        if (!isEvenHex(password)) {
            throw new BusinessException(
                    ErrorCode.USER_LLM_API_KEY_DECRYPT_FAILED,
                    "APP_AI_USER_KEY_ENCRYPTION_KEY 须为偶数位 hex 字符串");
        }
        return password;
    }

    private static boolean isEvenHex(String value) {
        if (value.length() % 2 != 0) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
