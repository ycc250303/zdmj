package com.zdmj.userAuthService.service.impl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.security.JwtSessionStore;
import com.zdmj.common.security.JwtTokenService;
import com.zdmj.userAuthService.dto.UserResponse;
import com.zdmj.userAuthService.dto.UserLoginRequest;
import com.zdmj.userAuthService.dto.UserLoginResponse;
import com.zdmj.userAuthService.dto.UserRegisterRequest;
import com.zdmj.userAuthService.dto.UserResetPasswordRequest;
import com.zdmj.userAuthService.dto.UserUpdateRequest;
import com.zdmj.userAuthService.entity.User;
import com.zdmj.userAuthService.enums.VerificationCodeScene;
import com.zdmj.userAuthService.service.VerificationCodeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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
    private JwtSessionStore jwtSessionStore;

    private static final String TEST_JWT_SECRET =
            "test-jwt-secret-key-for-jwt-token-generation-2024-very-long-secret-key";
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private UserLoginServiceImpl userService;
    private static boolean tableInfoInitialized = false;

    @BeforeEach
    void setUp() {
        initMybatisPlusLambdaCache();
        userService = spy(new UserLoginServiceImpl(
                verificationCodeService, jwtSessionStore, new JwtTokenService(TEST_JWT_SECRET)));
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
        doThrow(new BusinessException(ErrorCode.CAPTCHA_ERROR)).when(verificationCodeService)
                .verifyAndConsume(eq(dto.getEmail()), eq(dto.getVerificationCode()), eq(VerificationCodeScene.REGISTER));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(dto));

        assertEquals(ErrorCode.CAPTCHA_ERROR.getCode(), ex.getCode());
        verify(verificationCodeService).verifyAndConsume(eq(dto.getEmail()), eq(dto.getVerificationCode()),
                eq(VerificationCodeScene.REGISTER));
        verify(userService, never()).save(any(User.class));
    }

    @Test
    void register_whenSaveFailed_shouldThrow2004() {
        UserRegisterRequest dto = buildRegisterDTO();
        doReturn(false).when(userService).existsByUsername(dto.getUsername());
        doReturn(false).when(userService).existsByEmail(dto.getEmail());
        doReturn(false).when(userService).save(any(User.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(dto));

        assertEquals(ErrorCode.USER_REGISTER_FAILED.getCode(), ex.getCode());
        var order = org.mockito.Mockito.inOrder(verificationCodeService, userService);
        order.verify(verificationCodeService).verifyAndConsume(dto.getEmail(), dto.getVerificationCode(),
                VerificationCodeScene.REGISTER);
        order.verify(userService).save(any(User.class));
    }

    @Test
    void register_whenAllValid_shouldReturnUserResponseAndEncodePassword() {
        UserRegisterRequest dto = buildRegisterDTO();
        doReturn(false).when(userService).existsByUsername(dto.getUsername());
        doReturn(false).when(userService).existsByEmail(dto.getEmail());
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
        assertTrue(PASSWORD_ENCODER.matches(dto.getPassword(), savedUser.getPassword()));
    }

    @Test
    void login_whenUserNotFound_shouldThrow2006() {
        UserLoginRequest dto = new UserLoginRequest();
        dto.setUsernameOrEmail("ghost");
        dto.setPassword("password123");
        doReturn(null).when(userService).getOne(any(LambdaQueryWrapper.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(dto));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(userService).getOne(any(LambdaQueryWrapper.class));
        verify(jwtSessionStore, never()).replace(anyLong(), anyString());
    }

    @Test
    void login_whenPasswordWrong_shouldThrow2005() {
        UserLoginRequest dto = new UserLoginRequest();
        dto.setUsernameOrEmail("alice");
        dto.setPassword("wrong-password");
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword(PASSWORD_ENCODER.encode("right-password"));
        doReturn(user).when(userService).getOne(any(LambdaQueryWrapper.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(dto));

        assertEquals(ErrorCode.USER_PASSWORD_WRONG.getCode(), ex.getCode());
        verify(userService).getOne(any(LambdaQueryWrapper.class));
        verify(jwtSessionStore, never()).replace(anyLong(), anyString());
    }

    @Test
    void login_whenSuccess_shouldReplaceSessionAndReturnToken() {
        UserLoginRequest dto = new UserLoginRequest();
        dto.setUsernameOrEmail("alice");
        dto.setPassword("right-password");
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword(PASSWORD_ENCODER.encode("right-password"));
        doReturn(user).when(userService).getOne(any(LambdaQueryWrapper.class));

        UserLoginResponse response = userService.login(dto);

        assertEquals("alice", response.getUser().getUsername());
        assertEquals(3, response.getToken().split("\\.").length);
        verify(jwtSessionStore).replace(eq(1L), eq(response.getToken()));
    }

    @Test
    void login_whenEmail_shouldReturnQueriedUser() {
        UserLoginRequest dto = new UserLoginRequest();
        dto.setUsernameOrEmail("alice@test.com");
        dto.setPassword("right-password");
        User user = new User();
        user.setId(2L);
        user.setUsername("alice");
        user.setEmail("alice@test.com");
        user.setPassword(PASSWORD_ENCODER.encode("right-password"));
        doReturn(user).when(userService).getOne(any(LambdaQueryWrapper.class));

        UserLoginResponse response = userService.login(dto);

        assertEquals("alice@test.com", response.getUser().getEmail());
        verify(userService).getOne(any(LambdaQueryWrapper.class));
        verify(jwtSessionStore).replace(eq(2L), eq(response.getToken()));
    }

    @Test
    void login_whenSessionStoreFails_shouldThrow1012AndNotReturnToken() {
        UserLoginRequest dto = new UserLoginRequest();
        dto.setUsernameOrEmail("alice");
        dto.setPassword("right-password");
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword(PASSWORD_ENCODER.encode("right-password"));
        doReturn(user).when(userService).getOne(any(LambdaQueryWrapper.class));
        doThrow(new RedisConnectionFailureException("down")).when(jwtSessionStore).replace(eq(1L), anyString());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(dto));

        assertEquals(ErrorCode.AUTH_STORE_UNAVAILABLE.getCode(), ex.getCode());
        verify(jwtSessionStore).replace(eq(1L), anyString());
    }

    @Test
    void getUserById_whenUserExists_shouldReturnDTO() {
        User user = new User();
        user.setId(123L);
        user.setUsername("u123");
        user.setEmail("u123@test.com");
        loginAs(123L);
        doReturn(user).when(userService).getById(123L);

        UserResponse dto = userService.getUserById(123L);

        assertEquals(123L, dto.getId());
        assertEquals("u123", dto.getUsername());
        assertEquals("u123@test.com", dto.getEmail());
        verify(userService).getById(123L);
    }

    @Test
    void getUserById_whenUserMissing_shouldThrow2006() {
        loginAs(404L);
        doReturn(null).when(userService).getById(404L);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.getUserById(404L));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(userService).getById(404L);
    }

    @Test
    void getUserById_whenOtherUser_shouldThrow1003AndNotQuery() {
        loginAs(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.getUserById(123L));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        assertEquals(ErrorCode.NO_PERMISSION.getMessage(), ex.getMessage());
        verify(userService, never()).getById(anyLong());
    }

    @Test
    void getUserById_whenNotLogin_shouldThrow1002AndNotQuery() {
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.getUserById(123L));

        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), ex.getCode());
        verify(userService, never()).getById(anyLong());
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
    void resetPassword_whenEmailNotRegistered_shouldThrow2007() {
        UserResetPasswordRequest dto = buildResetDTO();
        doReturn(null).when(userService).getOne(any(LambdaQueryWrapper.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.resetPassword(dto));

        assertEquals(ErrorCode.USER_EMAIL_NOT_REGISTERED.getCode(), ex.getCode());
        verify(userService).getOne(any(LambdaQueryWrapper.class));
        verifyNoInteractions(verificationCodeService);
    }

    @Test
    void resetPassword_whenCaptchaInvalid_shouldThrow2003() {
        UserResetPasswordRequest dto = buildResetDTO();
        User user = new User();
        user.setId(99L);
        doReturn(user).when(userService).getOne(any(LambdaQueryWrapper.class));
        doThrow(new BusinessException(ErrorCode.CAPTCHA_ERROR)).when(verificationCodeService)
                .verifyAndConsume(eq(dto.getEmail()), eq(dto.getVerificationCode()), eq(VerificationCodeScene.RESET_PASSWORD));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.resetPassword(dto));

        assertEquals(ErrorCode.CAPTCHA_ERROR.getCode(), ex.getCode());
        verify(verificationCodeService).verifyAndConsume(eq(dto.getEmail()), eq(dto.getVerificationCode()),
                eq(VerificationCodeScene.RESET_PASSWORD));
        verify(userService, never()).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void resetPassword_whenUpdateFailed_shouldThrow2008() {
        UserResetPasswordRequest dto = buildResetDTO();
        User user = new User();
        user.setId(88L);
        doReturn(user).when(userService).getOne(any(LambdaQueryWrapper.class));
        doReturn(false).when(userService).update(any(LambdaUpdateWrapper.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.resetPassword(dto));

        assertEquals(ErrorCode.PASSWORD_CHANGE_FAILED.getCode(), ex.getCode());
        var order = org.mockito.Mockito.inOrder(verificationCodeService, userService);
        order.verify(verificationCodeService).verifyAndConsume(dto.getEmail(), dto.getVerificationCode(),
                VerificationCodeScene.RESET_PASSWORD);
        order.verify(userService).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void resetPassword_whenAllValid_shouldUpdatePasswordSuccessfully() {
        UserResetPasswordRequest dto = buildResetDTO();
        User user = new User();
        user.setId(88L);
        doReturn(user).when(userService).getOne(any(LambdaQueryWrapper.class));
        doReturn(true).when(userService).update(any(LambdaUpdateWrapper.class));

        userService.resetPassword(dto);

        verify(userService).getOne(any(LambdaQueryWrapper.class));
        verify(verificationCodeService).verifyAndConsume(eq(dto.getEmail()), eq(dto.getVerificationCode()),
                eq(VerificationCodeScene.RESET_PASSWORD));
        verify(userService).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateProfile_whenNameBlank_shouldStoreEmptyString() {
        UserUpdateRequest dto = new UserUpdateRequest();
        dto.setName("   ");
        dto.setPhone("  ");
        dto.setWebsite(" ");

        User existing = profileUser(14L, "old-name", "13800000000", "https://old.site", "上海");
        loginAs(14L);
        doReturn(existing).when(userService).getById(14L);
        doReturn(true).when(userService).update(any(LambdaUpdateWrapper.class));

        UserResponse result = userService.updateProfile(14L, dto);

        assertEquals("", result.getName());
        assertEquals("", result.getPhone());
        assertEquals("", result.getWebsite());
        assertEquals("上海", result.getPreferredWorkCity());
        LambdaUpdateWrapper<User> update = captureProfileUpdate();
        assertEquals(List.of("name", "phone", "website", "updated_at"), columnsOf(update));
        assertProfileValues(update, 14L, "", "", "");
        assertProfileIdentityUntouched(update, existing);
    }

    @Test
    void updateProfile_whenUserNotFound_shouldThrow2006() {
        UserUpdateRequest dto = new UserUpdateRequest();
        dto.setName("new-name");
        loginAs(12L);
        doReturn(null).when(userService).getById(12L);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateProfile(12L, dto));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(userService).getById(12L);
        verify(userService, never()).update(any(LambdaUpdateWrapper.class));
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    void updateProfile_whenUpdateFailed_shouldThrow2017() {
        UserUpdateRequest dto = new UserUpdateRequest();
        dto.setName("neo");

        User existing = profileUser(13L, "old", null, null, null);
        loginAs(13L);
        doReturn(existing).when(userService).getById(13L);
        doReturn(false).when(userService).update(any(LambdaUpdateWrapper.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateProfile(13L, dto));

        assertEquals(ErrorCode.USER_UPDATE_FAILED.getCode(), ex.getCode());
        assertEquals(List.of("name", "updated_at"), columnsOf(captureProfileUpdate()));
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    void updateProfile_whenSuccess_shouldOnlyUpdateNonNullFields() {
        UserUpdateRequest dto = new UserUpdateRequest();
        dto.setName("new-name");
        dto.setPhone(null);
        dto.setWebsite(null);

        User existing = profileUser(15L, "old-name", "13800000000", "https://old.site", "上海");
        loginAs(15L);
        doReturn(existing).when(userService).getById(15L);
        doReturn(true).when(userService).update(any(LambdaUpdateWrapper.class));

        UserResponse result = userService.updateProfile(15L, dto);

        assertEquals("new-name", result.getName());
        assertEquals("13800000000", result.getPhone());
        assertEquals("https://old.site", result.getWebsite());
        assertEquals("上海", result.getPreferredWorkCity());
        LambdaUpdateWrapper<User> update = captureProfileUpdate();
        assertEquals(List.of("name", "updated_at"), columnsOf(update));
        assertProfileValues(update, 15L, "new-name");
        assertProfileIdentityUntouched(update, existing);
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    void updateProfile_whenFieldsPresent_shouldTrimAndKeepNullFields() {
        UserUpdateRequest dto = new UserUpdateRequest();
        dto.setName("  张三  ");
        dto.setWebsite("  https://example.com  ");
        dto.setPreferredWorkCity("   ");

        User existing = profileUser(16L, "old-name", "13800000000", "https://old.site", "上海");
        loginAs(16L);
        doReturn(existing).when(userService).getById(16L);
        doReturn(true).when(userService).update(any(LambdaUpdateWrapper.class));

        UserResponse result = userService.updateProfile(16L, dto);

        assertEquals("张三", result.getName());
        assertEquals("13800000000", result.getPhone());
        assertEquals("https://example.com", result.getWebsite());
        assertEquals("", result.getPreferredWorkCity());
        LambdaUpdateWrapper<User> update = captureProfileUpdate();
        assertEquals(List.of("name", "website", "preferred_work_city", "updated_at"), columnsOf(update));
        assertProfileValues(update, 16L, "张三", "https://example.com", "");
        assertProfileIdentityUntouched(update, existing);
    }

    @Test
    void updateProfile_whenNoFieldProvided_shouldNotWrite() {
        User existing = profileUser(17L, "old-name", "13800000000", "https://old.site", "上海");
        loginAs(17L);
        doReturn(existing).when(userService).getById(17L);

        UserResponse result = userService.updateProfile(17L, new UserUpdateRequest());

        assertEquals("old-name", result.getName());
        assertEquals("13800000000", result.getPhone());
        assertEquals("https://old.site", result.getWebsite());
        assertEquals("上海", result.getPreferredWorkCity());
        assertEquals("alice", result.getUsername());
        assertEquals("alice@test.com", result.getEmail());
        verify(userService, never()).update(any(LambdaUpdateWrapper.class));
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    void updateProfile_whenOtherUser_shouldThrow1003AndNotQuery() {
        loginAs(1L);
        UserUpdateRequest dto = new UserUpdateRequest();
        dto.setName("neo");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateProfile(14L, dto));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        assertEquals(ErrorCode.NO_PERMISSION.getMessage(), ex.getMessage());
        verify(userService, never()).getById(anyLong());
        verify(userService, never()).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateProfile_whenNotLogin_shouldThrow1002AndNotQuery() {
        UserUpdateRequest dto = new UserUpdateRequest();
        dto.setName("neo");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateProfile(14L, dto));

        assertEquals(ErrorCode.USER_NOT_LOGIN.getCode(), ex.getCode());
        verify(userService, never()).getById(anyLong());
        verify(userService, never()).update(any(LambdaUpdateWrapper.class));
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

    private static void loginAs(Long userId) {
        UserHolder.set(UserContext.of(userId, "u" + userId));
    }

    private User profileUser(Long id, String name, String phone, String website, String city) {
        User user = new User();
        user.setId(id);
        user.setUsername("alice");
        user.setPassword("old-password-hash");
        user.setEmail("alice@test.com");
        user.setName(name);
        user.setPhone(phone);
        user.setWebsite(website);
        user.setPreferredWorkCity(city);
        return user;
    }

    private LambdaUpdateWrapper<User> captureProfileUpdate() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<User>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userService).update(captor.capture());
        return captor.getValue();
    }

    private static List<String> columnsOf(LambdaUpdateWrapper<User> update) {
        String sqlSet = update.getSqlSet();
        assertNotNull(sqlSet);
        return Arrays.stream(sqlSet.split(","))
                .map(segment -> segment.substring(0, segment.indexOf('=')).trim())
                .toList();
    }

    private static List<Object> valuesOf(LambdaUpdateWrapper<User> update) {
        return List.copyOf(update.getParamNameValuePairs().values());
    }

    private static void assertProfileValues(LambdaUpdateWrapper<User> update, Long userId, Object... fields) {
        String where = update.getSqlSegment();
        assertNotNull(where);
        assertTrue(where.contains("id"));
        List<Object> values = valuesOf(update);
        assertTrue(values.contains(userId));
        for (Object field : fields) {
            assertTrue(values.contains(field));
        }
        assertEquals(1, values.stream().filter(LocalDateTime.class::isInstance).count());
        assertEquals(fields.length + 2, values.size());
    }

    private static void assertProfileIdentityUntouched(LambdaUpdateWrapper<User> update, User existing) {
        String sqlSet = update.getSqlSet();
        assertFalse(sqlSet.contains("password"));
        assertFalse(sqlSet.contains("username"));
        assertFalse(sqlSet.contains("email"));
        assertFalse(sqlSet.contains("created_at"));
        List<Object> values = valuesOf(update);
        assertFalse(values.contains(existing.getPassword()));
        assertFalse(values.contains(existing.getUsername()));
        assertFalse(values.contains(existing.getEmail()));
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
