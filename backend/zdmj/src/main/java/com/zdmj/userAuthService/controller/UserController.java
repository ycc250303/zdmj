package com.zdmj.userAuthService.controller;

import com.zdmj.common.Result;
import com.zdmj.userAuthService.dto.UserDTO;
import com.zdmj.userAuthService.dto.UserLoginDTO;
import com.zdmj.userAuthService.dto.UserLoginResponseDTO;
import com.zdmj.userAuthService.dto.UserRegisterDTO;
import com.zdmj.userAuthService.dto.UserResetPasswordDTO;
import com.zdmj.userAuthService.service.UserService;
import com.zdmj.userAuthService.service.VerificationCodeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private VerificationCodeService verificationCodeService;

    /**
     * 发送验证码
     *
     * @param email 邮箱地址
     * @return 发送结果
     */
    @PostMapping("/send-verification-code")
    public Result<String> sendVerificationCode(
            @RequestParam @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email) {
        log.info("发送验证码请求: {}", email);
        boolean success = verificationCodeService.sendVerificationCode(email);
        if (success) {
            return Result.success("验证码已发送到邮箱", null);
        } else {
            return Result.error("验证码发送失败，请稍后重试");
        }
    }

    /**
     * 用户注册
     * 
     * @param registerDTO 注册信息
     * @return 注册结果
     */
    @PostMapping("/register")
    public Result<UserDTO> register(@Valid @RequestBody UserRegisterDTO registerDTO) {
        log.info("用户注册请求: {}", registerDTO.getUsername());
        UserDTO userDTO = userService.register(registerDTO);
        return Result.success("注册成功", userDTO);
    }

    /**
     * 用户登录
     * 
     * @param loginDTO 登录信息
     * @return 登录结果（包含Token和用户信息）
     */
    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginDTO loginDTO) {
        log.info("用户登录请求: {}", loginDTO.getUsernameOrEmail());
        UserLoginResponseDTO response = userService.login(loginDTO);
        return Result.success("登录成功", response);
    }

    /**
     * 根据ID查询用户信息
     * 
     * @param id 用户ID
     * @return 用户信息
     */
    @GetMapping("/{id}")
    public Result<UserDTO> getUserById(@PathVariable Long id) {
        log.info("查询用户信息: {}", id);
        UserDTO userDTO = userService.getUserById(id);
        return Result.success(userDTO);
    }

    /**
     * 检查用户名是否存在
     * 
     * @param username 用户名
     * @return 是否存在
     */
    @GetMapping("/check-username")
    public Result<Boolean> checkUsername(@RequestParam String username) {
        log.info("检查用户名是否存在: {}", username);
        boolean exists = userService.existsByUsername(username);
        return Result.success(exists);
    }

    /**
     * 检查邮箱是否存在
     * 
     * @param email 邮箱
     * @return 是否存在
     */
    @GetMapping("/check-email")
    public Result<Boolean> checkEmail(@RequestParam String email) {
        log.info("检查邮箱是否存在: {}", email);
        boolean exists = userService.existsByEmail(email);
        return Result.success(exists);
    }

    /**
     * 重置密码（忘记密码时使用）
     * 
     * @param resetPasswordDTO 重置密码信息（包含邮箱、验证码、新密码）
     * @return 重置结果
     */
    @PostMapping("/reset-password")
    public Result<String> resetPassword(@Valid @RequestBody UserResetPasswordDTO resetPasswordDTO) {
        log.info("重置密码请求: email={}", resetPasswordDTO.getEmail());
        userService.resetPassword(resetPasswordDTO);
        return Result.success("密码修改成功", null);
    }
}
