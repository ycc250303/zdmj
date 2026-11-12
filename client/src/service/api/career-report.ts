import { request } from '../request';
import type * as CareerReportApi from './career-report.types';

/**
 * =====================================================================
 * 职业发展报告（Career Development Report）相关接口
 * 对应后端：CareerDevelopmentReportController（基路径 /career-reports）
 * =====================================================================
 */

export type { CareerReportApi };

/**
 * 查询当前用户针对某岗位的职业发展报告（不触发生成；不存在返回 null）。
 * 后端：GET /career-reports/jobs/{jobId}
 */
export function fetchGetLatestCareerReport(jobId: number | string) {
  return request<CareerReportApi.CareerReport | null>({
    url: `/career-reports/jobs/${jobId}`,
    method: 'get'
  });
}

/**
 * 生成职业发展报告并覆盖保存。
 * 后端：POST /career-reports/jobs/{jobId}（body 可选）
 */
export function fetchGenerateCareerReport(jobId: number | string, req?: CareerReportApi.CareerReportGenerateReq) {
  return request<CareerReportApi.CareerReport>({
    url: `/career-reports/jobs/${jobId}`,
    method: 'post',
    data: req ?? {},
    timeout: 300000
  });
}

/**
 * 润色报告并覆盖保存。
 * 后端：POST /career-reports/{id}/polish
 */
export function fetchPolishCareerReport(id: number | string, req?: CareerReportApi.CareerReportPolishReq) {
  return request<CareerReportApi.CareerReport>({
    url: `/career-reports/${id}/polish`,
    method: 'post',
    data: req ?? {},
    timeout: 300000
  });
}

/**
 * 检查报告完整性并返回检查结果。
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
 * 保存手动编辑后的报告，并覆盖原报告。
 * 后端：PUT /career-reports/{id}
 */
export function fetchSaveCareerReportManualEdit(id: number | string, req: CareerReportApi.CareerReportUpdateReq) {
  return request<CareerReportApi.CareerReport>({
    url: `/career-reports/${id}`,
    method: 'put',
    data: req
  });
}
