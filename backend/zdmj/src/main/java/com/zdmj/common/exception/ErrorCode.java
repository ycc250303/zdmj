package com.zdmj.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一业务错误码。
 *
 * <p>约定：{@code 0} 表示成功；{@code 1xxx} 起为业务错误码。每个枚举项显式绑定
 * {@link HttpStatus}，由 {@link GlobalExceptionHandler} 与 {@link ProblemDetailSupport} 写入
 * RFC 9457 响应，不再依赖错误文案推断 HTTP 状态。</p>
 */
@Getter
public enum ErrorCode {

    // ========== 通用错误 1xxx ==========
    VALIDATION_ERROR(1001, "参数校验失败", HttpStatus.BAD_REQUEST),
    USER_NOT_LOGIN(1002, "用户未登录", HttpStatus.UNAUTHORIZED),
    NO_PERMISSION(1003, "无权操作", HttpStatus.FORBIDDEN),
    REQUEST_BODY_ERROR(1004, "请求体错误，请提供有效的JSON数据", HttpStatus.BAD_REQUEST),
    DATE_FORMAT_ERROR(1005, "日期格式错误，请使用 yyyy-MM-dd 格式（例如：2024-09-01）", HttpStatus.BAD_REQUEST),
    REQUEST_METHOD_NOT_SUPPORTED(1006, "请求方法不支持或接口路径错误", HttpStatus.NOT_FOUND),
    URL_FORMAT_ERROR(1007, "URL格式不正确", HttpStatus.BAD_REQUEST),
    SYSTEM_EXCEPTION(1008, "系统异常，请联系管理员", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_EMPTY(1009, "上传文件不能为空", HttpStatus.BAD_REQUEST),
    FILE_SIZE_EXCEEDED(1010, "上传文件大小超出限制", HttpStatus.BAD_REQUEST),
    RATE_LIMIT_EXCEEDED(1011, "请求过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),

    // ========== 用户相关 2xxx ==========
    USER_ALREADY_EXISTS(2001, "用户名已存在", HttpStatus.CONFLICT),
    USER_EMAIL_EXISTS(2002, "邮箱已被注册", HttpStatus.CONFLICT),
    CAPTCHA_ERROR(2003, "验证码错误或已过期", HttpStatus.BAD_REQUEST),
    USER_REGISTER_FAILED(2004, "用户注册失败", HttpStatus.BAD_REQUEST),
    USER_PASSWORD_WRONG(2005, "用户名或密码错误", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(2006, "用户不存在", HttpStatus.NOT_FOUND),
    USER_EMAIL_NOT_REGISTERED(2007, "该邮箱未注册", HttpStatus.NOT_FOUND),
    PASSWORD_CHANGE_FAILED(2008, "密码修改失败", HttpStatus.BAD_REQUEST),
    CAPTCHA_SEND_FAILED(2009, "验证码发送失败，请稍后重试", HttpStatus.BAD_REQUEST),
    USER_LLM_NOT_CONFIGURED(2010, "请先配置大模型与 API Key", HttpStatus.PRECONDITION_REQUIRED),
    USER_LLM_CONFIG_INVALID(2011, "大模型配置无效", HttpStatus.BAD_REQUEST),
    USER_LLM_API_KEY_ENCRYPT_FAILED(2012, "API Key 加密失败", HttpStatus.BAD_REQUEST),
    USER_LLM_API_KEY_DECRYPT_FAILED(2013, "API Key 解密失败", HttpStatus.BAD_REQUEST),
    USER_LLM_CONNECTION_TEST_FAILED(2014, "大模型连通性测试失败", HttpStatus.BAD_REQUEST),

    // ========== 简历与能力画像 3xxx ==========
    RESUME_CREATE_FAILED(3002, "创建简历失败", HttpStatus.BAD_REQUEST),
    RESUME_ALREADY_EXISTS(3003, "用户已有简历，不能重复创建", HttpStatus.CONFLICT),
    RESUME_UPDATE_FAILED(3004, "更新简历失败", HttpStatus.BAD_REQUEST),
    RESUME_DELETE_FAILED(3005, "删除简历失败", HttpStatus.BAD_REQUEST),
    RESUME_NOT_FOUND(3006, "简历不存在", HttpStatus.NOT_FOUND),
    CAPABILITY_PROFILE_SCORE_INVALID(3010, "能力画像分项评分超出合法范围", HttpStatus.BAD_REQUEST),
    CAPABILITY_PROFILE_NOT_FOUND(3011, "当前用户尚未生成能力画像", HttpStatus.NOT_FOUND),
    CAPABILITY_PROFILE_GENERATION_FAILED(3012, "能力画像生成失败，请稍后重试", HttpStatus.BAD_REQUEST),
    RESUME_IMPORT_PARSE_FAILED(3013, "简历识别失败，请稍后重试", HttpStatus.BAD_REQUEST),
    RESUME_IMPORT_TEXT_EMPTY(3014, "提取到的简历文本为空，无法识别", HttpStatus.BAD_REQUEST),

    // ========== 项目经历 4xxx ==========
    PROJECT_EXPERIENCE_ADD_FAILED(4001, "添加项目经历失败", HttpStatus.BAD_REQUEST),
    PROJECT_END_TIME_INVALID(4002, "项目结束时间不能早于开始时间", HttpStatus.BAD_REQUEST),
    PROJECT_EXPERIENCE_UPDATE_FAILED(4003, "更新项目经历失败", HttpStatus.BAD_REQUEST),
    PROJECT_EXPERIENCE_DELETE_FAILED(4004, "删除项目经历失败", HttpStatus.BAD_REQUEST),
    PROJECT_EXPERIENCE_NOT_FOUND(4005, "项目经历不存在", HttpStatus.NOT_FOUND),

    // ========== 工作经历 5xxx ==========
    CAREER_ADD_FAILED(5001, "添加工作经历失败", HttpStatus.BAD_REQUEST),
    CAREER_LEAVE_TIME_INVALID(5003, "离职时间不能早于入职时间", HttpStatus.BAD_REQUEST),
    CAREER_UPDATE_FAILED(5004, "更新工作经历失败", HttpStatus.BAD_REQUEST),
    CAREER_DELETE_FAILED(5005, "删除工作经历失败", HttpStatus.BAD_REQUEST),
    CAREER_NOT_FOUND(5006, "工作经历不存在", HttpStatus.NOT_FOUND),

    // ========== 教育经历 6xxx ==========
    EDUCATION_ADD_FAILED(6001, "添加教育经历失败", HttpStatus.BAD_REQUEST),
    EDUCATION_UPDATE_FAILED(6002, "更新教育经历失败", HttpStatus.BAD_REQUEST),
    EDUCATION_GRADUATE_TIME_INVALID(6003, "毕业时间不能早于入学时间", HttpStatus.BAD_REQUEST),
    EDUCATION_DELETE_FAILED(6004, "删除教育经历失败", HttpStatus.BAD_REQUEST),
    EDUCATION_NOT_FOUND(6005, "教育经历不存在", HttpStatus.NOT_FOUND),

    // ========== 获奖信息 6500 ==========
    AWARD_ADD_FAILED(6501, "添加获奖信息失败", HttpStatus.BAD_REQUEST),
    AWARD_UPDATE_FAILED(6502, "更新获奖信息失败", HttpStatus.BAD_REQUEST),
    AWARD_DELETE_FAILED(6503, "删除获奖信息失败", HttpStatus.BAD_REQUEST),
    AWARD_NOT_FOUND(6504, "获奖信息不存在", HttpStatus.NOT_FOUND),
    AWARD_TYPE_INVALID(6505, "奖项类型无效", HttpStatus.BAD_REQUEST),

    // ========== 技能 7xxx ==========
    SKILL_ADD_FAILED(7001, "添加技能失败", HttpStatus.BAD_REQUEST),
    SKILL_UPDATE_FAILED(7003, "更新技能失败", HttpStatus.BAD_REQUEST),
    SKILL_DELETE_FAILED(7004, "删除技能失败", HttpStatus.BAD_REQUEST),
    SKILL_NOT_FOUND(7005, "技能不存在", HttpStatus.NOT_FOUND),

    // ========== 知识库 8000-8099 ==========
    KNOWLEDGE_BASE_SAVE_FAILED(8001, "保存知识库失败", HttpStatus.BAD_REQUEST),
    KNOWLEDGE_BASE_DELETE_FAILED(8004, "删除知识库失败", HttpStatus.BAD_REQUEST),
    KNOWLEDGE_BASE_NOT_FOUND(8005, "知识库不存在", HttpStatus.NOT_FOUND),
    FILE_TYPE_NOT_EXISTS(8009, "文件类型不存在，请不要修改知识内容中的URL", HttpStatus.BAD_REQUEST),
    KNOWLEDGE_BASE_EMBEDDING_FAILED(8010, "向量化任务失败", HttpStatus.BAD_REQUEST),
    KNOWLEDGE_DOCUMENT_CREATE_FAILED(8011, "创建知识文档失败", HttpStatus.BAD_REQUEST),
    KNOWLEDGE_DOCUMENT_NOT_FOUND(8012, "知识文档不存在", HttpStatus.NOT_FOUND),
    KNOWLEDGE_DOCUMENT_UPDATE_FAILED(8013, "更新知识文档失败", HttpStatus.BAD_REQUEST),
    KNOWLEDGE_DOCUMENT_CONTENT_EXISTS(8014, "该知识文档已存在，请勿重复添加", HttpStatus.CONFLICT),

    // ========== 对话 9xxx ==========
    CONVERSATION_CREATE_FAILED(9001, "创建会话失败", HttpStatus.BAD_REQUEST),
    CONVERSATION_DELETE_FAILED(9002, "删除会话失败", HttpStatus.BAD_REQUEST),
    CONVERSATION_NOT_FOUND(9003, "会话不存在", HttpStatus.NOT_FOUND),
    CONVERSATION_UPDATE_FAILED(9004, "更新会话失败", HttpStatus.BAD_REQUEST),
    MESSAGE_CREATE_FAILED(9005, "创建消息失败", HttpStatus.BAD_REQUEST),
    CONVERSATION_CONFIG_LOCKED(9006, "会话已开始对话，无法修改检索配置", HttpStatus.CONFLICT),

    // ========== 岗位 jobService 100xx ==========
    JOB_NOT_FOUND(10001, "岗位不存在", HttpStatus.NOT_FOUND),
    JOB_CAPABILITY_PROFILE_GENERATION_FAILED(10002, "生成岗位能力画像失败", HttpStatus.BAD_REQUEST),
    JOB_CAREER_GRAPH_GENERATION_FAILED(10004, "生成岗位关联图谱失败", HttpStatus.BAD_REQUEST),
    JOB_CAREER_GRAPH_INVALID(10005, "岗位关联图谱结果不符合要求（晋升路径或换岗路径数量不足）", HttpStatus.BAD_REQUEST),

    // ========== 人岗匹配 matchService 110xx ==========
    MATCH_GENERATION_FAILED(11001, "生成人岗匹配分析失败，请稍后重试", HttpStatus.BAD_REQUEST),
    MATCH_PRECONDITION_MISSING(11002, "学生能力画像缺失，请先到能力画像页生成画像", HttpStatus.BAD_REQUEST),

    // ========== 职业发展报告 careerReportService 120xx ==========
    CAREER_REPORT_NOT_FOUND(12001, "职业发展报告不存在", HttpStatus.NOT_FOUND),
    CAREER_REPORT_GENERATION_FAILED(12002, "生成职业发展报告失败，请稍后重试", HttpStatus.BAD_REQUEST),
    CAREER_REPORT_POLISH_FAILED(12003, "润色职业发展报告失败，请稍后重试", HttpStatus.BAD_REQUEST),
    CAREER_REPORT_INVALID(12004, "职业发展报告结果不符合要求", HttpStatus.BAD_REQUEST);

    private final Integer code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(Integer code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    /**
     * 按业务错误码解析 HTTP 状态；未知码回退为 400。
     */
    public static HttpStatus httpStatusOf(Integer code) {
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        for (ErrorCode value : values()) {
            if (value.code.equals(code)) {
                return value.httpStatus;
            }
        }
        return HttpStatus.BAD_REQUEST;
    }

    /**
     * 按业务错误码解析枚举；未知码回退为 {@link #SYSTEM_EXCEPTION}。
     */
    public static ErrorCode of(Integer code) {
        if (code == null) {
            return SYSTEM_EXCEPTION;
        }
        for (ErrorCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return SYSTEM_EXCEPTION;
    }
}
