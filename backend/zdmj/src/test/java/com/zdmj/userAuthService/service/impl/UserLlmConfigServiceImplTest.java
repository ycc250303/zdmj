package com.zdmj.userAuthService.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.zdmj.common.ai.UserLlmRouter;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.userAuthService.dto.UserLlmConfigDTO;
import com.zdmj.userAuthService.dto.UserLlmConfigRequest;
import com.zdmj.userAuthService.dto.UserLlmConnectionTestRequest;
import com.zdmj.userAuthService.entity.UserLlmConfig;
import com.zdmj.userAuthService.mapper.UserLlmConfigMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLlmConfigServiceImplTest {

    private static final Long USER_ID = 100L;

    @Mock
    private UserLlmConfigMapper userLlmConfigMapper;

    @Mock
    private UserLlmRouter userLlmRouter;

    private UserLlmConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserLlmConfigServiceImpl(userLlmConfigMapper, userLlmRouter);
        UserHolder.set(UserContext.of(USER_ID, "tester"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void getMyConfig_returnsPlatformDefaultWhenNotConfigured() {
        when(userLlmConfigMapper.selectById(USER_ID)).thenReturn(null);
        when(userLlmRouter.isPlatformFallbackEnabled()).thenReturn(true);

        UserLlmConfigDTO dto = service.getMyConfig();

        assertFalse(dto.isConfigured());
        assertTrue(dto.isUsingPlatformDefault());
    }

    @Test
    void getMyConfig_masksApiKeyWhenConfigured() {
        UserLlmConfig config = new UserLlmConfig();
        config.setUserId(USER_ID);
        config.setModelCode("qwen3.6-plus");
        config.setApiKeyCiphertext("cipher-qwen");
        when(userLlmConfigMapper.selectById(USER_ID)).thenReturn(config);
        when(userLlmRouter.decryptApiKey("cipher-qwen")).thenReturn("sk-qwen-key");

        UserLlmConfigDTO dto = service.getMyConfig();

        assertTrue(dto.isConfigured());
        assertEquals("qwen3.6-plus", dto.getModelCode());
        assertEquals("sk-****-key", dto.getApiKeyMasked());
    }

    @Test
    void saveMyConfig_insertsAndEvictsCache() {
        when(userLlmConfigMapper.selectById(USER_ID)).thenReturn(null);
        UserLlmConfigRequest request = new UserLlmConfigRequest();
        request.setModelCode("qwen3.6-plus");
        request.setApiKey("sk-qwen-key");
        when(userLlmRouter.encryptApiKey("sk-qwen-key")).thenReturn("cipher-qwen");

        service.saveMyConfig(request);

        ArgumentCaptor<UserLlmConfig> captor = ArgumentCaptor.forClass(UserLlmConfig.class);
        verify(userLlmConfigMapper).insert(captor.capture());
        assertEquals("qwen3.6-plus", captor.getValue().getModelCode());
        assertEquals("cipher-qwen", captor.getValue().getApiKeyCiphertext());
        verify(userLlmRouter).validateModelCode("qwen3.6-plus");
        verify(userLlmRouter).evict(USER_ID);
    }

    @Test
    void saveMyConfig_updatesModelAndEvictsWhenSwitchingToDeepSeek() {
        UserLlmConfig existing = new UserLlmConfig();
        existing.setUserId(USER_ID);
        existing.setModelCode("qwen3.6-plus");
        existing.setApiKeyCiphertext("cipher-qwen");
        when(userLlmConfigMapper.selectById(USER_ID)).thenReturn(existing);

        UserLlmConfigRequest request = new UserLlmConfigRequest();
        request.setModelCode("deepseek-v4-flash");
        request.setApiKey("sk-deepseek-key");
        when(userLlmRouter.encryptApiKey("sk-deepseek-key")).thenReturn("cipher-deepseek");

        service.saveMyConfig(request);

        ArgumentCaptor<UserLlmConfig> captor = ArgumentCaptor.forClass(UserLlmConfig.class);
        verify(userLlmConfigMapper).updateById(captor.capture());
        assertEquals("deepseek-v4-flash", captor.getValue().getModelCode());
        assertEquals("cipher-deepseek", captor.getValue().getApiKeyCiphertext());
        verify(userLlmRouter).validateModelCode("deepseek-v4-flash");
        verify(userLlmRouter).evict(USER_ID);
        verify(userLlmConfigMapper, never()).insert(any());
    }

    @Test
    void deleteMyConfig_removesRowAndEvictsCache() {
        service.deleteMyConfig();

        verify(userLlmConfigMapper).deleteById(USER_ID);
        verify(userLlmRouter).evict(USER_ID);
    }

    @Test
    void testConnection_delegatesToRouterWithoutDbRead() {
        UserLlmConnectionTestRequest request = new UserLlmConnectionTestRequest();
        request.setModelCode("deepseek-v4-flash");
        request.setApiKey("sk-deepseek-key");

        service.testConnection(request);

        verify(userLlmRouter).validateModelCode("deepseek-v4-flash");
        verify(userLlmRouter).testConnection("deepseek-v4-flash", "sk-deepseek-key");
        verify(userLlmConfigMapper, never()).selectById(USER_ID);
    }
}
