package com.zdmj.userAuthService.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zdmj.common.cache.RedisUtil;
import com.zdmj.common.cache.RedisConstants;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.userAuthService.dto.UserResponse;
import com.zdmj.userAuthService.dto.UserLoginRequest;
import com.zdmj.userAuthService.dto.UserLoginResponse;
import com.zdmj.userAuthService.dto.UserRegisterRequest;
import com.zdmj.userAuthService.dto.UserResetPasswordRequest;
import com.zdmj.userAuthService.dto.UserUpdateRequest;
import com.zdmj.userAuthService.entity.User;
import com.zdmj.userAuthService.enums.VerificationCodeScene;
import com.zdmj.userAuthService.service.VerificationCodeService;
import com.zdmj.userAuthService.util.PasswordUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private VerificationCodeService verificationCodeService;

    @Mock
    private RedisUtil redisUtil;

    private UserServiceImpl userService;
    private static boolean tableInfoInitialized = false;

    @BeforeEach
    void setUp() {
        initMybatisPlusLambdaCache();
        userService = spy(new UserServiceImpl(verificationCodeService, redisUtil));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void register_whenUsernameExists_shouldThrow2001() {
        UserRegisterRequest dto = buildRegisterDTO();
        doReturn(true).when(userService).existsByUsername(dto.getUsername());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(dto));

        assertEquals(ErrorCode.USER_ALREADY_EXISTS.getCode(), ex.getCode());
        verify(userService).existsByUsername(dto.getUsername());
        verify(userService, never()).existsByEmail(anyString());
        verifyNoInteractions(verificationCodeService);
    }

    @Test
    void register_whenEmailExists_shouldThrow2002() {
        UserRegisterRequest dto = buildRegisterDTO();
        doReturn(false).when(userService).existsByUsername(dto.getUsername());
        doReturn(true).when(userService).existsByEmail(dto.getEmail());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(dto));

        assertEquals(ErrorCode.USER_EMAIL_EXISTS.getCode(), ex.getCode());
        verify(userService).existsByUsername(dto.getUsername());
        verify(userService).existsByEmail(dto.getEmail());
        verifyNoInteractions(verificationCodeService);
    }

    @Test
    void register_whenCaptchaInvalid_shouldThrow2003() {
        UserRegisterRequest dto = buildRegisterDTO();
        doReturn(false).when(userService).existsByUsername(dto.getUsername());
        doReturn(false).when(userService).existsByEmail(dto.getEmail());
        doReturn(false).when(verificationCodeService).verifyCode(dto.getEmail(), dto.getVerificationCode(),
                VerificationCodeScene.REGISTER);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(dto));

        assertEquals(ErrorCode.CAPTCHA_ERROR.getCode(), ex.getCode());
        verify(verificationCodeService).verifyCode(dto.getEmail(), dto.getVerificationCode(),
                VerificationCodeScene.REGISTER);
        verify(userService, never()).save(any(User.class));
    }

    @Test
    void register_whenSaveFailed_shouldThrow2004() {
        UserRegisterRequest dto = buildRegisterDTO();
        doReturn(false).when(userService).existsByUsername(dto.getUsername());
        doReturn(false).when(userService).existsByEmail(dto.getEmail());
        doReturn(true).when(verificationCodeService).verifyCode(dto.getEmail(), dto.getVerificationCode(),
                VerificationCodeScene.REGISTER);
        doReturn(false).when(userService).save(any(User.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(dto));

        assertEquals(ErrorCode.USER_REGISTER_FAILED.getCode(), ex.getCode());
        verify(userService).save(any(User.class));
    }

    @Test
    void register_whenAllValid_shouldReturnUserResponseAndEncodePassword() {
        UserRegisterRequest dto = buildRegisterDTO();
        doReturn(false).when(userService).existsByUsername(dto.getUsername());
        doReturn(false).when(userService).existsByEmail(dto.getEmail());
        doReturn(true).when(verificationCodeService).verifyCode(dto.getEmail(), dto.getVerificationCode(),
                VerificationCodeScene.REGISTER);
        doReturn(true).when(userService).save(any(User.class));

        UserResponse result = userService.register(dto);

        assertEquals(dto.getUsername(), result.getUsername());
        assertEquals(dto.getEmail(), result.getEmail());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals(dto.getUsername(), savedUser.getUsername());
        assertEquals(dto.getEmail(), savedUser.getEmail());
        assertNotEquals(dto.getPassword(), savedUser.getPassword());
        assertTrue(PasswordUtil.matches(dto.getPassword(), savedUser.getPassword()));
    }

    @Test
    void login_whenUserNotFound_shouldThrow2006() {
        UserLoginRequest dto = new UserLoginRequest();
        dto.setUsernameOrEmail("ghost");
        dto.setPassword("password123");
        doReturn(null).when(userService).getUserByUsername("ghost");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(dto));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(userService).getUserByUsername("ghost");
        verify(redisUtil, never()).exists(anyString());
    }

    @Test
    void login_whenPasswordWrong_shouldThrow2005() {
        UserLoginRequest dto = new UserLoginRequest();
        dto.setUsernameOrEmail("alice");
        dto.setPassword("wrong-password");
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword(PasswordUtil.encode("right-password"));
        doReturn(user).when(userService).getUserByUsername("alice");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(dto));

        assertEquals(ErrorCode.USER_PASSWORD_WRONG.getCode(), ex.getCode());
        verify(userService).getUserByUsername("alice");
        verify(redisUtil, never()).setString(anyString(), anyString(), anyInt());
    }

    @Test
    void login_whenSuccess_shouldDeleteOldTokenAndWriteNewToken() {
        UserLoginRequest dto = new UserLoginRequest();
        dto.setUsernameOrEmail("alice");
        dto.setPassword("right-password");
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword(PasswordUtil.encode("right-password"));
        doReturn(user).when(userService).getUserByUsername("alice");
        doReturn(true).when(redisUtil).exists(RedisConstants.JWT_TOKEN_KEY + 1L);

        UserLoginResponse response = userService.login(dto);

        assertEquals("alice", response.getUser().getUsername());
        assertEquals(3, response.getToken().split("\\.").length);
        verify(redisUtil).exists(RedisConstants.JWT_TOKEN_KEY + 1L);
        verify(redisUtil).delete(RedisConstants.JWT_TOKEN_KEY + 1L);
        verify(redisUtil).setString(eq(RedisConstants.JWT_TOKEN_KEY + 1L), eq(response.getToken()),
                eq(RedisConstants.JWT_TOKEN_TTL));
    }

    @Test
    void getUserById_whenUserExists_shouldReturnDTO() {
        User user = new User();
        user.setId(123L);
        user.setUsername("u123");
        user.setEmail("u123@test.com");
        doReturn(user).when(userService).getById(123L);

        UserResponse dto = userService.getUserById(123L);

        assertEquals(123L, dto.getId());
        assertEquals("u123", dto.getUsername());
        assertEquals("u123@test.com", dto.getEmail());
        verify(userService).getById(123L);
    }

    @Test
    void getUserById_whenUserMissing_shouldThrow2006() {
        doReturn(null).when(userService).getById(404L);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.getUserById(404L));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(userService).getById(404L);
    }

    @Test
    void existsByUsername_andExistsByEmail_shouldReflectCountResult() {
        doReturn(1L).doReturn(0L).when(userService).count(any(LambdaQueryWrapper.class));

        boolean usernameExists = userService.existsByUsername("alice");
        boolean emailExists = userService.existsByEmail("alice@test.com");

        assertEquals(true, usernameExists);
        assertFalse(emailExists);
        verify(userService, times(2)).count(any(LambdaQueryWrapper.class));
    }

    @Test
    void getUserByUsername_andGetUserByEmail_shouldReturnQueryResult() {
        User byUsername = new User();
        byUsername.setUsername("alice");
        User byEmail = new User();
        byEmail.setEmail("alice@test.com");
        doReturn(byUsername).doReturn(byEmail).when(userService).getOne(any(LambdaQueryWrapper.class));

        User user1 = userService.getUserByUsername("alice");
        User user2 = userService.getUserByEmail("alice@test.com");

        assertEquals("alice", user1.getUsername());
        assertEquals("alice@test.com", user2.getEmail());
        verify(userService, times(2)).getOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void resetPassword_whenEmailNotRegistered_shouldThrow2007() {
        UserResetPasswordRequest dto = buildResetDTO();
        doReturn(null).when(userService).getUserByEmail(dto.getEmail());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.resetPassword(dto));

        assertEquals(ErrorCode.USER_EMAIL_NOT_REGISTERED.getCode(), ex.getCode());
        verify(userService).getUserByEmail(dto.getEmail());
        verifyNoInteractions(verificationCodeService);
    }

    @Test
    void resetPassword_whenCaptchaInvalid_shouldThrow2003() {
        UserResetPasswordRequest dto = buildResetDTO();
        User user = new User();
        user.setId(99L);
        doReturn(user).when(userService).getUserByEmail(dto.getEmail());
        doReturn(false).when(verificationCodeService).verifyCode(dto.getEmail(), dto.getVerificationCode(),
                VerificationCodeScene.RESET_PASSWORD);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.resetPassword(dto));

        assertEquals(ErrorCode.CAPTCHA_ERROR.getCode(), ex.getCode());
        verify(verificationCodeService).verifyCode(dto.getEmail(), dto.getVerificationCode(),
                VerificationCodeScene.RESET_PASSWORD);
        verify(userService, never()).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void resetPassword_whenUpdateFailed_shouldThrow2008() {
        UserResetPasswordRequest dto = buildResetDTO();
        User user = new User();
        user.setId(88L);
        doReturn(user).when(userService).getUserByEmail(dto.getEmail());
        doReturn(true).when(verificationCodeService).verifyCode(dto.getEmail(), dto.getVerificationCode(),
                VerificationCodeScene.RESET_PASSWORD);
        doReturn(false).when(userService).update(any(LambdaUpdateWrapper.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.resetPassword(dto));

        assertEquals(ErrorCode.PASSWORD_CHANGE_FAILED.getCode(), ex.getCode());
        verify(userService).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void resetPassword_whenAllValid_shouldUpdatePasswordSuccessfully() {
        UserResetPasswordRequest dto = buildResetDTO();
        User user = new User();
        user.setId(88L);
        doReturn(user).when(userService).getUserByEmail(dto.getEmail());
        doReturn(true).when(verificationCodeService).verifyCode(dto.getEmail(), dto.getVerificationCode(),
                VerificationCodeScene.RESET_PASSWORD);
        doReturn(true).when(userService).update(any(LambdaUpdateWrapper.class));

        userService.resetPassword(dto);

        verify(userService).getUserByEmail(dto.getEmail());
        verify(verificationCodeService).verifyCode(dto.getEmail(), dto.getVerificationCode(),
                VerificationCodeScene.RESET_PASSWORD);
        verify(userService).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateCurrentUser_whenNameBlank_shouldThrow1001() {
        UserHolder.set(UserContext.of(14L, "u14"));
        UserUpdateRequest dto = new UserUpdateRequest();
        dto.setName("   ");

        User existing = new User();
        existing.setId(14L);
        existing.setName("old-name");
        doReturn(existing).when(userService).getById(14L);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateCurrentUser(dto));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), ex.getCode());
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    void updateCurrentUser_whenUserNotFound_shouldThrow2006() {
        UserHolder.set(UserContext.of(12L, "u12"));
        UserUpdateRequest dto = new UserUpdateRequest();
        dto.setName("new-name");
        doReturn(null).when(userService).getById(12L);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateCurrentUser(dto));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(userService).getById(12L);
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    void updateCurrentUser_whenUpdateFailed_shouldThrow2004() {
        UserHolder.set(UserContext.of(13L, "u13"));
        UserUpdateRequest dto = new UserUpdateRequest();
        dto.setName("neo");

        User existing = new User();
        existing.setId(13L);
        existing.setName("old");

        doReturn(existing).when(userService).getById(13L);
        doReturn(false).when(userService).updateById(any(User.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateCurrentUser(dto));

        assertEquals(ErrorCode.USER_REGISTER_FAILED.getCode(), ex.getCode());
        assertEquals("neo", existing.getName());
        verify(userService).updateById(existing);
    }

    @Test
    void updateCurrentUser_whenSuccess_shouldOnlyUpdateNonNullFields() {
        UserHolder.set(UserContext.of(15L, "u15"));
        UserUpdateRequest dto = new UserUpdateRequest();
        dto.setName("new-name");
        dto.setPhone(null);
        dto.setWebsite(null);

        User existing = new User();
        existing.setId(15L);
        existing.setName("old-name");
        existing.setPhone("13800000000");
        existing.setWebsite("https://old.site");

        doReturn(existing).when(userService).getById(15L);
        doReturn(true).when(userService).updateById(existing);

        UserResponse result = userService.updateCurrentUser(dto);

        assertEquals("new-name", result.getName());
        assertEquals("13800000000", result.getPhone());
        assertEquals("https://old.site", result.getWebsite());
        verify(userService).updateById(existing);
        assertEquals("new-name", existing.getName());
        assertEquals("13800000000", existing.getPhone());
        assertEquals("https://old.site", existing.getWebsite());
    }

    private UserRegisterRequest buildRegisterDTO() {
        UserRegisterRequest dto = new UserRegisterRequest();
        dto.setUsername("alice");
        dto.setPassword("Password123");
        dto.setEmail("alice@test.com");
        dto.setVerificationCode("123456");
        return dto;
    }

    private UserResetPasswordRequest buildResetDTO() {
        UserResetPasswordRequest dto = new UserResetPasswordRequest();
        dto.setEmail("alice@test.com");
        dto.setVerificationCode("123456");
        dto.setNewPassword("newPassword123");
        return dto;
    }

    private static void initMybatisPlusLambdaCache() {
        if (tableInfoInitialized) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, User.class);
        tableInfoInitialized = true;
    }
}
