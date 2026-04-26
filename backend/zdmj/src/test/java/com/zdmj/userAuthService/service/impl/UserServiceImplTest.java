package com.zdmj.userAuthService.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zdmj.common.cache.RedisConstants;
import com.zdmj.common.cache.RedisUtil;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.userAuthService.dto.UserLoginDTO;
import com.zdmj.userAuthService.dto.UserLoginResponseDTO;
import com.zdmj.userAuthService.dto.UserRegisterDTO;
import com.zdmj.userAuthService.dto.UserResetPasswordDTO;
import com.zdmj.userAuthService.dto.UserUpdateDTO;
import com.zdmj.userAuthService.entity.User;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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
    void register_success_shouldEncryptPasswordAndReturnDto() {
        UserRegisterDTO dto = buildRegisterDto();
        doReturn(false).when(userService).existsByUsername(dto.getUsername());
        doReturn(false).when(userService).existsByEmail(dto.getEmail());
        when(verificationCodeService.verifyCode(dto.getEmail(), dto.getVerificationCode())).thenReturn(true);
        doReturn(true).when(userService).save(any(User.class));

        var result = userService.register(dto);

        assertEquals(dto.getUsername(), result.getUsername());
        assertEquals(dto.getEmail(), result.getEmail());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertNotEquals(dto.getPassword(), savedUser.getPassword());
        assertTrue(PasswordUtil.matches(dto.getPassword(), savedUser.getPassword()));
    }

    @Test
    void register_username_exists_shouldThrowBusinessException() {
        UserRegisterDTO dto = buildRegisterDto();
        doReturn(true).when(userService).existsByUsername(dto.getUsername());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(dto));

        assertEquals(ErrorCode.USER_ALREADY_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void register_email_exists_shouldThrowBusinessException() {
        UserRegisterDTO dto = buildRegisterDto();
        doReturn(false).when(userService).existsByUsername(dto.getUsername());
        doReturn(true).when(userService).existsByEmail(dto.getEmail());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(dto));

        assertEquals(ErrorCode.USER_EMAIL_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void register_captcha_invalid_shouldThrowBusinessException() {
        UserRegisterDTO dto = buildRegisterDto();
        doReturn(false).when(userService).existsByUsername(dto.getUsername());
        doReturn(false).when(userService).existsByEmail(dto.getEmail());
        when(verificationCodeService.verifyCode(dto.getEmail(), dto.getVerificationCode())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(dto));

        assertEquals(ErrorCode.CAPTCHA_ERROR.getCode(), ex.getCode());
    }

    @Test
    void register_save_failed_shouldThrowBusinessException() {
        UserRegisterDTO dto = buildRegisterDto();
        doReturn(false).when(userService).existsByUsername(dto.getUsername());
        doReturn(false).when(userService).existsByEmail(dto.getEmail());
        when(verificationCodeService.verifyCode(dto.getEmail(), dto.getVerificationCode())).thenReturn(true);
        doReturn(false).when(userService).save(any(User.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(dto));

        assertEquals(ErrorCode.USER_REGISTER_FAILED.getCode(), ex.getCode());
    }

    @Test
    void login_success_by_username_shouldReturnTokenAndStoreInRedis() {
        User user = buildUser(10L, "tester", "pwd123456", "test@zdmj.com");
        UserLoginDTO loginDTO = new UserLoginDTO();
        loginDTO.setUsernameOrEmail("tester");
        loginDTO.setPassword("pwd123456");
        doReturn(user).when(userService).getUserByUsername("tester");
        when(redisUtil.exists(RedisConstants.JWT_TOKEN_KEY + user.getId())).thenReturn(false);

        UserLoginResponseDTO response = userService.login(loginDTO);

        assertNotNull(response.getToken());
        assertEquals(user.getUsername(), response.getUser().getUsername());
        verify(redisUtil).setString(RedisConstants.JWT_TOKEN_KEY + user.getId(), response.getToken(), RedisConstants.JWT_TOKEN_TTL);
    }

    @Test
    void login_success_by_email_with_old_token_shouldDeleteOldTokenFirst() {
        User user = buildUser(11L, "tester2", "pwd123456", "test2@zdmj.com");
        UserLoginDTO loginDTO = new UserLoginDTO();
        loginDTO.setUsernameOrEmail(user.getEmail());
        loginDTO.setPassword("pwd123456");
        doReturn(user).when(userService).getUserByEmail(user.getEmail());
        when(redisUtil.exists(RedisConstants.JWT_TOKEN_KEY + user.getId())).thenReturn(true);

        UserLoginResponseDTO response = userService.login(loginDTO);

        assertNotNull(response.getToken());
        String key = RedisConstants.JWT_TOKEN_KEY + user.getId();
        verify(redisUtil).delete(key);
        verify(redisUtil).setString(key, response.getToken(), RedisConstants.JWT_TOKEN_TTL);
    }

    @Test
    void login_user_not_found_shouldThrowBusinessException() {
        UserLoginDTO loginDTO = new UserLoginDTO();
        loginDTO.setUsernameOrEmail("missing");
        loginDTO.setPassword("pwd");
        doReturn(null).when(userService).getUserByUsername("missing");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(loginDTO));

        assertEquals(ErrorCode.USER_PASSWORD_WRONG.getCode(), ex.getCode());
    }

    @Test
    void login_password_wrong_shouldThrowBusinessException() {
        User user = buildUser(12L, "tester3", "rightPwd", "test3@zdmj.com");
        UserLoginDTO loginDTO = new UserLoginDTO();
        loginDTO.setUsernameOrEmail("tester3");
        loginDTO.setPassword("wrongPwd");
        doReturn(user).when(userService).getUserByUsername("tester3");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(loginDTO));

        assertEquals(ErrorCode.USER_PASSWORD_WRONG.getCode(), ex.getCode());
        verifyNoMoreInteractions(redisUtil);
    }

    @Test
    void getUserById_success_shouldReturnDto() {
        User user = buildUser(20L, "u20", "pwd", "u20@zdmj.com");
        doReturn(user).when(userService).getById(20L);

        var dto = userService.getUserById(20L);

        assertEquals(20L, dto.getId());
        assertEquals("u20", dto.getUsername());
    }

    @Test
    void getUserById_notFound_shouldThrowBusinessException() {
        doReturn(null).when(userService).getById(anyLong());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.getUserById(99L));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void resetPassword_success_shouldUpdatePassword() {
        User user = buildUser(30L, "u30", "oldPwd", "u30@zdmj.com");
        UserResetPasswordDTO dto = new UserResetPasswordDTO();
        dto.setEmail(user.getEmail());
        dto.setVerificationCode("123456");
        dto.setNewPassword("newPwd123");
        doReturn(user).when(userService).getUserByEmail(user.getEmail());
        when(verificationCodeService.verifyCode(dto.getEmail(), dto.getVerificationCode())).thenReturn(true);
        doReturn(true).when(userService).update(any());

        userService.resetPassword(dto);

        verify(userService).update(any());
    }

    @Test
    void resetPassword_email_not_registered_shouldThrowBusinessException() {
        UserResetPasswordDTO dto = new UserResetPasswordDTO();
        dto.setEmail("missing@zdmj.com");
        dto.setVerificationCode("123456");
        dto.setNewPassword("newPwd123");
        doReturn(null).when(userService).getUserByEmail(dto.getEmail());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.resetPassword(dto));

        assertEquals(ErrorCode.USER_EMAIL_NOT_REGISTERED.getCode(), ex.getCode());
    }

    @Test
    void resetPassword_captcha_invalid_shouldThrowBusinessException() {
        User user = buildUser(31L, "u31", "oldPwd", "u31@zdmj.com");
        UserResetPasswordDTO dto = new UserResetPasswordDTO();
        dto.setEmail(user.getEmail());
        dto.setVerificationCode("123456");
        dto.setNewPassword("newPwd123");
        doReturn(user).when(userService).getUserByEmail(user.getEmail());
        when(verificationCodeService.verifyCode(dto.getEmail(), dto.getVerificationCode())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.resetPassword(dto));

        assertEquals(ErrorCode.CAPTCHA_ERROR.getCode(), ex.getCode());
    }

    @Test
    void resetPassword_update_fail_shouldThrowBusinessException() {
        User user = buildUser(32L, "u32", "oldPwd", "u32@zdmj.com");
        UserResetPasswordDTO dto = new UserResetPasswordDTO();
        dto.setEmail(user.getEmail());
        dto.setVerificationCode("123456");
        dto.setNewPassword("newPwd123");
        doReturn(user).when(userService).getUserByEmail(user.getEmail());
        when(verificationCodeService.verifyCode(dto.getEmail(), dto.getVerificationCode())).thenReturn(true);
        doReturn(false).when(userService).update(any());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.resetPassword(dto));

        assertEquals(ErrorCode.PASSWORD_CHANGE_FAILED.getCode(), ex.getCode());
    }

    @Test
    void updateCurrentUser_success_shouldUpdateAllowedFieldsOnly() {
        UserHolder.set(UserContext.of(40L, "u40", "u40@zdmj.com"));
        User user = buildUser(40L, "u40", "pwd", "u40@zdmj.com");
        user.setName("oldName");
        user.setPhone("oldPhone");
        user.setWebsite("oldSite");
        doReturn(user).when(userService).getById(40L);
        doReturn(true).when(userService).updateById(any(User.class));

        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setName("newName");
        dto.setPhone("newPhone");

        var result = userService.updateCurrentUser(dto);

        assertEquals("newName", result.getName());
        assertEquals("newPhone", result.getPhone());
        assertEquals("oldSite", result.getWebsite());
    }

    @Test
    void updateCurrentUser_notLoggedIn_shouldThrowBusinessException() {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setName("newName");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateCurrentUser(dto));

        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), ex.getCode());
    }

    @Test
    void updateCurrentUser_userNotFound_shouldThrowBusinessException() {
        UserHolder.set(UserContext.of(41L, "u41", "u41@zdmj.com"));
        doReturn(null).when(userService).getById(41L);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateCurrentUser(new UserUpdateDTO()));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void updateCurrentUser_updateFailed_shouldThrowBusinessException() {
        UserHolder.set(UserContext.of(42L, "u42", "u42@zdmj.com"));
        User user = buildUser(42L, "u42", "pwd", "u42@zdmj.com");
        doReturn(user).when(userService).getById(42L);
        doReturn(false).when(userService).updateById(any(User.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateCurrentUser(new UserUpdateDTO()));

        assertEquals(ErrorCode.USER_REGISTER_FAILED.getCode(), ex.getCode());
    }

    private UserRegisterDTO buildRegisterDto() {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUsername("new_user");
        dto.setPassword("password123");
        dto.setEmail("new_user@zdmj.com");
        dto.setVerificationCode("123456");
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

    private User buildUser(Long id, String username, String rawPassword, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(PasswordUtil.encode(rawPassword));
        return user;
    }
}
