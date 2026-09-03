package com.zdmj.userAuthService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.security.JwtSessionStore;
import com.zdmj.userAuthService.util.JwtUtil;
import com.zdmj.userAuthService.util.PasswordUtil;
import com.zdmj.userAuthService.dto.UserResponse;
import com.zdmj.userAuthService.dto.UserLoginRequest;
import com.zdmj.userAuthService.dto.UserLoginResponse;
import com.zdmj.userAuthService.dto.UserRegisterRequest;
import com.zdmj.userAuthService.dto.UserResetPasswordRequest;
import com.zdmj.userAuthService.dto.UserUpdateRequest;
import com.zdmj.userAuthService.entity.User;
import com.zdmj.userAuthService.mapper.UserMapper;
import com.zdmj.userAuthService.enums.VerificationCodeScene;
import com.zdmj.userAuthService.service.UserService;
import com.zdmj.userAuthService.service.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务实现类
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final VerificationCodeService verificationCodeService;
    private final JwtSessionStore jwtSessionStore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse register(UserRegisterRequest registerDTO) {
        // 1. 检查用户名是否已存在
        if (existsByUsername(registerDTO.getUsername())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        // 2. 检查邮箱是否已存在
        if (existsByEmail(registerDTO.getEmail())) {
            throw new BusinessException(ErrorCode.USER_EMAIL_EXISTS);
        }

        // 3. 验证验证码
        if (!verificationCodeService.verifyCode(registerDTO.getEmail(), registerDTO.getVerificationCode(),
                VerificationCodeScene.REGISTER)) {
            throw new BusinessException(ErrorCode.CAPTCHA_ERROR);
        }

        // 4. 创建用户对象
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(PasswordUtil.encode(registerDTO.getPassword())); // 加密密码
        user.setEmail(registerDTO.getEmail());

        // 5. 保存到数据库
        boolean saved = save(user);
        if (!saved) {
            throw new BusinessException(ErrorCode.USER_REGISTER_FAILED);
        }

        log.info("用户注册成功: {}", user.getUsername());

        // 6. 转换为DTO返回
        return convertToResponse(user);
    }

    @Override
    public UserLoginResponse login(UserLoginRequest loginDTO) {
        // 1. 根据用户名或邮箱查询用户
        User user = null;
        String usernameOrEmail = loginDTO.getUsernameOrEmail();

        // 判断是用户名还是邮箱（简单判断：包含@符号则为邮箱）
        if (usernameOrEmail.contains("@")) {
            user = getUserByEmail(usernameOrEmail);
        } else {
            user = getUserByUsername(usernameOrEmail);
        }

        // 2. 检查用户是否存在
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 3. 验证密码
        if (!PasswordUtil.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_WRONG);
        }

        String token = JwtUtil.generateToken(user.getId(), user.getUsername());
        try {
            jwtSessionStore.replace(user.getId(), token);
        } catch (DataAccessException e) {
            log.error("写入登录态失败: userId={}", user.getId(), e);
            throw new BusinessException(ErrorCode.AUTH_STORE_UNAVAILABLE, e);
        }
        log.info("用户登录成功: {}", user.getUsername());

        // 构建登录响应
        UserLoginResponse response = new UserLoginResponse();
        response.setToken(token);
        response.setUser(convertToResponse(user));

        return response;
    }

    @Override
    public UserResponse getUserById(Long id) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return convertToResponse(user);
    }

    @Override
    public User getUserByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
    }

    @Override
    public User getUserByEmail(String email) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
    }

    @Override
    public boolean existsByUsername(String username) {
        return count(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)) > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        return count(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(UserResetPasswordRequest resetPasswordDTO) {
        // 1. 根据邮箱查询用户
        User user = getUserByEmail(resetPasswordDTO.getEmail());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_EMAIL_NOT_REGISTERED);
        }

        // 2. 验证验证码
        if (!verificationCodeService.verifyCode(resetPasswordDTO.getEmail(), resetPasswordDTO.getVerificationCode(),
                VerificationCodeScene.RESET_PASSWORD)) {
            throw new BusinessException(ErrorCode.CAPTCHA_ERROR);
        }

        // 3. 加密新密码
        String encodedPassword = PasswordUtil.encode(resetPasswordDTO.getNewPassword());

        // 4. 使用 MyBatis-Plus 的 LambdaUpdateWrapper 更新密码
        boolean updated = update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getPassword, encodedPassword)
                .set(User::getUpdatedAt, DateTimeUtil.now()));
        if (!updated) {
            throw new BusinessException(ErrorCode.PASSWORD_CHANGE_FAILED);
        }

        log.info("用户密码重置成功: userId={}, email={}", user.getId(), resetPasswordDTO.getEmail());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse updateCurrentUser(UserUpdateRequest updateDTO) {
        Long userId = UserHolder.requireUserId();

        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (updateDTO.getName() != null) {
            rejectBlankField(updateDTO.getName(), "姓名不能为空");
            user.setName(updateDTO.getName());
        }
        if (updateDTO.getPhone() != null) {
            rejectBlankField(updateDTO.getPhone(), "电话不能为空");
            user.setPhone(updateDTO.getPhone());
        }
        if (updateDTO.getWebsite() != null) {
            rejectBlankField(updateDTO.getWebsite(), "主页链接不能为空");
            user.setWebsite(updateDTO.getWebsite());
        }
        if (updateDTO.getPreferredWorkCity() != null) {
            user.setPreferredWorkCity(updateDTO.getPreferredWorkCity().trim());
        }
        boolean updated = updateById(user);
        if (!updated) {
            throw new BusinessException(ErrorCode.USER_REGISTER_FAILED);
        }

        log.info("用户信息更新成功: userId={}", userId);
        return convertToResponse(user);
    }

    private void rejectBlankField(String value, String message) {
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), message);
        }
    }

    /**
     * 将User实体转换为UserResponse
     */
    private UserResponse convertToResponse(User user) {
        UserResponse dto = new UserResponse();
        BeanUtils.copyProperties(user, dto);
        return dto;
    }

}
