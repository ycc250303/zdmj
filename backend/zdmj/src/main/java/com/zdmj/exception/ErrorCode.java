package com.zdmj.exception;

import lombok.Getter;

/**
 * 统一错误码定义
 * 用于统一管理业务错误码
 */
@Getter
public enum ErrorCode {

    // ========== HTTP状态码 ==========
    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // ========== 通用错误 1xxx ==========
    VALIDATION_ERROR(1001, "参数校验失败"),
    USER_NOT_LOGIN(1002, "用户未登录"),
    NO_PERMISSION(1003, "无权操作"),
    RESOURCE_NOT_FOUND(1004, "资源不存在"),
    SERVER_ERROR(1005, "服务器内部错误"),
    REQUEST_BODY_EMPTY(1006, "请求体不能为空，请提供有效的JSON数据"),
    DATE_FORMAT_ERROR(1007, "日期格式错误，请使用 yyyy-MM-dd 格式（例如：2024-09-01）"),
    JSON_FORMAT_ERROR(1008, "JSON格式错误，请检查请求体格式是否正确"),
    PARAM_TYPE_ERROR(1009, "请求参数格式错误，请检查数据类型是否正确"),
    MISSING_PARAMETER(1010, "缺少必需的请求参数"),
    FILE_EMPTY(1011, "文件不能为空"),
    URL_FORMAT_ERROR(1012, "URL格式不正确"),
    ILLEGAL_ARGUMENT(1013, "非法参数"),
    SYSTEM_EXCEPTION(1014, "系统异常，请联系管理员"),

    // ========== 用户相关 (2xxx) ==========
    USER_ALREADY_EXISTS(2001, "用户名已存在"),
    USER_EMAIL_EXISTS(2002, "邮箱已被注册"),
    CAPTCHA_ERROR(2003, "验证码错误或已过期"),
    USER_REGISTER_FAILED(2004, "用户注册失败"),
    USER_PASSWORD_WRONG(2005, "用户名或密码错误"),
    USER_NOT_FOUND(2006, "用户不存在"),
    USER_EMAIL_NOT_REGISTERED(2007, "该邮箱未注册"),
    PASSWORD_CHANGE_FAILED(2008, "密码修改失败"),
    CAPTCHA_SEND_FAILED(2009, "验证码发送失败，请稍后重试"),

    // ========== 简历相关 (3xxx) ==========
    RESUME_NAME_EXISTS(3001, "简历名称已存在，请使用其他名称"),
    RESUME_CREATE_FAILED(3002, "创建简历失败"),
    RESUME_ID_EMPTY(3003, "简历ID不能为空"),
    RESUME_UPDATE_FAILED(3004, "更新简历失败"),
    RESUME_DELETE_FAILED(3005, "删除简历失败"),
    RESUME_NOT_FOUND(3006, "简历不存在"),

    // ========== 项目经历相关 (4xxx) ==========
    PROJECT_EXPERIENCE_ADD_FAILED(4001, "添加项目经历失败"),
    PROJECT_EXPERIENCE_ID_EMPTY(4002, "项目经历ID不能为空"),
    PROJECT_END_TIME_INVALID(4003, "项目结束时间不能早于开始时间"),
    PROJECT_EXPERIENCE_UPDATE_FAILED(4004, "更新项目经历失败"),
    PROJECT_EXPERIENCE_DELETE_FAILED(4005, "删除项目经历失败"),
    PROJECT_EXPERIENCE_NOT_FOUND(4006, "项目经历不存在"),
    PROJECT_EXPERIENCE_NAME_NOT_ALLOW_CHANGE(4007, "项目经历名称一经确定，不能修改"),

    // ========== 工作经历相关 (5xxx) ==========
    CAREER_ADD_FAILED(5001, "添加工作经历失败"),
    CAREER_ID_EMPTY(5002, "工作经历ID不能为空"),
    CAREER_LEAVE_TIME_INVALID(5003, "离职时间不能早于入职时间"),
    CAREER_UPDATE_FAILED(5004, "更新工作经历失败"),
    CAREER_DELETE_FAILED(5005, "删除工作经历失败"),
    CAREER_NOT_FOUND(5006, "工作经历不存在"),

    // ========== 教育经历相关 (6xxx) ==========
    EDUCATION_ADD_FAILED(6001, "添加教育经历失败"),
    EDUCATION_ID_EMPTY(6002, "教育经历ID不能为空"),
    EDUCATION_GRADUATE_TIME_INVALID(6003, "毕业时间不能早于入学时间"),
    EDUCATION_UPDATE_FAILED(6004, "更新教育经历失败"),
    EDUCATION_DELETE_FAILED(6005, "删除教育经历失败"),
    EDUCATION_NOT_FOUND(6006, "教育经历不存在"),

    // ========== 技能相关 (7xxx) ==========
    SKILL_ADD_FAILED(7001, "添加技能失败"),
    SKILL_ID_EMPTY(7002, "技能ID不能为空"),
    SKILL_UPDATE_FAILED(7003, "更新技能失败"),
    SKILL_DELETE_FAILED(7004, "删除技能失败"),
    SKILL_NOT_FOUND(7005, "技能不存在"),
    SKILL_CONTENT_EMPTY(7006, "技能内容不能为空"),
    SKILL_CONTENT_FORMAT_ERROR(7007, "技能内容格式错误"),
    SKILL_CONTENT_PARSE_FAILED(7008, "技能内容解析失败"),

    // ========== 知识库相关 (8xxx) ==========
    KNOWLEDGE_BASE_SAVE_FAILED(8001, "保存知识库失败"),
    KNOWLEDGE_BASE_ID_EMPTY(8002, "知识库ID不能为空"),
    KNOWLEDGE_BASE_UPDATE_FAILED(8003, "更新知识库失败"),
    KNOWLEDGE_BASE_DELETE_FAILED(8004, "删除知识库失败"),
    KNOWLEDGE_BASE_NOT_FOUND(8005, "知识库不存在"),
    KNOWLEDGE_BASE_NAME_EXISTS(8006, "知识库名称已存在，请使用其他名称"),
    PDF_URL_REQUIRED(8007, "PDF文档类型必须提供COS URL"),
    MARKDOWN_URL_REQUIRED(8008, "Markdown文档类型必须提供COS URL"),
    FILE_TYPE_NOT_EXISTS(8009, "文件类型不存在，请不要修改知识内容中的URL"),
    KNOWLEDGE_BASE_EMBEDDING_FAILED(8010, "触发向量化任务失败");


    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
