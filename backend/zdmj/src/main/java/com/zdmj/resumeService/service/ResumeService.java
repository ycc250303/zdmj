package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.ResumeContentDTO;
import com.zdmj.resumeService.dto.ResumeDTO;
import com.zdmj.resumeService.entity.Resume;
import java.util.List;

/**
 * 简历服务接口
 */
public interface ResumeService {

    /**
     * 创建简历
     *
     * @param resumeDTO 简历实体
     * @return 创建的简历实体
     */
    Resume create(ResumeDTO resumeDTO);

    /**
     * 根据ID查询简历
     *
     * @param id 简历ID
     * @return 简历实体
     */
    Resume getById(Long id);

    /**
     * 根据用户ID查询所有简历
     *
     * @param userId 用户ID
     * @return 简历列表
     */
    List<Resume> getByUserId();

    /**
     * 更新简历
     *
     * @param resume 简历实体
     * @return 更新后的简历实体
     */
    Resume update(ResumeDTO resumeDTO);

    /**
     * 删除简历
     *
     * @param id 简历ID
     */
    void delete(Long id);

    /**
     * 根据ID查询简历完整内容
     *
     * @param id 简历ID
     * @return 简历完整内容
     */
    ResumeContentDTO getResumeContentById(Long id);

    /**
     * 查询所有简历完整内容
     *
     * @return 简历完整内容列表
     */
    List<ResumeContentDTO> getResumeContentList();
}
