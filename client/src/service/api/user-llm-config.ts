import { request } from '../request';

export interface UserLlmConfig {
  configured: boolean;
  modelCode?: string;
  modelDisplayName?: string;
  apiKeyMasked?: string;
  models: UserLlmModelOption[];
}

export interface UserLlmModelOption {
  code: string;
  displayName: string;
}

export interface SaveUserLlmConfigRequest {
  modelCode: string;
  apiKey: string;
}

/** 获取当前用户模型配置与可选模型目录 */
export function fetchGetUserLlmConfig() {
  return request<UserLlmConfig>({
    url: '/users/llm-config',
    method: 'get'
  });
}

/** 校验模型连通性后保存当前用户配置 */
export function fetchSaveUserLlmConfig(data: SaveUserLlmConfigRequest) {
  return request<null>({
    url: '/users/llm-config',
    method: 'put',
    data
  });
}

/** 删除用户大模型配置。删除后对话调用返回未配置。 */
export function fetchDeleteUserLlmConfig() {
  return request<null>({
    url: '/users/llm-config',
    method: 'delete'
  });
}
