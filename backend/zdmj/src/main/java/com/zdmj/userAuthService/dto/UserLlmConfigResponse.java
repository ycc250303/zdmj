package com.zdmj.userAuthService.dto;

import lombok.Data;

@Data
public class UserLlmConfigResponse {

    private boolean configured;

    private String modelCode;

    private String modelDisplayName;

    private String apiKeyMasked;
}