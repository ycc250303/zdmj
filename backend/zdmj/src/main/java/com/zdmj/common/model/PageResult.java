package com.zdmj.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果封装类
 * 
 * @param <T> 数据项类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /**
     * 数据列表
     */
    private List<T> data;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer page;

    /**
     * 每页数量
     */
    private Integer limit;

    /**
     * 创建分页结果
     * 
     * @param data  数据列表
     * @param total 总记录数
     * @param page  当前页码
     * @param limit 每页数量
     * @return 分页结果对象
     */
    public static <T> PageResult<T> of(List<T> data, Long total, Integer page, Integer limit) {
        return new PageResult<>(data, total, page, limit);
    }
}
