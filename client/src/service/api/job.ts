import { request } from '../request';

/**
 * =====================================================================
 * TypeScript 类型定义区域 (DTOs)
 * 规范：与后端 OpenAPI Schema 严格对齐，保障数据结构在前端的强类型约束
 * =====================================================================
 */
export namespace JobApi {
  // 岗位实体
  export interface Job {
    id: number;
    jobName: string;
    companyId: number;
    companyName: string;
    description: string;
    location: string;
    salaryMin: number;
    salaryMax: number;
    salaryType: number; // 1=日薪 / 2=月薪 / 3=年薪
    content: string[]; // 岗位职责
    requirements: string[]; // 岗位要求
    keywords: string[]; // 岗位关键词
    link: string;
    companySize?: number;
    companyFundingType?: number;
    companyIndustries?: string[];
    companyIntroduction?: string;
    createdAt: string;
    updatedAt: string;
  }

  // 岗位列表项
  export interface JobListItem {
    id: number;
    jobName: string;
    companyName: string;
    description: string;
    location: string;
    salaryMin: number;
    salaryMax: number;
    salaryType: number;
    salary?: string; // 薪资展示文案
    keywords: string[];
    link: string;
    companySize?: number;
    companyFundingType?: number;
    companyIndustries?: string[];
    jobDuties?: string[]; // 岗位职责
    jobRequirements?: string[]; // 岗位要求
  }

  // 岗位分页查询条件
  export interface JobPageQuery {
    page?: number;
    limit?: number;
    companySizes?: number[];
    fundingTypes?: number[];
    industries?: string[];
    companyName?: string;
    employment?: 'INTERN' | 'FULLTIME';
    filterSalaryMin?: number;
    filterSalaryMax?: number;
    jobName?: string;
  }

  // 创建岗位请求
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
    link: string;
    jobDuties?: string[];
    jobRequirements?: string[];
    keywords?: string[];
  }

  // 更新岗位请求
  export interface JobUpdate extends JobCreate {
    id: number;
  }

  // 分页响应
  export interface PageResponse<T> {
    list: T[];  // 后端返回的是 list 字段
    total: number;
    page: number;
    limit: number;
    totalPages?: number;
  }

  // 岗位能力画像
  export interface JobCapabilityProfile {
    targetRoleType?: string; // 岗位类型
    professionalSkills?: string; // 专业技能
    certificates?: string; // 证书
    innovationAbility?: string; // 创新能力
    learningAbility?: string; // 学习能力
    pressureResistance?: string; // 抗压能力
    communicationAbility?: string; // 沟通能力
    practicalAbility?: string; // 实践能力
    strengths?: string[]; // 岗位优势亮点
    missingSkills?: string[]; // 缺失技能项
    weakEvidenceItems?: string[]; // 证据不足项
    summary?: string; // 一句话总结
  }
}

/**
 * =====================================================================
 * API 请求封装区域
 * 规范：统一使用 request() 进行调用，返回 Promise 响应
 * =====================================================================
 */

/**
 * 查询岗位详情
 * @param id 岗位ID
 */
export function fetchGetJobDetail(id: number) {
  return request<JobApi.JobListItem>({ url: `/jobs/${id}`, method: 'get' });
}

/**
 * 查询岗位列表（分页）
 * @param query 查询条件
 */
export function fetchGetJobPage(query: JobApi.JobPageQuery) {
  // 后端使用 @GetMapping + @RequestBody，axios默认会忽略GET的data
  // 需要通过transformRequest或者在请求选项中明确指定
  return request<JobApi.PageResponse<JobApi.JobListItem>>({
    url: '/jobs',
    method: 'get' as any,
    data: query,
    headers: {
      'Content-Type': 'application/json'
    },
    // 强制发送body，即使方法是GET
    transformRequest: [(data) => {
      return JSON.stringify(data);
    }]
  } as any);
}

/**
 * 创建岗位
 * @param data 岗位数据
 */
export function fetchCreateJob(data: JobApi.JobCreate) {
  return request<JobApi.Job>({ url: '/jobs', method: 'post', data });
}

/**
 * 更新岗位
 * @param data 岗位数据
 */
export function fetchUpdateJob(data: JobApi.JobUpdate) {
  return request<JobApi.Job>({ url: '/jobs', method: 'put', data });
}

/**
 * 删除岗位
 * @param id 岗位ID
 */
export function fetchDeleteJob(id: number) {
  return request({ url: `/jobs/${id}`, method: 'delete' });
}

/**
 * 查询岗位能力画像
 * @param id 岗位ID
 */
export function fetchGetJobCapabilityProfile(id: number) {
  return request<JobApi.JobCapabilityProfile>({
    url: `/jobs/${id}/capability-profile`,
    method: 'get'
  });
}

/**
 * 生成岗位能力画像
 * @param id 岗位ID
 */
export function fetchGenerateJobCapabilityProfile(id: number) {
  return request<JobApi.JobCapabilityProfile>({
    url: `/jobs/${id}/capability-profile`,
    method: 'post',
    timeout: 300000 // 5分钟超时，AI生成需要较长时间
  });
}
