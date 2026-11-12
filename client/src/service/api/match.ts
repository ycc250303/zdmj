import { request } from '../request';
import type * as MatchApi from './match.types';

/**
 * =====================================================================
 * 人岗匹配相关接口
 * 对应后端：JobStudentMatchController（基路径 /matches）
 * =====================================================================
 */

export type { MatchApi };

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
 * 生成人岗匹配并返回结果。
 * 后端：POST /matches/jobs/{jobId}（请求体可选）
 */
export function fetchGenerateJobStudentMatch(jobId: number | string, req?: MatchApi.JobStudentMatchGenerateReq) {
  return request<MatchApi.JobStudentMatch>({
    url: `/matches/jobs/${jobId}`,
    method: 'post',
    data: req ?? {},
    timeout: 300000
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
