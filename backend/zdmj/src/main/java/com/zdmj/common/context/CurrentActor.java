package com.zdmj.common.context;

/**
 * 当前操作者。
 *
 * @param userId 用户主键
 */
public record CurrentActor(Long userId) {

    /**
     * 用用户主键构造操作者。
     *
     * @param userId 用户主键，允许为空，由调用方决定是否拒绝
     * @return 操作者
     */
    public static CurrentActor of(Long userId) {
        return new CurrentActor(userId);
    }
}
