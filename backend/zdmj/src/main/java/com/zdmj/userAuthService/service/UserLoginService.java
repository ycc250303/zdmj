package com.zdmj.userAuthService.service;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.userAuthService.dto.UserResponse;
import com.zdmj.userAuthService.dto.UserLoginRequest;
import com.zdmj.userAuthService.dto.UserLoginResponse;
import com.zdmj.userAuthService.dto.UserRegisterRequest;
import com.zdmj.userAuthService.dto.UserResetPasswordRequest;
import com.zdmj.userAuthService.dto.UserUpdateRequest;

/**
 * 用户服务接口
 */
public interface UserLoginService {

    /**
     * 用户注册
     *
     * @param registerDTO 注册信息
     * @return 用户信息
     */
    UserResponse register(UserRegisterRequest registerDTO);

    /**
     * 用户登录
     *
     * @param loginDTO 登录信息
     * @return 登录响应（包含Token和用户信息）
     */
    UserLoginResponse login(UserLoginRequest loginDTO);

    /**
     * 查询当前登录用户的资料。路径编号必须与认证身份一致。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param id 用户编号
     * @return 用户信息
     * @throws BusinessException 未登录、编号不属于当前用户或用户不存在
     */
    UserResponse getUserById(CurrentActor actor, Long id);

    /**
     * 检查用户名是否存在
     *
     * @param username 用户名
     * @return 是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     *
     * @param email 邮箱
     * @return 是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 重置密码（忘记密码时使用）
     *
     * @param resetPasswordDTO 重置密码信息（包含邮箱、验证码、新密码）
     */
    void resetPassword(UserResetPasswordRequest resetPasswordDTO);

    /**
     * 按用户编号更新姓名、电话、主页和意向工作城市。
     * 字段为 null 时保持原值且不写入该列；非 null 时写入去掉首尾空白后的文本，空白截断后为空字符串。
     * 本次写入限于已提供的资料字段和更新时间。四个资料字段都未提供时不写数据库。
     *
     * @param actor 已认证操作者，包含用户编号
     * @param userId    用户编号
     * @param updateDTO 待更新字段
     * @return 更新后的用户信息
     * @throws BusinessException 未登录、编号不属于当前用户、用户不存在或写入失败
     */
    UserResponse updateProfile(CurrentActor actor, Long userId, UserUpdateRequest updateDTO);
}
