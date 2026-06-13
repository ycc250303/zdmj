import { useAuthStore } from '@/store/modules/auth';
import { localStg } from '@/utils/storage';
import { fetchRefreshToken } from '../api';
import { parseApiErrorBody, type ApiErrorBody } from './api-error';
import type { RequestInstanceState } from './type';

export function getAuthorization() {
  const token = localStg.get('token');
  const Authorization = token ? `Bearer ${token}` : null;

  return Authorization;
}

/** 触发强制登出的业务错误码（默认含 USER_NOT_LOGIN=1002） */
export function getLogoutCodes(): string[] {
  const fromEnv = import.meta.env.VITE_SERVICE_LOGOUT_CODES?.split(',')
    .map(code => code.trim())
    .filter(Boolean);
  return fromEnv?.length ? fromEnv : ['1002'];
}

export function shouldForceLogout(httpStatus?: number, backendErrorCode?: string): boolean {
  if (httpStatus === 401) {
    return true;
  }
  return Boolean(backendErrorCode && getLogoutCodes().includes(backendErrorCode));
}

export function resolveRequestErrorInfo(error: {
  message?: string;
  response?: { status?: number; data?: unknown };
}): { httpStatus?: number; message: string; backendErrorCode: string } {
  const httpStatus = error.response?.status;
  let message = error.message ?? '';
  let backendErrorCode = '';
  if (error.response?.data) {
    const parsed = parseApiErrorBody(error.response.data as ApiErrorBody);
    message = parsed.msg || message;
    backendErrorCode = parsed.code;
  }
  return { httpStatus, message, backendErrorCode };
}

export async function forceLogout() {
  const authStore = useAuthStore();
  await authStore.resetStore();
}

/** refresh token */
async function handleRefreshToken() {
  const { resetStore } = useAuthStore();

  const rToken = localStg.get('refreshToken') || '';
  const { error, data } = await fetchRefreshToken(rToken);
  if (!error) {
    localStg.set('token', data.token);
    localStg.set('refreshToken', data.refreshToken);
    return true;
  }

  resetStore();

  return false;
}

export async function handleExpiredRequest(state: RequestInstanceState) {
  if (!state.refreshTokenPromise) {
    state.refreshTokenPromise = handleRefreshToken();
  }

  const success = await state.refreshTokenPromise;

  setTimeout(() => {
    state.refreshTokenPromise = null;
  }, 1000);

  return success;
}

export function showErrorMsg(state: RequestInstanceState, message: string) {
  if (!state.errMsgStack?.length) {
    state.errMsgStack = [];
  }

  const isExist = state.errMsgStack.includes(message);

  if (!isExist) {
    state.errMsgStack.push(message);

    window.$message?.error(message, {
      onLeave: () => {
        state.errMsgStack = state.errMsgStack.filter(msg => msg !== message);

        setTimeout(() => {
          state.errMsgStack = [];
        }, 5000);
      }
    });
  }
}
