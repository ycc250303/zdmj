package com.zdmj.userAuthService.service;

import java.util.List;

import com.zdmj.userAuthService.dto.LlmModelOptionResponse;
import com.zdmj.userAuthService.dto.UserLlmConfigResponse;
import com.zdmj.userAuthService.dto.UserLlmConfigRequest;
import com.zdmj.userAuthService.dto.UserLlmConnectionTestRequest;

public interface UserLlmConfigService {

    UserLlmConfigResponse getMyConfig();

    List<LlmModelOptionResponse> listModels();

    void saveMyConfig(UserLlmConfigRequest request);

    void deleteMyConfig();

    void testConnection(UserLlmConnectionTestRequest request);
}