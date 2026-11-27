package com.zdmj.common.model;

/**
 * 规范化后的分页参数，由分页规则生成。
 * @param page 页码，从一开始
 * @param limit 每页数量，受调用方指定的数量上限限制
 */
public record PageParameters(int page, int limit) {
}
