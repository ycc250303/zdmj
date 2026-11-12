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
  /** 生成/润色使用的 prompt 名称 */
  promptName?: string;
  /** 结构化报告正文（章节键 -> 内容） */
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
