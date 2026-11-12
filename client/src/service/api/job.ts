import { request } from '../request';

/**
 * =====================================================================
 * TypeScript 类型定义区域 (DTOs)
 * 规范：与后端 OpenAPI Schema 严格对齐，保障数据结构在前端的强类型约束
 * 对齐后端：
 *  - JobController (基路径 /jobs)
 *  - JobStudentMatchController (基路径 /matches)
 * =====================================================================
 */
export namespace JobApi {
  // 岗位列表项（后端 JobListItemDTO，详情、列表统一使用此结构）
  export interface JobListItem {
    id: number;
    jobName: string;
    companyId?: number;
    companyName: string;
    description: string;
    location: string;
    salaryMin: number;
    salaryMax: number;
    salaryType: number; // 1=日薪 / 2=月薪 / 3=年薪
    salary?: string; // 薪资展示文案
    keywords?: string[];
    link?: string;
    companySize?: number;
    companyFundingType?: number;
    companyIndustries?: string[];
  }

  // 兼容旧引用：Job 与 JobListItem 同构
  export type Job = JobListItem;

  // 岗位分页查询条件（对应后端 JobPageQueryDTO）
  export interface JobPageQuery extends Api.Common.PageQueryParams {
    companySizes?: number[];
    fundingTypes?: number[];
    industries?: string[];
    companyName?: string;
    /** 实习=INTERN，全职=FULL_TIME（月薪+年薪） */
    employment?: 'INTERN' | 'FULL_TIME';
    /** 薪资类型 1=日薪/2=月薪/3=年薪；与 employment 二选一，用于薪资区间筛选 */
    salaryType?: number;
    filterSalaryMin?: number;
    filterSalaryMax?: number;
    jobName?: string;
  }

  // 创建岗位请求（对应后端 JobDTO，创建组）
  export interface JobCreate {
    jobName: string;
    companyName: string;
    companySize?: number;
    companyFundingType?: number;
    companyIndustries?: string[];
    companyIntroduction?: string;
    description: string;
    location: string;
    salaryMin: number;
    salaryMax: number;
    salaryType: number;
    link?: string;
    keywords?: string[];
  }

  // 更新岗位请求（对应后端 JobDTO，更新组，需 id）
  export interface JobUpdate extends JobCreate {
    id: number;
  }

  // 分页响应（对应后端 PageDTO）
  export type PageResponse<T> = Api.Common.PageDTO<T>;
}

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
