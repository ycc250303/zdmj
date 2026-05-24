/** RFC 9457 Problem Details 或 legacy Result 错误体 */
export interface ApiErrorBody {
  code?: number | string;
  detail?: string;
  msg?: string;
  title?: string;
  status?: number;
}

/**
 * 统一解析后端错误响应（Problem Details 的 detail/code 与 Result 的 msg/code 兼容）。
 */
export function parseApiErrorBody(data: ApiErrorBody | undefined | null): { code: string; msg: string } {
  if (!data) {
    return { code: '', msg: '' };
  }
  const code = data.code != null ? String(data.code) : '';
  const msg = data.detail || data.msg || data.title || '';
  return { code, msg };
}

/**
 * 将 Problem Details 归一化为与成功 Result 同形的对象，便于 onBackendFail 复用。
 */
export function normalizeErrorResponse(data: ApiErrorBody | undefined | null): App.Service.Response<null> {
  const { code, msg } = parseApiErrorBody(data);
  return { code, msg, data: null };
}
