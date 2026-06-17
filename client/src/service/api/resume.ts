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

  // --- 获奖信息模块 ---
  export interface AwardCreate {
    /** 1=奖学金, 2=竞赛获奖, 3=其他类型 */
    awardType: number;
    name: string;
    awardDate: string;
    description?: string;
  }
  export interface AwardUpdate extends AwardCreate {
    id: number;
  }

  // --- 简历模块 ---
  export interface ResumeCreate {
    skillId: number;
  }
  export interface ResumeUpdate extends ResumeCreate {
    id: number;
  }

  //展示层
  export interface SkillDTO {
    id: number;
    content: SkillItem[];
  }

  export interface EducationDTO {
    id: number;
    school: string;
    major: string;
    degree: number;
    startDate: string;
    endDate?: string;
    visible: boolean;
    gpa?: string;
  }

  export interface CareerDTO {
    id: number;
    company: string;
    position: string;
    startDate: string;
    endDate?: string;
    visible: boolean;
    details?: string;
  }

  export interface ProjectDTO {
    id: number;
    name: string;
    startDate: string;
    endDate?: string;
    role: string;
    description: string;
    contribution: string;
    techStack?: string[];
    highlights?: string;
    url?: string;
    visible: boolean;
  }

  export interface AwardDTO {
    id: number;
    awardType: number;
    name: string;
    awardDate: string;
    description?: string;
  }

  export interface ResumePersonalInfo {
    name?: string;
    phone?: string;
    homepageUrl?: string;
    preferredWorkCity?: string;
  }

  export interface ResumeContentDTO {
    id: number;
    skill: SkillDTO;
    educations: EducationDTO[];
    careers: CareerDTO[];
    projects: ProjectDTO[];
    awards: AwardDTO[];
    personalInfo?: ResumePersonalInfo;
  }

  export interface ResumeContentSaveRequest {
    skill: SkillCreate | SkillUpdate;
    educations: Array<EducationCreate | EducationUpdate>;
    careers: Array<CareerCreate | CareerUpdate>;
    projects: Array<ProjectCreate | ProjectUpdate>;
    awards: Array<AwardCreate | AwardUpdate>;
    personalInfo?: ResumePersonalInfo;
  }

  /** 简历 PDF/文本识别结果 */
  export interface ResumeImportPersonalInfo {
    name?: string;
    phone?: string;
    email?: string;
    major?: string;
    homepageUrl?: string;
    preferredWorkCity?: string;
  }

  export interface ResumeImportEducationItem {
    school?: string;
    major?: string;
    degree?: number;
    startDate?: string;
    endDate?: string;
    visible?: boolean;
    gpa?: string;
  }

  export interface ResumeImportCareerItem {
    company?: string;
    position?: string;
    startDate?: string;
    endDate?: string;
    visible?: boolean;
    details?: string;
  }

  export interface ResumeImportProjectItem {
    name?: string;
    role?: string;
    startDate?: string;
    endDate?: string;
    description?: string;
    contribution?: string;
    techStack?: string[];
    highlights?: string;
    url?: string;
    visible?: boolean;
  }

  export interface ResumeImportAwardItem {
    awardType?: number;
    name?: string;
    awardDate?: string;
    description?: string;
  }

  export interface ResumeImportSkillItem {
    content?: SkillItem[];
  }

  export interface ResumeImportParseResult {
    personalInfo?: ResumeImportPersonalInfo;
    educations?: ResumeImportEducationItem[];
    careers?: ResumeImportCareerItem[];
    projects?: ResumeImportProjectItem[];
    awards?: ResumeImportAwardItem[];
    skill?: ResumeImportSkillItem;
    warnings?: string[];
  }

  export interface ResumeImportParseRequest {
    pdfUrl?: string;
    rawText?: string;
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

// ==================== 5. 获奖信息控制器 (Awards) ====================
export function fetchAddAward(data: ResumeApi.AwardCreate) {
  return request({ url: '/awards', method: 'post', data });
}
export function fetchUpdateAward(data: ResumeApi.AwardUpdate) {
  return request({ url: '/awards', method: 'put', data });
}
export function fetchGetAwardList() {
  return request({ url: '/awards', method: 'get' });
}
export function fetchGetAwardDetail(id: number) {
  return request({ url: `/awards/${id}`, method: 'get' });
}
export function fetchDeleteAward(id: number) {
  return request({ url: `/awards/${id}`, method: 'delete' });
}

// ==================== 6. 简历控制器 (Resumes) ====================
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

/** 获取当前用户简历完整内容（不存在则后端自动创建） */
export function fetchGetMyResumeContent() {
  // _silent: 关闭全局错误 toast；新用户尚无简历是正常情况，由页面自行降级到导入流程
  return request<ResumeApi.ResumeContentDTO>({ url: '/resumes/me/content', method: 'get', _silent: true } as any);
}

/** 全量保存当前用户简历内容 */
export function fetchSaveMyResumeContent(data: ResumeApi.ResumeContentSaveRequest) {
  return request<ResumeApi.ResumeContentDTO>({ url: '/resumes/me/content', method: 'put', data });
}

/** 简历 PDF/文本结构化识别（不写库） */
export function fetchParseResumeImport(data: ResumeApi.ResumeImportParseRequest) {
  return request<ResumeApi.ResumeImportParseResult>({
    url: '/resumes/import/parse',
    method: 'post',
    data
  });
}

// --- 基本信息模块 DTO ---
export interface UserUpdateDTO {
  name?: string;
  phone?: string;
  homepageUrl?: string;
}

// 请求方法：更新当前登录用户的基本信息
export function fetchUpdateUserInfo(data: UserUpdateDTO) {
  return request({ url: '/users/me', method: 'put', data });
}