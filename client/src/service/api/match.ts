import { request } from '../request';

/**
 * =====================================================================
 * 人岗匹配相关接口
 * 对应后端：JobStudentMatchController（基路径 /matches）
 * =====================================================================
 */
export namespace MatchApi {
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
    keySkillMatchRate?: number;
    summary?: string;
    updatedAt?: string;
  }

  // 分页查询参数
  export interface MatchPageQuery extends Api.Common.PageQueryParams {}
}

/**
 * 分页查询当前用户已匹配过的岗位记录
 * 后端：GET /matches?page=&limit=
 */
export function fetchGetMyMatchPage(params?: MatchApi.MatchPageQuery) {
  return request<Api.Common.PageDTO<MatchApi.JobStudentMatchListItem>>({
    url: '/matches',
    method: 'get',
    params
  });
}

/**
 * 查询当前用户与该岗位的最新匹配结果（不触发 LLM；不存在返回 null）
 * 后端：GET /matches/jobs/{jobId}
 */
export function fetchGetJobStudentMatch(jobId: number | string) {
  return request<MatchApi.JobStudentMatch | null>({
    url: `/matches/jobs/${jobId}`,
    method: 'get'
  });
}

/**
 * 生成人岗匹配分析（覆盖式，触发 LLM）
 * 后端：POST /matches/jobs/{jobId}（请求体可选）
 */
export function fetchGenerateJobStudentMatch(
  jobId: number | string,
  req?: MatchApi.JobStudentMatchGenerateReq
) {
  return request<MatchApi.JobStudentMatch>({
    url: `/matches/jobs/${jobId}`,
    method: 'post',
    data: req ?? {},
    timeout: 300000 // 5 分钟超时，AI 生成耗时较长
  });
}

/**
 * 查询岗位的默认匹配权重（用于前端权重面板初始化）
 * 后端：GET /matches/jobs/{jobId}/weights
 */
export function fetchGetJobMatchDefaultWeights(jobId: number | string) {
  return request<MatchApi.MatchWeightConfig>({
    url: `/matches/jobs/${jobId}/weights`,
    method: 'get'
  });
}
