package com.zdmj.knowledgeService.enums;

/**
 * 知识类型枚举
 */
public enum KnowledgeTypeEnum {
    PROJECT_DOCUMENT(1, "项目文档"),
    GITHUB_REPO(2, "GitHub链接"),
    PROJECT_DEEPWIKI(3, "项目DeepWiki文档");

    private final int code;
    private final String label;

    KnowledgeTypeEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
