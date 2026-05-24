package com.zdmj.common.model;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一分页响应封装（避免直接暴露 Spring Data {@code Page} 的 pageable/sort 等字段）。
 *
 * @param <T> 列表元素类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageDTO<T> {

    /** 当前页数据 */
    private List<T> list;

    /** 总条数 */
    private long total;

    /**
     * 当前页码（从 1 开始，与常见 query 参数 {@code page} 一致）
     */
    private int page;

    /** 每页条数 */
    private int limit;

    /** 总页数 */
    private int totalPages;

    /**
     * 构建分页结果；{@code totalPages} 由 total、limit 计算（limit &lt;= 0 时为 0）。
     */
    public static <T> PageDTO<T> of(List<T> list, long total, int page, int limit) {
        int totalPages = limit > 0 ? (int) ((total + limit - 1) / limit) : 0;
        return new PageDTO<>(list, total, page, limit, totalPages);
    }

    /** 由 MyBatis-Plus {@link IPage} 构建（records 与 page 元素类型一致） */
    public static <T> PageDTO<T> from(IPage<T> page) {
        return of(page.getRecords(), page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /** 由 MyBatis-Plus {@link IPage} 构建（records 经映射后传入） */
    public static <T> PageDTO<T> from(IPage<?> page, List<T> list) {
        return of(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }
}
