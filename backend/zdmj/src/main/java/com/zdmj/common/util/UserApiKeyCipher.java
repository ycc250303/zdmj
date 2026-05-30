package com.zdmj.common.util;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.util.StringUtils;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/**
 * 用户 LLM API Key 对称加密（Spring Security Encryptors.text）。
 */
public class UserApiKeyCipher {
    /** Spring Encryptors.text 要求 password/salt 均为偶数位 hex 字符串 */
    private static final String SALT = "7a646d6a757365726c6c6d00";

    private UserApiKeyCipher() {
    }

    /**
     * 创建加密器
     * @param configuredKey
     * @param requireKey
     * @return
     */
    public static TextEncryptor createEncryptor(String configuredKey, boolean requireKey) {
        String password = resolvePassword(configuredKey, requireKey);
        return Encryptors.text(password, SALT);

    }

    /**
     * 加密 API Key
     * @param encryptor
     * @param plainText
     * @return
     */
    public static String encrypt(TextEncryptor encryptor, String plainText) {
        try {
            return encryptor.encrypt(plainText);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.USER_LLM_API_KEY_ENCRYPT_FAILED, e);
        }
    }

    /**
     * 解密 API Key
     * @param encryptor
     * @param ciphertext
     * @return
     */
    public static String decrypt(TextEncryptor encryptor, String ciphertext) {
        try {
            return encryptor.decrypt(ciphertext);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.USER_LLM_API_KEY_DECRYPT_FAILED, e);
        }
    }

    /**
     * 掩码 API Key
     * @param apiKey
     * @return
     */
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

    /**
     * 解析密码
     * @param configuredKey
     * @param requireKey
     * @return
     */
    private static String resolvePassword(String configuredKey, boolean requireKey) {
        if (StringUtils.hasText(configuredKey)) {
            return configuredKey.trim();
        }
        if (requireKey) {
            throw new BusinessException(
                    ErrorCode.USER_LLM_API_KEY_DECRYPT_FAILED.getCode(),
                    "APP_AI_USER_KEY_ENCRYPTION_KEY 未配置（须为偶数位 hex 字符串）");
        }
        return "0123456789abcdef0123456789abcdef";
    }
}
