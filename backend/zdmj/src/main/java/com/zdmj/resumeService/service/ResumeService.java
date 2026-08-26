package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.ResumeContentResponse;
import com.zdmj.resumeService.dto.ResumeContentSaveRequest;
import com.zdmj.resumeService.dto.ResumeImportParseRequest;
import com.zdmj.resumeService.dto.ResumeImportParseResponse;
import com.zdmj.resumeService.dto.ResumeRequest;
import com.zdmj.resumeService.dto.ResumeResponse;

import java.util.List;

/**
 * 简历服务接口
 */
public interface ResumeService {

    ResumeResponse create(ResumeRequest resumeRequest);

    List<ResumeResponse> getByUserId();

    ResumeResponse update(ResumeRequest resumeRequest);

    void delete(Long id);

    List<ResumeContentResponse> getResumeContentList();

    ResumeContentResponse getMyResumeContent();

    ResumeContentResponse saveMyResumeContent(ResumeContentSaveRequest request);

    ResumeImportParseResponse parseImport(ResumeImportParseRequest request);
}
