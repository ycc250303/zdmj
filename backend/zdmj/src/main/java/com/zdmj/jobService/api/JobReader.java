package com.zdmj.jobService.api;

/** 提供共享岗位详情，隐藏岗位维护与缓存实现。 */
public interface JobReader {
    /**
     * 读取岗位详情，沿用岗位详情缓存与缺失语义。
     * @param id 岗位编号
     * @return 包含公司信息的不可变岗位详情
     * @throws com.zdmj.common.exception.BusinessException 岗位不存在
     */
    JobListItemResponse getDetail(Long id);
}
