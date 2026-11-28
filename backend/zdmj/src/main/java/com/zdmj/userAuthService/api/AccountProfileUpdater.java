package com.zdmj.userAuthService.api;

import com.zdmj.common.context.CurrentActor;

/** 接收简历中的账户资料，同步范围仅限当前认证用户。 */
public interface AccountProfileUpdater {
    /**
     * 加入调用方事务，未提供事务时独立提交；按非 null 字段同步资料，文本去除首尾空白，空白保存为空字符串。
     * @param actor 已认证操作者
     * @param update 姓名、电话、主页及意向城市，全部未提供时不写数据库
     * @throws com.zdmj.common.exception.BusinessException 用户不存在或资料写入失败
     */
    void synchronizeProfile(CurrentActor actor, AccountProfileUpdate update);
}
