package com.zdmj.userAuthService.service;

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
     * 根据ID查询用户
     * 
     * @param id 用户ID
     * @return 用户信息
     */
    UserResponse getUserById(Long id);

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
     * 字段为 null 时保持原值；非 null 时写入去掉首尾空白后的文本，空白截断后为空字符串。
     *
     * @param userId    用户编号
     * @param updateDTO 待更新字段
     * @return 更新后的用户信息
     * @throws BusinessException 用户不存在或写入失败
     */
    UserResponse updateProfile(Long userId, UserUpdateRequest updateDTO);
}
