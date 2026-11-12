// 单一维度的匹配对比结构（对应 DimensionMatchDTO）
export interface DimensionMatch {
  jobSide?: string; // 岗位侧要求摘要
  studentSide?: string; // 学生侧表现摘要
  score?: number; // 0~100
  gap?: string; // 差距描述
  evidence?: string[]; // 证据片段
}

// 四维度权重配置（对应 MatchWeightConfigDTO）
// 后端使用 BigDecimal 序列化，前端按 number 接收
export interface MatchWeightConfig {
  basic?: number; // 基础要求
  professionalSkill?: number; // 职业技能
  professionalQuality?: number; // 职业素养
  developmentPotential?: number; // 发展潜力
}

// 人岗匹配分析结果（对应 JobStudentMatchDTO）
export interface JobStudentMatch {
  id?: number; // 匹配记录编号
  jobId?: number;
  targetRoleType?: string; // 岗位类型展示值
  overallScore?: number; // 综合匹配度 0~100
  dimensions?: {
    basic?: DimensionMatch;
    professionalSkill?: DimensionMatch;
    professionalQuality?: DimensionMatch;
    developmentPotential?: DimensionMatch;
    [key: string]: DimensionMatch | undefined;
  };
  weights?: MatchWeightConfig; // 本次匹配的权重快照
  matchedHighlights?: string[]; // 命中亮点
  criticalGaps?: string[]; // 关键差距
  matchedKeywords?: string[]; // 命中的岗位关键词
  missingKeywords?: string[]; // 缺失的岗位关键词
  keySkillMatchRate?: number; // 关键技能匹配率 0~1
  summary?: string; // 一句话总结
}

// 生成请求体（对应 JobStudentMatchGenerateReqDTO）
export interface JobStudentMatchGenerateReq {
  weights?: MatchWeightConfig; // 可选自定义权重
}

// 匹配记录列表项（对应 JobStudentMatchListItemDTO）
export interface JobStudentMatchListItem {
  id?: number;
  jobId?: number;
  jobName?: string;
  companyName?: string;
  overallScore?: number;
  summary?: string;
  updatedAt?: string;
}

// 分页查询参数
export type MatchPageQuery = Api.Common.PageQueryParams;
