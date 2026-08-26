package com.zdmj.userAuthService.dto;

import lombok.Data;

@Data
public class UserLlmConfigResponse {

    private boolean configured;

    private boolean usingPlatformDefault;

    private String modelCode;

    private String modelDisplayName;

    private String apiKeyMasked;
}