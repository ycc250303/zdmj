import { request } from '../request';
import type * as ResumeApi from './resume.types';

/** * =====================================================================
 * TypeScript 类型定义区域 (DTOs)
 * 规范：与后端 OpenAPI Schema 严格对齐，保障数据结构在前端的强类型约束
 * =====================================================================
 */

export type { ResumeApi };

/** * =====================================================================
 * API 请求封装区域
 * 规范：统一使用 request() 进行调用，返回 Promise 响应
 * =====================================================================
 */

/** 当前用户简历列表。没有技能和经历时为空，有内容时为一条。 */
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
