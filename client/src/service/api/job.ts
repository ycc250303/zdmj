import { request } from '../request';
import type * as JobApi from './job.types';

export type { JobApi };

/**
 * =====================================================================
 * API 请求封装区域
 * 规范：统一使用 request() 进行调用，返回 Promise 响应
 * =====================================================================
 */

/**
 * 查询岗位详情
 * 后端：GET /jobs/{id}
 */
export function fetchGetJobDetail(id: number | string) {
  return request<JobApi.JobListItem>({ url: `/jobs/${id}`, method: 'get' });
}

/**
 * 查询岗位列表（分页）
 * 后端：GET /jobs?page=&limit=&companySizes=[..]&fundingTypes=[..]&industries=[..]&...
 *
 * 关键点：
 *  1. 使用 params 而非 data 传递查询参数（GET 请求参数必须放 query string）
 *  2. 数组参数后端 JobPageQueryDTO.parseRawList 要求是 JSON 字符串形式（如 "[1,2,3]"），
 *     因此对数组字段在此处统一 JSON.stringify 一次。
 */
export function fetchGetJobPage(query: JobApi.JobPageQuery) {
  const params: Record<string, any> = {};

  // 普通字段直接透传
  if (query.page !== undefined) params.page = query.page;
  if (query.limit !== undefined) params.limit = query.limit;
  if (query.companyName) params.companyName = query.companyName;
  if (query.employment) params.employment = query.employment;
  if (query.salaryType !== undefined) params.salaryType = query.salaryType;
  if (query.filterSalaryMin !== undefined) params.filterSalaryMin = query.filterSalaryMin;
  if (query.filterSalaryMax !== undefined) params.filterSalaryMax = query.filterSalaryMax;
  if (query.jobName) params.jobName = query.jobName;

  // 数组字段：转换为 JSON 字符串，匹配后端 parseRawList 解析逻辑
  if (query.companySizes && query.companySizes.length > 0) {
    params.companySizes = JSON.stringify(query.companySizes);
  }
  if (query.fundingTypes && query.fundingTypes.length > 0) {
    params.fundingTypes = JSON.stringify(query.fundingTypes);
  }
  if (query.industries && query.industries.length > 0) {
    params.industries = JSON.stringify(query.industries);
  }

  return request<JobApi.PageResponse<JobApi.JobListItem>>({
    url: '/jobs',
    method: 'get',
    params
  });
}

/**
 * 创建岗位
 * 后端：POST /jobs
 */
export function fetchCreateJob(data: JobApi.JobCreate) {
  return request<JobApi.JobListItem>({ url: '/jobs', method: 'post', data });
}

/**
 * 更新岗位
 * 后端：PUT /jobs （请求体含 id）
 */
export function fetchUpdateJob(data: JobApi.JobUpdate) {
  return request<JobApi.JobListItem>({ url: '/jobs', method: 'put', data });
}

/**
 * 删除岗位
 * 后端：DELETE /jobs/{id}
 */
export function fetchDeleteJob(id: number | string) {
  return request({ url: `/jobs/${id}`, method: 'delete' });
}
