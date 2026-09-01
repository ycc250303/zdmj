package com.zdmj.common.storage;

/**
 * 用户域 COS 对象键约定：{@code user-{id}/{bizArea}/{file}}。
 * <p>用路径第一段精确等于 {@code user-{id}}，避免 {@code user-1} 误匹配 {@code user-10}；
 * 拒绝 {@code ..} / 空段等路径穿越形态。
 */
public final class UserObjectKeys {

    private UserObjectKeys() {
    }

    public static String ownedPrefix(long userId) {
        return "user-" + userId + "/";
    }

    /**
     * 规范化对象键；非法（空、含 {@code ..}、空段）返回 {@code null}。
     */
    public static String normalize(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String raw = key.replace('\\', '/').trim();
        while (raw.startsWith("/")) {
            raw = raw.substring(1);
        }
        if (raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("/");
        if (parts.length < 2) {
            return null;
        }
        for (String part : parts) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)) {
                return null;
            }
        }
        return String.join("/", parts);
    }

    public static boolean isOwnedBy(String key, long userId) {
        String normalized = normalize(key);
        if (normalized == null) {
            return false;
        }
        int slash = normalized.indexOf('/');
        if (slash <= 0) {
            return false;
        }
        return ("user-" + userId).equals(normalized.substring(0, slash));
    }
}
