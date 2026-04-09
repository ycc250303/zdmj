package com.zdmj.jobService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.jobService.dto.JobDetailDTO;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.entity.Job;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface JobMapper extends BaseMapper<Job> {

    /** 
     * 查询岗位详情
     * @param id 岗位ID
     * @return 岗位详情
     */
    JobDetailDTO selectDetailById(@Param("id") Long id);

    /** 
     * 查询岗位列表
     * @param offset 偏移量
     * @param limit 每页条数
     * @param companySizes 公司规模
     * @param fundingTypes 融资类型
     * @param industries 行业
     * @return 岗位列表
     */
    List<JobListItemDTO> selectPage(
            @Param("offset") Integer offset,
            @Param("limit") Integer limit,
            @Param("companySizes") List<Integer> companySizes,
            @Param("fundingTypes") List<Integer> fundingTypes,
            @Param("industries") List<String> industries,
            @Param("companyNameKeyword") String companyNameKeyword);

    /** 
     * 查询岗位总数
     * @param companySizes 公司规模
     * @param fundingTypes 融资类型
     * @param industries 行业
     * @param companyNameKeyword 公司名称关键词（包含匹配）
     * @return 岗位总数
     */
    Long countPage(
            @Param("companySizes") List<Integer> companySizes,
            @Param("fundingTypes") List<Integer> fundingTypes,
            @Param("industries") List<String> industries,
            @Param("companyNameKeyword") String companyNameKeyword);
}
