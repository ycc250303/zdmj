package com.zdmj.userAuthService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.DateTimeUtil;
import com.zdmj.common.security.JwtSessionStore;
import com.zdmj.common.security.JwtTokenService;
import com.zdmj.userAuthService.dto.UserResponse;
import com.zdmj.userAuthService.dto.UserLoginRequest;
import com.zdmj.userAuthService.dto.UserLoginResponse;
import com.zdmj.userAuthService.dto.UserRegisterRequest;
import com.zdmj.userAuthService.dto.UserResetPasswordRequest;
import com.zdmj.userAuthService.dto.UserUpdateRequest;
import com.zdmj.userAuthService.entity.User;
import com.zdmj.userAuthService.mapper.UserMapper;
import com.zdmj.userAuthService.enums.VerificationCodeScene;
import com.zdmj.userAuthService.service.UserLoginService;
import com.zdmj.userAuthService.service.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务实现类
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UserLoginServiceImpl extends ServiceImpl<UserMapper, User> implements UserLoginService {

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final VerificationCodeService verificationCodeService;
    private final JwtSessionStore jwtSessionStore;
    private final JwtTokenService jwtTokenService;

    /**
     * 注册用户
     *
     * @param registerDTO 用户名、邮箱、密码与验证码
     * @return 提交后的用户信息
     * @throws BusinessException 用户已存在、验证码被拒绝或基础设施不可用；数据库故障保留原因传播
     */
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

        // 3. 创建用户
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(PASSWORD_ENCODER.encode(registerDTO.getPassword()));
        user.setEmail(registerDTO.getEmail());

        // 4. 验证验证码
        verificationCodeService.verifyAndConsume(registerDTO.getEmail(), registerDTO.getVerificationCode(),
                VerificationCodeScene.REGISTER);

        // 5. 保存用户
        if (!save(user)) {
            throw new BusinessException(ErrorCode.USER_REGISTER_FAILED);
        }
        log.info("用户注册成功: {}", user.getUsername());
        return convertToResponse(user);
    }

    /**
     * 登录
     *
     * @param loginDTO 用户名或邮箱与密码
     * @return 登录响应（包含Token和用户信息）
     * @throws BusinessException 用户不存在、密码错误或基础设施不可用；数据库故障保留原因传播
     */
    @Override
    public UserLoginResponse login(UserLoginRequest loginDTO) {
        // 1. 根据用户名或邮箱查询用户
        String usernameOrEmail = loginDTO.getUsernameOrEmail();

        // 判断是用户名还是邮箱（简单判断：包含@符号则为邮箱）
        User user = usernameOrEmail.contains("@")
                ? getUserByEmail(usernameOrEmail)
                : getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, usernameOrEmail));

        // 2. 检查用户是否存在
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 3. 验证密码
        if (!PASSWORD_ENCODER.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_WRONG);
        }

        String token = jwtTokenService.generateToken(user.getId(), user.getUsername());
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

    /**
     * 查询当前登录用户的资料。目标编号必须与认证身份一致。
     *
     * @param id 用户编号
     * @return 用户信息
     * @throws BusinessException 未登录、编号不属于当前用户或用户不存在
     */
    @Override
    public UserResponse getUserById(Long id) {
        return convertToResponse(requireOwnedUser(id));
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

    /**
     * 确认邮箱已注册并准备新密码，消费验证码后更新密码。
     * 数据库写入失败时保留原密码，验证码需要重新获取。
     *
     * @param resetPasswordDTO 邮箱、验证码与新密码
     * @throws BusinessException 邮箱未注册、验证码被拒绝或基础设施不可用；数据库故障保留原因传播
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(UserResetPasswordRequest resetPasswordDTO) {
        // 1. 根据邮箱查询用户
        User user = getUserByEmail(resetPasswordDTO.getEmail());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_EMAIL_NOT_REGISTERED);
        }

        String encodedPassword = PASSWORD_ENCODER.encode(resetPasswordDTO.getNewPassword());
        verificationCodeService.verifyAndConsume(resetPasswordDTO.getEmail(), resetPasswordDTO.getVerificationCode(),
                VerificationCodeScene.RESET_PASSWORD);
        boolean updated = update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getPassword, encodedPassword)
                .set(User::getUpdatedAt, DateTimeUtil.now()));
        if (!updated) {
            throw new BusinessException(ErrorCode.PASSWORD_CHANGE_FAILED);
        }
        log.info("用户密码重置成功: userId={}, email={}", user.getId(), resetPasswordDTO.getEmail());
    }

    /**
     * 确认目标用户就是当前登录用户后，按请求中的非 null 资料字段生成定向更新，并写入当前更新时间。
     * 返回值按查询结果合并这些字段得到。四个资料字段都未提供时直接返回查询结果。
     *
     * @param userId 用户编号
     * @param updateDTO 待更新字段
     * @return 合并请求内容后的用户信息
     * @throws BusinessException 未登录、编号不属于当前用户、用户不存在或写入未成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse updateProfile(Long userId, UserUpdateRequest updateDTO) {
        // 1. 确认目标用户就是当前登录用户
        User user = requireOwnedUser(userId);

        // 2. 只收集本次提供的资料字段
        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId());
        boolean changed = false;
        if (updateDTO.getName() != null) {
            String name = updateDTO.getName().trim();
            user.setName(name);
            update.set(User::getName, name);
            changed = true;
        }
        if (updateDTO.getPhone() != null) {
            String phone = updateDTO.getPhone().trim();
            user.setPhone(phone);
            update.set(User::getPhone, phone);
            changed = true;
        }
        if (updateDTO.getWebsite() != null) {
            String website = updateDTO.getWebsite().trim();
            user.setWebsite(website);
            update.set(User::getWebsite, website);
            changed = true;
        }
        if (updateDTO.getPreferredWorkCity() != null) {
            String city = updateDTO.getPreferredWorkCity().trim();
            user.setPreferredWorkCity(city);
            update.set(User::getPreferredWorkCity, city);
            changed = true;
        }
        if (!changed) {
            return convertToResponse(user);
        }

        // 3. 定向更新资料字段和更新时间
        update.set(User::getUpdatedAt, DateTimeUtil.now());
        if (!update(update)) {
            throw new BusinessException(ErrorCode.USER_UPDATE_FAILED);
        }
        log.info("用户信息更新成功: userId={}", userId);
        return convertToResponse(user);
    }

    /**
     * 确认目标编号属于当前登录用户，并读取该用户。
     * 编号不一致时不查询数据库。
     *
     * @param userId 目标用户编号
     * @return 当前用户
     * @throws BusinessException 未登录、编号不属于当前用户或用户不存在
     */
    private User requireOwnedUser(Long userId) {
        Long currentUserId = UserHolder.requireUserId();
        if (userId == null || !userId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 按邮箱查询用户。
     *
     * @param email 邮箱
     * @return 用户；不存在时返回 {@code null}
     */
    private User getUserByEmail(String email) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
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
