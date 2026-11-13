package com.zdmj.common.storage;

import java.util.Locale;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/** 文件分类，对应对象键第二段及 HTTP prefix 取值。 */
public enum FileCategory {
    /** 通用文件，未指定上传分类时使用 files。 */
    FILES,
    /** 简历导入文件，对应 resume。 */
    RESUME,
    /** 能力画像分析文件，对应 profile。 */
    PROFILE,
    /** 知识库文档，对应 knowledge。 */
    KNOWLEDGE;

    public String path() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FileCategory parse(String value) {
        if (value == null || value.isBlank()) {
            return FILES;
        }
        for (FileCategory category : values()) {
            if (category.path().equals(value.trim())) {
                return category;
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件分类无效");
    }
}