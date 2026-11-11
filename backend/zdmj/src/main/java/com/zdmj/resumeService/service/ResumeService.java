package com.zdmj.resumeService.service;

import com.zdmj.resumeService.dto.ResumeContentResponse;
import com.zdmj.resumeService.dto.ResumeContentSaveRequest;
import com.zdmj.resumeService.dto.ResumeImportParseRequest;
import com.zdmj.resumeService.dto.ResumeImportParseResponse;

import java.util.List;

/**
 * 当前用户的一份简历：读取、保存和导入识别。
 */
public interface ResumeService {

    /**
     * 查询当前用户的整份简历。没有技能和经历时返回空列表。
     *
     * @return 空列表或只含一份简历的列表
     */
    List<ResumeContentResponse> getResumeContentList();

    /**
     * 读取当前用户的整份简历。没有技能时创建一条占位技能后再返回。
     *
     * @return 整份简历
     */
    ResumeContentResponse getMyResumeContent();

    /**
     * 按提交内容替换当前用户的技能、经历和个人资料。
     *
     * @param request 整份内容
     * @return 保存后的整份简历
     */
    ResumeContentResponse saveMyResumeContent(ResumeContentSaveRequest request);

    /**
     * 识别简历文本，不写入经历表。
     *
     * @param request 导入请求
     * @return 识别结果
     */
    ResumeImportParseResponse parseImport(ResumeImportParseRequest request);
}
