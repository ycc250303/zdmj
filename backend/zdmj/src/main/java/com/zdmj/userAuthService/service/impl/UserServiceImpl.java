package com.zdmj.userAuthService.service.impl;

import com.zdmj.common.util.JwtUtil;
import com.zdmj.common.util.PasswordUtil;
import com.zdmj.exception.BusinessException;
import com.zdmj.userAuthService.dto.UserDTO;
import com.zdmj.userAuthService.dto.UserLoginDTO;
import com.zdmj.userAuthService.dto.UserLoginResponseDTO;
import com.zdmj.userAuthService.dto.UserRegisterDTO;
import com.zdmj.userAuthService.dto.UserResetPasswordDTO;
import com.zdmj.userAuthService.entity.User;
import com.zdmj.userAuthService.mapper.UserMapper;
import com.zdmj.userAuthService.service.UserService;
import com.zdmj.userAuthService.service.VerificationCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDTO register(UserRegisterDTO registerDTO) {
        // 1. 检查用户名是否已存在
        if (existsByUsername(registerDTO.getUsername())) {
            throw new BusinessException(400, "用户名已存在");
        }

        // 2. 检查邮箱是否已存在
        if (existsByEmail(registerDTO.getEmail())) {
            throw new BusinessException(400, "邮箱已被注册");
        }

        // 3. 验证验证码
        if (!verificationCodeService.verifyCode(registerDTO.getEmail(), registerDTO.getVerificationCode())) {
            throw new BusinessException(400, "验证码错误或已过期");
        }

        // 4. 创建用户对象
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(PasswordUtil.encode(registerDTO.getPassword())); // 加密密码
        user.setEmail(registerDTO.getEmail());
        user.setCreateAt(LocalDateTime.now());
        user.setUpdateAt(LocalDateTime.now());

        // 5. 保存到数据库
        int result = userMapper.insert(user);
        if (result <= 0) {
            throw new BusinessException(500, "用户注册失败");
        }

        log.info("用户注册成功: {}", user.getUsername());

        // 6. 转换为DTO返回
        return convertToDTO(user);
    }

    @Override
    public UserLoginResponseDTO login(UserLoginDTO loginDTO) {
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
            throw new BusinessException(400, "用户名或密码错误");
        }

        // 3. 验证密码
        if (!PasswordUtil.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(400, "用户名或密码错误");
        }

        log.info("用户登录成功: {}", user.getUsername());

        // 4. 生成JWT Token
        String token = JwtUtil.generateToken(user.getId(), user.getUsername());

        // 5. 构建登录响应
        UserLoginResponseDTO response = new UserLoginResponseDTO();
        response.setToken(token);
        response.setUser(convertToDTO(user));

        return response;
    }

    @Override
    public UserDTO getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return convertToDTO(user);
    }

    @Override
    public User getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public User getUserByEmail(String email) {
        return userMapper.selectByEmail(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.countByUsername(username) > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        return userMapper.countByEmail(email) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(UserResetPasswordDTO resetPasswordDTO) {
        // 1. 根据邮箱查询用户
        User user = getUserByEmail(resetPasswordDTO.getEmail());
        if (user == null) {
            throw new BusinessException(404, "该邮箱未注册");
        }

        // 2. 验证验证码
        if (!verificationCodeService.verifyCode(resetPasswordDTO.getEmail(), resetPasswordDTO.getVerificationCode())) {
            throw new BusinessException(400, "验证码错误或已过期");
        }

        // 3. 加密新密码
        String encodedPassword = PasswordUtil.encode(resetPasswordDTO.getNewPassword());

        // 4. 更新密码
        int result = userMapper.updatePassword(user.getId(), encodedPassword);
        if (result <= 0) {
            throw new BusinessException(500, "密码修改失败");
        }

        log.info("用户密码重置成功: userId={}, email={}", user.getId(), resetPasswordDTO.getEmail());
    }

    /**
     * 将User实体转换为UserDTO
     * 
     * @param user 用户实体
     * @return 用户DTO
     */
    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();
        BeanUtils.copyProperties(user, dto);
        return dto;
    }
}
