// --- 技能模块 ---
export interface SkillCreate {
  name: string;
  description?: string;
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

// 展示层
export interface SkillDTO {
  id: number;
  name: string;
  description?: string;
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
  skills: SkillDTO[];
  educations: EducationDTO[];
  careers: CareerDTO[];
  projects: ProjectDTO[];
  awards: AwardDTO[];
  personalInfo?: ResumePersonalInfo;
}

export interface ResumeContentSaveRequest {
  skills: Array<SkillCreate | SkillUpdate>;
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
  name?: string;
  description?: string;
}

export interface ResumeImportParseResult {
  personalInfo?: ResumeImportPersonalInfo;
  educations?: ResumeImportEducationItem[];
  careers?: ResumeImportCareerItem[];
  projects?: ResumeImportProjectItem[];
  awards?: ResumeImportAwardItem[];
  skills?: ResumeImportSkillItem[];
  warnings?: string[];
}

export interface ResumeImportParseRequest {
  rawText?: string;
}
