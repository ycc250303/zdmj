package com.zdmj.testsupport;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.dao.DataAccessResourceFailureException;

import com.zdmj.common.security.JwtSessionStore;

/**
 * 委托真实登录 allowlist。测试可将 {@link #find(long)} 切换为存储不可用，用于固定 503 契约。
 */
public class ToggleJwtSessionStore implements JwtSessionStore {

    private final JwtSessionStore delegate;
    private final AtomicBoolean unavailable = new AtomicBoolean(false);

    public ToggleJwtSessionStore(JwtSessionStore delegate) {
        this.delegate = delegate;
    }

    /**
     * 后续 {@link #find(long)} 抛出 {@link org.springframework.dao.DataAccessException}。
     */
    public void markUnavailable() {
        unavailable.set(true);
    }

    /**
     * 恢复为委托实现的真实查询。
     */
    public void markAvailable() {
        unavailable.set(false);
    }

    @Override
    public void replace(long userId, String token) {
        delegate.replace(userId, token);
    }

    @Override
    public Optional<String> find(long userId) {
        if (unavailable.get()) {
            throw new DataAccessResourceFailureException("toggle-store-down");
        }
        return delegate.find(userId);
    }

    @Override
    public void revoke(long userId) {
        delegate.revoke(userId);
    }
}
