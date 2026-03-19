import { request } from '../request';

/**
 * =====================================================================
 * TypeScript 类型定义区域 (DTOs)
 * 规范：与后端 OpenAPI Schema 严格对齐，保障数据结构在前端的强类型约束
 * =====================================================================
 */
export namespace KnowledgeApi {
  /** 知识类型枚举 */
  export type KnowledgeType = 1 | 2 | 3;
  // 1=项目文档, 2=GitHub仓库代码, 3=项目DeepWiki文档

  /** 任务状态枚举 */
  export type TaskStatus = 1 | 2 | 3 | 4 | 5;
  // 1=待执行, 2=执行中, 3=成功, 4=失败, 5=取消

  /** 创建知识库 DTO */
  export interface KnowledgeCreate {
    name: string;
    projectId: number;
    type: KnowledgeType;
    content: string;
    tag?: string[];
  }

  /** 更新知识库 DTO */
  export interface KnowledgeUpdate extends KnowledgeCreate {
    id: number;
  }

  /** 知识库响应 DTO */
  export interface KnowledgeDTO {
    id: number;
    userId: number;
    name: string;
    projectId: number;
    type: KnowledgeType;
    content: string;
    tag: string[];
    vectorIds: string[];
    vectorTaskId: string;
    vectorTaskStatus: TaskStatus;
    createdAt: string;
    updatedAt: string;
  }

  /** 分页查询参数 */
  export interface KnowledgeQueryParams {
    page: number;
    limit: number;
    projectId?: number;
    type?: KnowledgeType;
  }

  /** 分页结果 */
  export interface PageResult<T> {
    records: T[];
    total: number;
    current: number;
    size: number;
  }

  /** 向量化任务状态响应 */
  export interface TaskStatusResponse {
    taskId: string;
    knowledgeId?: number;
    status: string; // 后端返回字符串：PENDING, RUNNING, SUCCESS, FAILED, CANCELLED
    vectorIds?: number[];
    errorMessage?: string;
    startTime?: string;
    endTime?: string;
  }

  /** 文件上传结果 */
  export interface FileUploadResult {
    key: string;
    url: string;
    fileName: string;
    fileSize: number;
    contentType: string;
  }
}

/**
 * =====================================================================
 * API 请求封装区域
 * 规范：统一使用 request() 进行调用，返回 Promise 响应
 * =====================================================================
 */

/** 创建知识库 */
export function fetchCreateKnowledge(data: KnowledgeApi.KnowledgeCreate) {
  return request<KnowledgeApi.KnowledgeDTO>({ url: '/knowledge', method: 'post', data });
}

/** 分页查询知识库列表 */
export function fetchGetKnowledgeList(params: KnowledgeApi.KnowledgeQueryParams) {
  return request<KnowledgeApi.PageResult<KnowledgeApi.KnowledgeDTO>>({
    url: '/knowledge',
    method: 'get',
    params
  });
}

/** 根据ID查询知识库详情 */
export function fetchGetKnowledgeDetail(id: number) {
  return request<KnowledgeApi.KnowledgeDTO>({ url: `/knowledge/${id}`, method: 'get' });
}

/** 更新知识库 */
export function fetchUpdateKnowledge(data: KnowledgeApi.KnowledgeUpdate) {
  return request<KnowledgeApi.KnowledgeDTO>({ url: '/knowledge', method: 'put', data });
}

/** 删除知识库 */
export function fetchDeleteKnowledge(id: number) {
  return request<string>({ url: `/knowledge/${id}`, method: 'delete' });
}

/** 查询向量化任务状态 */
export function fetchVectorTaskStatus(knowledgeId: number) {
  return request<KnowledgeApi.TaskStatusResponse>({
    url: `/knowledge/${knowledgeId}/vector/task/status`,
    method: 'get'
  });
}

/** 文件上传 */
export function fetchUploadFile(file: File, prefix = 'knowledge') {
  const formData = new FormData();
  formData.append('file', file);
  return request<KnowledgeApi.FileUploadResult>({
    url: '/files/upload',
    method: 'post',
    data: formData,
    params: { prefix },
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
}

/** 启动向量化任务（通过更新知识库触发） */
export function fetchRetryVectorTask(data: KnowledgeApi.KnowledgeUpdate) {
  return request<KnowledgeApi.KnowledgeDTO>({
    url: '/knowledge',
    method: 'put',
    data
  });
}
