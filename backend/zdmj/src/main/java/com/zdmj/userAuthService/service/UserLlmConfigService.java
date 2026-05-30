package com.zdmj.userAuthService.service;

import java.util.List;

import com.zdmj.userAuthService.dto.LlmModelOptionDTO;
import com.zdmj.userAuthService.dto.UserLlmConfigDTO;
import com.zdmj.userAuthService.dto.UserLlmConfigRequest;
import com.zdmj.userAuthService.dto.UserLlmConnectionTestRequest;

public interface UserLlmConfigService {

    UserLlmConfigDTO getMyConfig();

    List<LlmModelOptionDTO> listModels();

    void saveMyConfig(UserLlmConfigRequest request);

    void deleteMyConfig();

    void testConnection(UserLlmConnectionTestRequest request);
}