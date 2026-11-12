import { request } from '../request';
import type * as CapabilityProfileApi from './profile.types';

/** * =====================================================================
 * TypeScript 类型定义区域 (DTOs)
 * 规范：与后端 OpenAPI Schema 严格对齐，保障数据结构在前端的强类型约束
 * =====================================================================
 */

export type { CapabilityProfileApi };

/** * =====================================================================
 * API 请求封装区域
 * 规范：统一使用 request() 进行调用，返回 Promise 响应
 * =====================================================================
 */

/**
 * 获取当前用户的能力画像
 * 对应接口：GET /capability-profile/current
 */
export function fetchGetCurrentCapabilityProfile() {
  return request<CapabilityProfileApi.StudentCapabilityProfile>({
    url: '/capability-profile/current',
    method: 'get'
  });
}

/**
 * 查询当前用户的能力画像（仅查询，不触发生成）
 * 对应接口：GET /capability-profile/current/query
 * 注意：如果画像不存在，返回 null，不会抛出错误
 */
export function fetchQueryCapabilityProfile() {
  return request<CapabilityProfileApi.StudentCapabilityProfile>({
    url: '/capability-profile/current/query',
    method: 'get'
  });
}

/**
 * 生成能力画像并返回结果。
 * 对应接口：POST /capability-profile/generate
 */
export function fetchGenerateCapabilityProfile(data: CapabilityProfileApi.CapabilityProfileGenerateReq) {
  return request<CapabilityProfileApi.StudentCapabilityProfile>({
    url: '/capability-profile/generate',
    method: 'post',
    data,
    timeout: 300000
  });
}

/**
 * 文件上传结果
 */
export interface FileUploadResult {
  key: string;
  url: string;
  fileName: string;
  fileSize: number;
  contentType: string;
}

/**
 * 上传文件到腾讯云COS
 * 对应接口：POST /files/upload
 * @param file 要上传的文件
 * @param prefix 文件存储前缀，默认为'profile'
 */
export function fetchUploadFile(file: File, prefix = 'profile') {
  const formData = new FormData();
  formData.append('file', file);
  return request<FileUploadResult>({
    url: '/files/upload',
    method: 'post',
    data: formData,
    params: { prefix }
  });
}
