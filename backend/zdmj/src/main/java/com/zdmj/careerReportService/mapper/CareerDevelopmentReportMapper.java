package com.zdmj.careerReportService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.careerReportService.entity.CareerDevelopmentReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 职业发展报告 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，自动拥有基础的 CRUD 方法：
 * - insert(entity) - 插入（自动回填ID）
 * - selectById(id) - 根据ID查询
 * - updateById(entity) - 根据ID更新
 * - deleteById(id) - 根据ID删除
 * - selectOne(wrapper) - 条件查询单个
 * - selectList(wrapper) - 条件查询列表
 * - selectCount(wrapper) - 条件计数
 * - update(wrapper) - 条件更新
 */
@Mapper
public interface CareerDevelopmentReportMapper extends BaseMapper<CareerDevelopmentReport> {
    // 版本化查询与业务写入均在 Service 层通过 QueryWrapper 实现，无需定义额外方法
}
