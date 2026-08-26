package com.zdmj.userAuthService.service;

import com.zdmj.userAuthService.dto.UserResponse;
import com.zdmj.userAuthService.dto.UserLoginRequest;
import com.zdmj.userAuthService.dto.UserLoginResponse;
import com.zdmj.userAuthService.dto.UserRegisterRequest;
import com.zdmj.userAuthService.dto.UserResetPasswordRequest;
import com.zdmj.userAuthService.dto.UserUpdateRequest;
import com.zdmj.userAuthService.entity.User;

/**
 * 用户服务接口
 */
public interface UserService {

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
     * 根据用户名查询用户
     * 
     * @param username 用户名
     * @return 用户实体
     */
    User getUserByUsername(String username);

    /**
     * 根据邮箱查询用户
     * 
     * @param email 邮箱
     * @return 用户实体
     */
    User getUserByEmail(String email);

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
     * 更新当前登录用户的基本信息
     *
     * @param updateDTO 更新信息（姓名、电话、主页链接）
     * @return 更新后的用户信息
     */
    UserResponse updateCurrentUser(UserUpdateRequest updateDTO);
}
