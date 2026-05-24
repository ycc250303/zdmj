package com.zdmj.common.model;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页入参规范化（page 从 1 开始，limit 默认 20、上限 100）。
 */
public final class PageRequests {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private PageRequests() {
    }

    /** 规范化后的 page / limit */
    public record Normalized(int page, int limit) {
    }

    public static Normalized normalize(Integer page, Integer limit) {
        return normalize(page, limit, MAX_LIMIT);
    }

    public static Normalized normalize(Integer page, Integer limit, int maxLimit) {
        int p = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int l = (limit == null || limit < 1) ? DEFAULT_LIMIT : Math.min(limit, maxLimit);
        return new Normalized(p, l);
    }

    public static <T> Page<T> toPage(Normalized normalized) {
        return new Page<>(normalized.page(), normalized.limit());
    }
}
