import { request } from '../request';

/** * =====================================================================
 * TypeScript 类型定义区域 (DTOs)
 * 规范：与后端 OpenAPI Schema 严格对齐，保障数据结构在前端的强类型约束
 * =====================================================================
 */
export namespace CapabilityProfileApi {
  /** 用户画像生成请求参数 */
  export interface CapabilityProfileGenerateReq {
    /** COS 上传后返回的 PDF 文件 URL，和 rawText 二选一即可 */
    pdfUrl?: string;
    /** 前端自行拼接的文本内容，和 pdfUrl 二选一即可 */
    rawText?: string;
  }

  /** 岗位专项评估分项 */
  export interface ScoreDetail {
    /** 岗位匹配技术深度评分 */
    jobMatchTechDepthScore?: number;
    /** 项目实践评分 */
    projectPracticeScore?: number;
    /** 内容完整度评分 */
    contentCompletenessScore?: number;
    /** 结构表达评分 */
    structureExpressionScore?: number;
    /** 职业素养评分 */
    professionalPotentialScore?: number;
  }

  /** 改进建议 */
  export interface Suggestion {
    category?: string;
    priority?: string;
    issue?: string;
    recommendation?: string;
  }

  /** 学生就业能力画像 DTO */
  export interface StudentCapabilityProfile {
    /** 专业技能：2～4 句中文，须结合简历中的课程/项目/技术栈写具体证据，避免只堆砌关键词 */
    professionalSkills?: string;
    /** 证书：说明有无证书、名称与含金量；无则写「无」并简述是否影响岗位判断 */
    certificates?: string;
    /** 创新能力：结合竞赛/课题/项目中的创新点、个人角色与可验证结果（勿空洞套话） */
    innovationAbility?: string;
    /** 学习能力：从自学内容、技术栈扩展、问题解决过程等提取可追问证据 */
    learningAbility?: string;
    /** 抗压能力：时间紧/任务重/多线程协作等场景中的职责与交付，尽量量化或写清边界 */
    pressureResistance?: string;
    /** 沟通能力：协作、评审、跨角色对接等经历；无证据则写「简历未体现」 */
    communicationAbility?: string;
    /** 实习/实践能力：项目背景—个人职责—技术实现—结果；突出个人贡献与可量化产出 */
    practicalAbility?: string;
    /** 简历完整度总评（0～100） */
    completenessScore?: number;
    /** 综合竞争力（0～100） */
    competitivenessScore?: number;
    /** 岗位专项评估分项 */
    scoreDetail?: ScoreDetail;
    /** 简历优势点 */
    strengths?: string[];
    /** 缺失技能项 */
    missingSkills?: string[];
    /** 证据不足项 */
    weakEvidenceItems?: string[];
    /** 改进建议 */
    suggestions?: Suggestion[];
    /** 一句话总结 */
    summary?: string;
  }
}

/** * =====================================================================
 * API 请求封装区域
 * 规范：统一使用 request() 进行调用，返回 Promise 响应
 * =====================================================================
 */

/**
 * 获取当前用户的能力画像
 * 对应接口：GET /capability-profile/current
 */
export function fetchGetCurrentCapabilityProfile() {
  return request<CapabilityProfileApi.StudentCapabilityProfile>({
    url: '/capability-profile/current',
    method: 'get'
  });
}

/**
 * 查询当前用户的能力画像（仅查询，不触发生成）
 * 对应接口：GET /capability-profile/current/query
 * 注意：如果画像不存在，返回 null，不会抛出错误
 */
export function fetchQueryCapabilityProfile() {
  return request<CapabilityProfileApi.StudentCapabilityProfile>({
    url: '/capability-profile/current/query',
    method: 'get'
  });
}

/**
 * 生成能力画像（支持从 PDF 解析或文本直接生成）
 * 对应接口：POST /capability-profile/generate
 * 注意：此接口调用AI生成，耗时较长，设置超时时间为5分钟
 */
export function fetchGenerateCapabilityProfile(data: CapabilityProfileApi.CapabilityProfileGenerateReq) {
  return request<CapabilityProfileApi.StudentCapabilityProfile>({
    url: '/capability-profile/generate',
    method: 'post',
    data,
    timeout: 300000 // 5分钟超时，AI生成需要较长时间
  });
}

/**
 * 文件上传结果
 */
export interface FileUploadResult {
  key: string;
  url: string;
  fileName: string;
  fileSize: number;
  contentType: string;
}

/**
 * 上传文件到腾讯云COS
 * 对应接口：POST /files/upload
 * @param file 要上传的文件
 * @param prefix 文件存储前缀，默认为'profile'
 */
export function fetchUploadFile(file: File, prefix = 'profile') {
  const formData = new FormData();
  formData.append('file', file);
  return request<FileUploadResult>({
    url: '/files/upload',
    method: 'post',
    data: formData,
    params: { prefix }
  });
}
