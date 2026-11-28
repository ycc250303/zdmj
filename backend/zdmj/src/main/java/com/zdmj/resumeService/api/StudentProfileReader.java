package com.zdmj.resumeService.api;

import com.zdmj.common.context.CurrentActor;

/** 提供当前用户已经保存的能力画像。 */
public interface StudentProfileReader {
    /**
     * 按认证用户查询并复制画像，未生成画像时返回 null。
     * @param actor 已认证操作者
     * @return 不可变画像快照或 null
     */
    StudentProfileSnapshot findCurrentProfile(CurrentActor actor);
}
