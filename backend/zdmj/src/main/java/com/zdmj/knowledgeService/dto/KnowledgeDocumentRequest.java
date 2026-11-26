package com.zdmj.knowledgeService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;

/**
 * 创建/更新知识文档请求
 *
 * @param id 文档编号；更新时必填
 * @param title 文档标题，最长 500 个字符；创建时不能为空白
 * @param type 来源类型；创建时必填，上传接口使用类型 1
 * @param content 编辑时用于核对原文档的来源标识；上传时由服务端生成
 */
public record KnowledgeDocumentRequest(
        @NotNull(message = "文档ID不能为空", groups = UpdateGroup.class)
        Long id,
        @NotBlank(message = "文档标题不能为空", groups = CreateGroup.class)
        @Size(max = 500, message = "文档标题不能超过500个字符", groups = {CreateGroup.class, UpdateGroup.class})
        String title,
        @NotNull(message = "来源类型不能为空", groups = CreateGroup.class)
        Integer type,
        String content) {
}
