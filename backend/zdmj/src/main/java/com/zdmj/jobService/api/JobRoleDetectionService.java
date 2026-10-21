package com.zdmj.jobService.api;

import com.zdmj.common.context.CurrentActor;

/** 岗位方向识别公开接口，由岗位模块维护规则及模型调用。 */
public interface JobRoleDetectionService {
    /**
     * 优先匹配关键词，未达到阈值时调用模型并校验输出。
     * @param actor 当前操作者
     * @param text 简历或岗位文本；空文本返回未知方向
     * @return 方向、置信度及识别依据
     * @throws IllegalStateException 模型输出缺失或不符合约束
     */
    JobRoleDetectionResult detect(CurrentActor actor, String text);
}
