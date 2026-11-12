package com.zdmj.common.context;

/**
 * 当前操作者。
 *
 * @param userId 用户主键，必须为正数
 */
public record CurrentActor(Long userId) {

    public CurrentActor {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 必须为正数");
        }
    }
}
