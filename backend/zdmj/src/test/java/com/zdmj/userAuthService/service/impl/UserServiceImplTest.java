package com.zdmj.userAuthService.service.impl;

import com.zdmj.common.context.CurrentActor;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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

    private CurrentActor actor = new CurrentActor(1L);

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

    @Test
    void register_whenUsernameExists_shouldThrow2001() {
        UserRegisterRequest dto = buildRegisterDTO();
        doReturn(true).when(userService).existsByUsername(dto.username());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(dto));

        assertEquals(ErrorCode.USER_ALREADY_EXISTS.getCode(), ex.getCode());
        verify(userService).existsByUsername(dto.username());
        verify(userService, never()).existsByEmail(anyString());
        verifyNoInteractions(verificationCodeService);
    }

    @Test
    void register_whenEmailExists_shouldThrow2002() {
        UserRegisterRequest dto = buildRegisterDTO();
        doReturn(false).when(userService).existsByUsername(dto.username());
        doReturn(true).when(userService).existsByEmail(dto.email());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(dto));

        assertEquals(ErrorCode.USER_EMAIL_EXISTS.getCode(), ex.getCode());
        verify(userService).existsByUsername(dto.username());
        verify(userService).existsByEmail(dto.email());
        verifyNoInteractions(verificationCodeService);
    }

    @Test
    void register_whenCaptchaInvalid_shouldThrow2003() {
        UserRegisterRequest dto = buildRegisterDTO();
        doReturn(false).when(userService).existsByUsername(dto.username());
        doReturn(false).when(userService).existsByEmail(dto.email());
        doThrow(new BusinessException(ErrorCode.CAPTCHA_ERROR)).when(verificationCodeService)
                .verifyAndConsume(eq(dto.email()), eq(dto.verificationCode()), eq(VerificationCodeScene.REGISTER));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(dto));

        assertEquals(ErrorCode.CAPTCHA_ERROR.getCode(), ex.getCode());
        verify(verificationCodeService).verifyAndConsume(eq(dto.email()), eq(dto.verificationCode()),
                eq(VerificationCodeScene.REGISTER));
        verify(userService, never()).save(any(User.class));
    }

    @Test
    void register_whenSaveFailed_shouldThrow2004() {
        UserRegisterRequest dto = buildRegisterDTO();
        doReturn(false).when(userService).existsByUsername(dto.username());
        doReturn(false).when(userService).existsByEmail(dto.email());
        doReturn(false).when(userService).save(any(User.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(dto));

        assertEquals(ErrorCode.USER_REGISTER_FAILED.getCode(), ex.getCode());
        var order = org.mockito.Mockito.inOrder(verificationCodeService, userService);
        order.verify(verificationCodeService).verifyAndConsume(dto.email(), dto.verificationCode(),
                VerificationCodeScene.REGISTER);
        order.verify(userService).save(any(User.class));
    }

    @Test
    void register_whenAllValid_shouldReturnUserResponseAndEncodePassword() {
        UserRegisterRequest dto = buildRegisterDTO();
        doReturn(false).when(userService).existsByUsername(dto.username());
        doReturn(false).when(userService).existsByEmail(dto.email());
        doReturn(true).when(userService).save(any(User.class));

        UserResponse result = userService.register(dto);

        assertEquals(dto.username(), result.username());
        assertEquals(dto.email(), result.email());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals(dto.username(), savedUser.getUsername());
        assertEquals(dto.email(), savedUser.getEmail());
        assertNotEquals(dto.password(), savedUser.getPassword());
        assertTrue(PASSWORD_ENCODER.matches(dto.password(), savedUser.getPassword()));
    }

    @Test
    void login_whenUserNotFound_shouldThrow2006() {
        UserLoginRequest dto = new UserLoginRequest("ghost", "password123");

        doReturn(null).when(userService).getOne(any(LambdaQueryWrapper.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(dto));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(userService).getOne(any(LambdaQueryWrapper.class));
        verify(jwtSessionStore, never()).replace(anyLong(), anyString());
    }

    @Test
    void login_whenPasswordWrong_shouldThrow2005() {
        UserLoginRequest dto = new UserLoginRequest("alice", "wrong-password");

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
        UserLoginRequest dto = new UserLoginRequest("alice", "right-password");

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword(PASSWORD_ENCODER.encode("right-password"));
        doReturn(user).when(userService).getOne(any(LambdaQueryWrapper.class));

        UserLoginResponse response = userService.login(dto);

        assertEquals("alice", response.user().username());
        assertEquals(3, response.token().split("\\.").length);
        verify(jwtSessionStore).replace(eq(1L), eq(response.token()));
    }

    @Test
    void login_whenEmail_shouldReturnQueriedUser() {
        UserLoginRequest dto = new UserLoginRequest("alice@test.com", "right-password");

        User user = new User();
        user.setId(2L);
        user.setUsername("alice");
        user.setEmail("alice@test.com");
        user.setPassword(PASSWORD_ENCODER.encode("right-password"));
        doReturn(user).when(userService).getOne(any(LambdaQueryWrapper.class));

        UserLoginResponse response = userService.login(dto);

        assertEquals("alice@test.com", response.user().email());
        verify(userService).getOne(any(LambdaQueryWrapper.class));
        verify(jwtSessionStore).replace(eq(2L), eq(response.token()));
    }

    @Test
    void login_whenSessionStoreFails_shouldThrow1012AndNotReturnToken() {
        UserLoginRequest dto = new UserLoginRequest("alice", "right-password");

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

        UserResponse dto = userService.getUserById(actor, 123L);

        assertEquals(123L, dto.id());
        assertEquals("u123", dto.username());
        assertEquals("u123@test.com", dto.email());
        verify(userService).getById(123L);
    }

    @Test
    void getUserById_whenUserMissing_shouldThrow2006() {
        loginAs(404L);
        doReturn(null).when(userService).getById(404L);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.getUserById(actor, 404L));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(userService).getById(404L);
    }

    @Test
    void getUserById_whenOtherUser_shouldThrow1003AndNotQuery() {
        loginAs(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.getUserById(actor, 123L));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        assertEquals(ErrorCode.NO_PERMISSION.getMessage(), ex.getMessage());
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
                .verifyAndConsume(eq(dto.email()), eq(dto.verificationCode()), eq(VerificationCodeScene.RESET_PASSWORD));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.resetPassword(dto));

        assertEquals(ErrorCode.CAPTCHA_ERROR.getCode(), ex.getCode());
        verify(verificationCodeService).verifyAndConsume(eq(dto.email()), eq(dto.verificationCode()),
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
        order.verify(verificationCodeService).verifyAndConsume(dto.email(), dto.verificationCode(),
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
        verify(verificationCodeService).verifyAndConsume(eq(dto.email()), eq(dto.verificationCode()),
                eq(VerificationCodeScene.RESET_PASSWORD));
        verify(userService).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateProfile_whenNameBlank_shouldStoreEmptyString() {
        UserUpdateRequest dto = new UserUpdateRequest("   ", "  ", " ", null);

        User existing = profileUser(14L, "old-name", "13800000000", "https://old.site", "上海");
        loginAs(14L);
        doReturn(existing).when(userService).getById(14L);
        doReturn(true).when(userService).update(any(LambdaUpdateWrapper.class));

        UserResponse result = userService.updateProfile(actor, 14L, dto);

        assertEquals("", result.name());
        assertEquals("", result.phone());
        assertEquals("", result.website());
        assertEquals("上海", result.preferredWorkCity());
        LambdaUpdateWrapper<User> update = captureProfileUpdate();
        assertEquals(List.of("name", "phone", "website", "updated_at"), columnsOf(update));
        assertProfileValues(update, 14L, "", "", "");
        assertProfileIdentityUntouched(update, existing);
    }

    @Test
    void updateProfile_whenUserNotFound_shouldThrow2006() {
        UserUpdateRequest dto = new UserUpdateRequest("new-name", null, null, null);

        loginAs(12L);
        doReturn(null).when(userService).getById(12L);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateProfile(actor, 12L, dto));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(userService).getById(12L);
        verify(userService, never()).update(any(LambdaUpdateWrapper.class));
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    void updateProfile_whenUpdateFailed_shouldThrow2017() {
        UserUpdateRequest dto = new UserUpdateRequest("neo", null, null, null);

        User existing = profileUser(13L, "old", null, null, null);
        loginAs(13L);
        doReturn(existing).when(userService).getById(13L);
        doReturn(false).when(userService).update(any(LambdaUpdateWrapper.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateProfile(actor, 13L, dto));

        assertEquals(ErrorCode.USER_UPDATE_FAILED.getCode(), ex.getCode());
        assertEquals(List.of("name", "updated_at"), columnsOf(captureProfileUpdate()));
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    void updateProfile_whenSuccess_shouldOnlyUpdateNonNullFields() {
        UserUpdateRequest dto = new UserUpdateRequest("new-name", null, null, null);

        User existing = profileUser(15L, "old-name", "13800000000", "https://old.site", "上海");
        loginAs(15L);
        doReturn(existing).when(userService).getById(15L);
        doReturn(true).when(userService).update(any(LambdaUpdateWrapper.class));

        UserResponse result = userService.updateProfile(actor, 15L, dto);

        assertEquals("new-name", result.name());
        assertEquals("13800000000", result.phone());
        assertEquals("https://old.site", result.website());
        assertEquals("上海", result.preferredWorkCity());
        LambdaUpdateWrapper<User> update = captureProfileUpdate();
        assertEquals(List.of("name", "updated_at"), columnsOf(update));
        assertProfileValues(update, 15L, "new-name");
        assertProfileIdentityUntouched(update, existing);
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    void updateProfile_whenFieldsPresent_shouldTrimAndKeepNullFields() {
        UserUpdateRequest dto = new UserUpdateRequest("  张三  ", null, "  https://example.com  ", "   ");

        User existing = profileUser(16L, "old-name", "13800000000", "https://old.site", "上海");
        loginAs(16L);
        doReturn(existing).when(userService).getById(16L);
        doReturn(true).when(userService).update(any(LambdaUpdateWrapper.class));

        UserResponse result = userService.updateProfile(actor, 16L, dto);

        assertEquals("张三", result.name());
        assertEquals("13800000000", result.phone());
        assertEquals("https://example.com", result.website());
        assertEquals("", result.preferredWorkCity());
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

        UserResponse result = userService.updateProfile(actor, 17L, new UserUpdateRequest(null, null, null, null));

        assertEquals("old-name", result.name());
        assertEquals("13800000000", result.phone());
        assertEquals("https://old.site", result.website());
        assertEquals("上海", result.preferredWorkCity());
        assertEquals("alice", result.username());
        assertEquals("alice@test.com", result.email());
        verify(userService, never()).update(any(LambdaUpdateWrapper.class));
        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    void updateProfile_whenOtherUser_shouldThrow1003AndNotQuery() {
        loginAs(1L);
        UserUpdateRequest dto = new UserUpdateRequest("neo", null, null, null);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateProfile(actor, 14L, dto));

        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        assertEquals(ErrorCode.NO_PERMISSION.getMessage(), ex.getMessage());
        verify(userService, never()).getById(anyLong());
        verify(userService, never()).update(any(LambdaUpdateWrapper.class));
    }

    private UserRegisterRequest buildRegisterDTO() {
        UserRegisterRequest dto = new UserRegisterRequest("alice", "Password123", "alice@test.com", "123456");

        return dto;
    }

    private UserResetPasswordRequest buildResetDTO() {
        UserResetPasswordRequest dto = new UserResetPasswordRequest("alice@test.com", "123456", "newPassword123");

        return dto;
    }

    private void loginAs(Long userId) {
        actor = new CurrentActor(userId);
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
