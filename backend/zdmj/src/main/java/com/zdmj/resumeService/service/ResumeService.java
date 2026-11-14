package com.zdmj.resumeService.service;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.resumeService.dto.ResumeContentResponse;
import com.zdmj.resumeService.dto.ResumeContentSaveRequest;
import com.zdmj.resumeService.dto.ResumeImportParseRequest;
import com.zdmj.resumeService.dto.ResumeImportParseResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 当前用户的一份简历：读取、保存和导入识别。
 */
public interface ResumeService {

    /**
     * 查询当前用户的整份简历。没有技能和经历时返回空列表。
     *
     * @param actor 已认证操作者，包含用户编号
     * @return 空列表或只含一份简历的列表
     */
    List<ResumeContentResponse> getResumeContentList(CurrentActor actor);

    /**
     * 读取当前用户的整份简历。没有技能时创建一条占位技能后再返回。
     *
     * @param actor 已认证操作者，包含用户编号
     * @return 整份简历
     */
    ResumeContentResponse getMyResumeContent(CurrentActor actor);

    /**
     * 按提交内容替换当前用户的技能、经历和个人资料。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param request 整份内容
     * @return 保存后的整份简历
     */
    ResumeContentResponse saveMyResumeContent(CurrentActor actor, ResumeContentSaveRequest request);

    /**
     * 识别简历纯文本，不写入经历表。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param request 纯文本请求
     * @return 识别结果
     * @throws com.zdmj.common.exception.BusinessException 文本为空或识别失败
     */
    ResumeImportParseResponse parseImport(CurrentActor actor, ResumeImportParseRequest request);

    /**
     * 从上传文件提取文本后识别简历，不保存文件，也不写入经历表。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param file 非空简历文件
     * @return 识别结果
     * @throws com.zdmj.common.exception.BusinessException 文件为空、无法读取、提取文本为空或识别失败
     */
    ResumeImportParseResponse parseImport(CurrentActor actor, MultipartFile file);
}
