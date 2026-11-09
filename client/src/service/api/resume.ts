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
    highlights?: string[];
    url?: string;
    visible?: boolean;
  }
  export interface ProjectUpdate extends ProjectCreate {
    id: number;
  }

  // --- 获奖信息模块 ---
  export interface AwardCreate {
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
    highlights?: string[];
    url?: string;
    visible: boolean;
  }

  export interface AwardDTO {
    id: number;
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
    highlights?: string[];
    url?: string;
    visible?: boolean;
  }

  export interface ResumeImportAwardItem {
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

export function fetchGetProjectList() {
  return request<ResumeApi.ProjectDTO[]>({ url: '/projects', method: 'get' });
}

/** 当前用户简历列表。单用户只有一份简历，结果为 0 或 1 条。 */
export function fetchGetResumeFullContentList() {
  return request<ResumeApi.ResumeContentDTO[]>({ url: '/resumes/content', method: 'get' });
}

/** 获取当前用户简历完整内容（不存在则后端自动创建） */
export function fetchGetMyResumeContent() {
  return request<ResumeApi.ResumeContentDTO>({ url: '/resumes/me/content', method: 'get' });
}

/** 全量保存当前用户简历内容 */
export function fetchSaveMyResumeContent(data: ResumeApi.ResumeContentSaveRequest) {
  return request<ResumeApi.ResumeContentDTO>({ url: '/resumes/me/content', method: 'put', data });
}

/** 识别简历 PDF 或文本，直接返回结构化结果（不写库） */
export function fetchParseResumeImport(data: ResumeApi.ResumeImportParseRequest) {
  return request<ResumeApi.ResumeImportParseResult>({
    url: '/resumes/import/parse',
    method: 'post',
    data,
    timeout: 300000
  });
}