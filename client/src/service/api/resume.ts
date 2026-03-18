import { request } from '../request';

/** * =====================================================================
 * TypeScript 类型定义区域 (DTOs)
 * 规范：与后端 OpenAPI Schema 严格对齐，保障数据结构在前端的强类型约束
 * =====================================================================
 */
export namespace ResumeApi {
  // --- 技能模块 ---
  export interface SkillItem {
    type: string;
    content: string[];
  }
  export interface SkillCreate {
    name: string;
    content: SkillItem[];
  }
  export interface SkillUpdate extends SkillCreate {
    id: number;
  }

  // --- 工作(实习)经历模块 ---
  export interface CareerCreate {
    company: string;
    position: string;
    startDate: string;
    endDate?: string;
    visible?: boolean;
    details?: string;
  }
  export interface CareerUpdate extends CareerCreate {
    id: number;
  }

  // --- 教育经历模块 ---
  export interface EducationCreate {
    school: string;
    major: string;
    degree: number; // 1:博士, 2:硕士, 3:本科, 4:大专, 5:高中, 6:其他
    startDate: string;
    endDate?: string;
    visible?: boolean;
    gpa?: string;
  }
  export interface EducationUpdate extends EducationCreate {
    id: number;
  }

  // --- 项目经历模块 ---
  export interface ProjectCreate {
    name: string;
    startDate: string;
    endDate?: string;
    role: string;
    description: string;
    contribution: string;
    techStack?: string[];
    highlights?: string;
    url?: string;
    visible?: boolean;
  }
  export interface ProjectUpdate extends ProjectCreate {
    id: number;
  }

  // --- 简历模块 ---
  export interface ResumeCreate {
    name: string;
    skillId: number;
  }
  export interface ResumeUpdate extends ResumeCreate {
    id: number;
  }
}

/** * =====================================================================
 * API 请求封装区域
 * 规范：统一使用 request() 进行调用，返回 Promise 响应
 * =====================================================================
 */

// ==================== 1. 技能控制器 (Skills) ====================
export function fetchAddSkill(data: ResumeApi.SkillCreate) {
  return request({ url: '/skills', method: 'post', data });
}
export function fetchUpdateSkill(data: ResumeApi.SkillUpdate) {
  return request({ url: '/skills', method: 'put', data });
}
export function fetchGetSkillList() {
  return request({ url: '/skills', method: 'get' });
}
export function fetchGetSkillDetail(id: number) {
  return request({ url: `/skills/${id}`, method: 'get' });
}
export function fetchDeleteSkill(id: number) {
  return request({ url: `/skills/${id}`, method: 'delete' });
}

// ==================== 2. 工作(实习)经历控制器 (Career) ====================
// 注意：后端接口路径为单数 /career
export function fetchAddCareer(data: ResumeApi.CareerCreate) {
  return request({ url: '/career', method: 'post', data });
}
export function fetchUpdateCareer(data: ResumeApi.CareerUpdate) {
  return request({ url: '/career', method: 'put', data });
}
export function fetchGetCareerList() {
  return request({ url: '/career', method: 'get' });
}
export function fetchGetCareerDetail(id: number) {
  return request({ url: `/career/${id}`, method: 'get' });
}
export function fetchDeleteCareer(id: number) {
  return request({ url: `/career/${id}`, method: 'delete' });
}

// ==================== 3. 教育经历控制器 (Educations) ====================
export function fetchAddEducation(data: ResumeApi.EducationCreate) {
  return request({ url: '/educations', method: 'post', data });
}
export function fetchUpdateEducation(data: ResumeApi.EducationUpdate) {
  return request({ url: '/educations', method: 'put', data });
}
export function fetchGetEducationList() {
  return request({ url: '/educations', method: 'get' });
}
export function fetchGetEducationDetail(id: number) {
  return request({ url: `/educations/${id}`, method: 'get' });
}
export function fetchDeleteEducation(id: number) {
  return request({ url: `/educations/${id}`, method: 'delete' });
}

// ==================== 4. 项目经历控制器 (Projects) ====================
export function fetchAddProject(data: ResumeApi.ProjectCreate) {
  return request({ url: '/projects', method: 'post', data });
}
export function fetchUpdateProject(data: ResumeApi.ProjectUpdate) {
  return request({ url: '/projects', method: 'put', data });
}
export function fetchGetProjectList() {
  return request({ url: '/projects', method: 'get' });
}
export function fetchGetProjectDetail(id: number) {
  return request({ url: `/projects/${id}`, method: 'get' });
}
export function fetchDeleteProject(id: number) {
  return request({ url: `/projects/${id}`, method: 'delete' });
}

// ==================== 5. 简历控制器 (Resumes) ====================
export function fetchAddResume(data: ResumeApi.ResumeCreate) {
  return request({ url: '/resumes', method: 'post', data });
}
export function fetchUpdateResume(data: ResumeApi.ResumeUpdate) {
  return request({ url: '/resumes', method: 'put', data });
}
export function fetchGetResumeList() {
  return request({ url: '/resumes', method: 'get' });
}
export function fetchGetResumeDetail(id: number) {
  return request({ url: `/resumes/${id}`, method: 'get' });
}
export function fetchDeleteResume(id: number) {
  return request({ url: `/resumes/${id}`, method: 'delete' });
}

/**
 * 聚合查询：一次性拉取整份简历的完整内容（包含技能、教育、工作、项目等关联数据）
 * 对应接口：/resumes/{id}/content
 */
export function fetchGetResumeFullContentDetail(id: number) {
  return request({ url: `/resumes/${id}/content`, method: 'get' });
}

/**
 * 聚合查询：拉取所有简历的完整内容列表
 * 对应接口：/resumes/content
 */
export function fetchGetResumeFullContentList() {
  return request({ url: '/resumes/content', method: 'get' });
}