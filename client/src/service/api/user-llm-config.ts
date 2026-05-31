import { request } from '../request';

export namespace UserLlmConfigApi {
  export interface Config {
    configured: boolean;
    usingPlatformDefault: boolean;
    modelCode?: string;
    modelDisplayName?: string;
    apiKeyMasked?: string;
  }

  export interface ModelOption {
    code: string;
    displayName: string;
  }

  export interface SaveRequest {
    modelCode: string;
    apiKey: string;
  }

  export interface TestRequest {
    modelCode: string;
    apiKey: string;
  }
}

/** 获取当前用户大模型配置 */
export function fetchGetUserLlmConfig() {
  return request<UserLlmConfigApi.Config>({
    url: '/users/llm-config',
    method: 'get'
  });
}

/** 获取可选模型列表 */
export function fetchListLlmModels() {
  return request<UserLlmConfigApi.ModelOption[]>({
    url: '/users/llm-config/models',
    method: 'get'
  });
}

/** 保存用户大模型配置 */
export function fetchSaveUserLlmConfig(data: UserLlmConfigApi.SaveRequest) {
  return request<null>({
    url: '/users/llm-config',
    method: 'put',
    data
  });
}

/** 删除用户大模型配置（恢复平台默认） */
export function fetchDeleteUserLlmConfig() {
  return request<null>({
    url: '/users/llm-config',
    method: 'delete'
  });
}

/** 测试模型与 API Key 连通性（不读库） */
export function fetchTestUserLlmConnection(data: UserLlmConfigApi.TestRequest) {
  return request<null>({
    url: '/users/llm-config/test',
    method: 'post',
    data
  });
}
