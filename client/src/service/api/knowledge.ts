import { request } from '../request';
import type * as KnowledgeApi from './knowledge.types';

/**
 * =====================================================================
 * TypeScript 类型定义区域 (DTOs)
 * 规范：与后端 API 严格对齐，保障数据结构在前端的强类型约束
 * =====================================================================
 */

export type { KnowledgeApi };

/**
 * =====================================================================
 * API 请求封装区域
 * 规范：统一使用 request() 进行调用，返回 Promise 响应
 * API路径：/api/zdmj 前缀由后端统一处理
 * =====================================================================
 */

// ========== 知识库管理 ==========

/** 创建知识库 */
export function fetchCreateKnowledgeBases() {
  return request<KnowledgeApi.KnowledgeBasesDTO>({ url: '/knowledge', method: 'post' });
}

/** 查询当前用户的知识库 */
export function fetchGetKnowledgeBases() {
  return request<KnowledgeApi.KnowledgeBasesDTO>({ url: '/knowledge', method: 'get' });
}

/** 清空知识库 */
export function fetchClearKnowledgeBases() {
  return request<undefined>({ url: '/knowledge', method: 'delete' });
}

// ========== 知识文档管理 ==========

/** 分页查询知识文档列表 */
export function fetchGetKnowledgeDocumentList(params?: KnowledgeApi.KnowledgeQueryParams) {
  return request<KnowledgeApi.PageResult<KnowledgeApi.KnowledgeDocumentDTO>>({
    url: '/knowledge-document',
    method: 'get',
    params
  });
}

/** 根据ID查询知识文档详情 */
export function fetchGetKnowledgeDocumentDetail(id: number) {
  return request<KnowledgeApi.KnowledgeDocumentDTO>({ url: `/knowledge-document/${id}`, method: 'get' });
}

/** 创建知识文档 */
export function fetchCreateKnowledgeDocument(data: KnowledgeApi.KnowledgeDocumentCreate) {
  return request<KnowledgeApi.KnowledgeDocumentDTO>({
    url: '/knowledge-document',
    method: 'post',
    data,
    timeout: 300000
  });
}

/** 更新知识文档 */
export function fetchUpdateKnowledgeDocument(data: KnowledgeApi.KnowledgeDocumentUpdate) {
  return request<KnowledgeApi.KnowledgeDocumentDTO>({
    url: '/knowledge-document',
    method: 'put',
    data,
    timeout: 300000
  });
}

/** 删除知识文档 */
export function fetchDeleteKnowledgeDocument(id: number) {
  return request<undefined>({ url: `/knowledge-document/${id}`, method: 'delete' });
}

// ========== 文件上传 ==========

/** 上传文件到腾讯云COS */
export function fetchUploadFile(file: File, prefix = 'knowledge') {
  const formData = new FormData();
  formData.append('file', file);
  return request<KnowledgeApi.FileUploadResult>({
    url: '/files/upload',
    method: 'post',
    data: formData,
    params: { prefix }
  });
}

// ========== 兼容性函数 (保持向后兼容) ==========

/** @deprecated 使用 fetchCreateKnowledgeDocument 代替 */
export function fetchCreateKnowledge(data: KnowledgeApi.KnowledgeDocumentCreate) {
  return fetchCreateKnowledgeDocument(data);
}

/** @deprecated 使用 fetchGetKnowledgeDocumentList 代替 */
export function fetchGetKnowledgeList(params?: KnowledgeApi.KnowledgeQueryParams) {
  return fetchGetKnowledgeDocumentList(params);
}

/** @deprecated 使用 fetchGetKnowledgeDocumentDetail 代替 */
export function fetchGetKnowledgeDetail(id: number) {
  return fetchGetKnowledgeDocumentDetail(id);
}

/** @deprecated 使用 fetchUpdateKnowledgeDocument 代替 */
export function fetchUpdateKnowledge(data: KnowledgeApi.KnowledgeDocumentUpdate) {
  return fetchUpdateKnowledgeDocument(data);
}

/** @deprecated 使用 fetchDeleteKnowledgeDocument 代替 */
export function fetchDeleteKnowledge(id: number) {
  return fetchDeleteKnowledgeDocument(id);
}
