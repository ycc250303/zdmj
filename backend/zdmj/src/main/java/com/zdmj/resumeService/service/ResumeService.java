package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.ResumeContentDTO;
import com.zdmj.resumeService.dto.ResumeContentSaveRequest;
import com.zdmj.resumeService.dto.ResumeDTO;
import com.zdmj.resumeService.dto.ResumeImportParseRequest;
import com.zdmj.resumeService.dto.ResumeImportParseResultDTO;
import com.zdmj.resumeService.entity.Resume;

import java.util.List;

/**
 * 简历服务接口
 */
public interface ResumeService {

    /**
     * 创建简历（每用户仅允许一份）
     */
    Resume create(ResumeDTO resumeDTO);

    List<Resume> getByUserId();

    Resume update(ResumeDTO resumeDTO);

    void delete(Long id);

    List<ResumeContentDTO> getResumeContentList();

    /**
     * 获取当前登录用户的简历完整内容；若不存在则自动创建空简历。
     */
    ResumeContentDTO getMyResumeContent();

    /**
     * 全量保存当前登录用户的简历内容（事务内 upsert 技能与各经历，并删除未提交项）。
     */
    ResumeContentDTO saveMyResumeContent(ResumeContentSaveRequest request);

    ResumeImportParseResultDTO parseImport(ResumeImportParseRequest request);
}
