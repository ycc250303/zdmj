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
