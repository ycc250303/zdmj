package com.zdmj.matchService.api;

import com.zdmj.common.context.CurrentActor;

/** 为报告提供当前用户的人岗匹配分析。 */
public interface JobMatchAnalysis {
    /**
     * 查询本人匹配，缺少时按默认权重生成并保存，再返回不可变快照。
     * @param actor 已认证操作者
     * @param jobId 岗位编号
     * @return 已保存的匹配快照
     * @throws com.zdmj.common.exception.BusinessException 岗位或画像缺失、模型生成失败
     */
    JobMatchSnapshot getOrGenerate(CurrentActor actor, Long jobId);
}
