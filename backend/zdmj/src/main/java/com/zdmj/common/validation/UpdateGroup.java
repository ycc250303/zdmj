package com.zdmj.common.validation;

/**
 * 更新操作的验证组
 * 用于标识更新时需要验证的字段
 * 
 * 使用方式：
 * 在Controller中使用 @Validated(UpdateGroup.class) 来启用更新时的验证规则
 * 更新时通常只验证提供的字段，允许部分更新
 */
public interface UpdateGroup {
}
