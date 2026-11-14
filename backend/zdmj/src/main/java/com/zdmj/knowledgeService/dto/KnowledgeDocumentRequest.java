package com.zdmj.knowledgeService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.UpdateGroup;

/**
 * 创建/更新知识文档请求
 */
@Data
public class KnowledgeDocumentRequest {
    /**
     * 文档ID
     */
    @NotNull(message = "文档ID不能为空", groups = UpdateGroup.class)
    private Long id;

    /**
     * 文档标题
     */
    @NotBlank(message = "文档标题不能为空", groups = CreateGroup.class)
    @jakarta.validation.constraints.Size(max = 500, message = "文档标题不能超过500个字符", groups = {CreateGroup.class, UpdateGroup.class})
    private String title;

    /**
     * 来源类型
     */
    @NotNull(message = "来源类型不能为空", groups = CreateGroup.class)
    private Integer type;

    /**
     * 编辑时的来源标识，仅用于核对当前文档；上传由服务端生成
     */
    private String content;
}
