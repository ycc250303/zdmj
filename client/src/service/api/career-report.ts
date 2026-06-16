import { request } from '../request';

/**
 * =====================================================================
 * 职业发展报告（Career Development Report）相关接口
 * 对应后端：CareerDevelopmentReportController（基路径 /career-reports）
 * =====================================================================
 */
export namespace CareerReportApi {
  /**
   * 报告状态：1=草稿 / 2=已校验 / 3=已发布 / 4=校验未通过
   */
  export type ReportStatus = 1 | 2 | 3 | 4;

  /**
   * 知识来源（RAG 命中）
   */
  export interface KnowledgeSource {
    title?: string;
    snippet?: string;
    url?: string;
    score?: number;
    documentId?: number;
    chunkIndex?: number;
    metadata?: Record<string, unknown>;
    [key: string]: any;
  }

  /**
   * 完整性检查结果（对应后端 CareerReportCheckDTO）
   */
  export interface CareerReportCheck {
    /** 是否通过完整性检查 */
    passed?: boolean;
    /** 完整度评分 0~100 */
    completenessScore?: number;
    /** 风险等级：low / medium / high */
    riskLevel?: 'low' | 'medium' | 'high' | string;
    /** 缺失的章节键或展示名 */
    missingSections?: string[];
    /** 行动计划中不可执行或信息不足的项 */
    nonActionableItems?: string[];
    /** 证据引用薄弱或缺失的项 */
    weakEvidenceItems?: string[];
  }

  /**
   * 报告主体（对应后端 CareerReportDTO）
   * reportContent 是 LLM 输出的结构化对象，业务上字段较灵活，使用 Record 兼容。
   */
  export interface CareerReport {
    id?: number;
    jobId?: number;
    /** 1=草稿 / 2=已校验 / 3=已发布 / 4=校验未通过 */
    status?: ReportStatus;
    /** 完整度评分 0~100 */
    completenessScore?: number;
    /** 版本号 */
    version?: number;
    /** 是否为该岗位下的最新版本 */
    latest?: boolean;
    /** 生成/润色使用的 prompt 名称 */
    promptName?: string;
    /** 结构化报告正文（章节键 -> 内容）*/
    reportContent?: Record<string, any>;
    /** 质量与完整性标记 */
    qualityFlags?: Record<string, any>;
    /** RAG 命中的知识来源 */
    knowledgeSources?: KnowledgeSource[];
  }

  /**
   * 生成报告请求体（对应后端 CareerReportGenerateReqDTO）
   */
  export interface CareerReportGenerateReq {
    /** 用户额外目标偏好（城市/行业/岗位倾向等） */
    userPreference?: string;
    /** 生成侧重点（如「补齐项目经历」「强化算法方向」） */
    focus?: string;
  }

  /**
   * 润色请求体（对应后端 CareerReportPolishReqDTO）
   */
  export interface CareerReportPolishReq {
    /** 润色要求 */
    instruction?: string;
  }

  /**
   * 手动编辑请求体（对应后端 CareerReportUpdateReqDTO）
   */
  export interface CareerReportUpdateReq {
    /** 手动编辑后的结构化正文 */
    reportContent?: Record<string, any>;
  }
}

/**
 * 查询当前用户针对某岗位的最新职业发展报告（不触发生成；不存在返回 null）。
 * 后端：GET /career-reports/jobs/{jobId}
 */
export function fetchGetLatestCareerReport(jobId: number | string) {
  return request<CareerReportApi.CareerReport | null>({
    url: `/career-reports/jobs/${jobId}`,
    method: 'get'
  });
}

/**
 * 生成职业发展报告（同步，会触发 LLM；耗时较长）。
 * 后端：POST /career-reports/jobs/{jobId}（body 可选）
 */
export function fetchGenerateCareerReport(
  jobId: number | string,
  req?: CareerReportApi.CareerReportGenerateReq
) {
  return request<CareerReportApi.CareerReport>({
    url: `/career-reports/jobs/${jobId}`,
    method: 'post',
    data: req ?? {},
    timeout: 600000 // 10 分钟超时（聚合多源 + LLM，耗时较长）
  });
}

/**
 * 智能润色报告（生成新版本）。
 * 后端：POST /career-reports/{id}/polish
 */
export function fetchPolishCareerReport(
  id: number | string,
  req?: CareerReportApi.CareerReportPolishReq
) {
  return request<CareerReportApi.CareerReport>({
    url: `/career-reports/${id}/polish`,
    method: 'post',
    data: req ?? {},
    timeout: 600000
  });
}

/**
 * 报告完整性检查（本地规则 + LLM 复核），结果会写回当前报告记录。
 * 后端：POST /career-reports/{id}/integrity-check
 */
export function fetchCheckCareerReportIntegrity(id: number | string) {
  return request<CareerReportApi.CareerReportCheck>({
    url: `/career-reports/${id}/integrity-check`,
    method: 'post',
    timeout: 300000
  });
}

/**
 * 保存手动编辑后的报告（生成新版本）。
 * 后端：PUT /career-reports/{id}
 */
export function fetchSaveCareerReportManualEdit(
  id: number | string,
  req: CareerReportApi.CareerReportUpdateReq
) {
  return request<CareerReportApi.CareerReport>({
    url: `/career-reports/${id}`,
    method: 'put',
    data: req
  });
}
