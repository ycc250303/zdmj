import type { AxiosResponse } from 'axios';
import { BACKEND_ERROR_CODE, createFlatRequest } from '@sa/axios';
import { useAuthStore } from '@/store/modules/auth';
import { getServiceBaseURL } from '@/utils/service';
import { $t } from '@/locales';
import { type ApiErrorBody, normalizeErrorResponse, parseApiErrorBody } from './api-error';
import {
  forceLogout,
  getAuthorization,
  getLogoutCodes,
  resolveRequestErrorInfo,
  shouldForceLogout,
  showErrorMsg
} from './shared';
import type { RequestInstanceState } from './type';

const isHttpProxy = import.meta.env.DEV && import.meta.env.VITE_HTTP_PROXY === 'Y';
const baseURL = getServiceBaseURL(import.meta.env, isHttpProxy);

export const request = createFlatRequest(
  {
    baseURL,
    timeout: 60000
  },
  {
    defaultState: {
      errMsgStack: []
    } as RequestInstanceState,
    transform(response: AxiosResponse<App.Service.Response<any>>) {
      return response.data.data;
    },
    async onRequest(config) {
      const Authorization = getAuthorization();
      Object.assign(config.headers, { Authorization });

      if (config.data instanceof FormData) {
        if (typeof config.headers.delete === 'function') {
          config.headers.delete('Content-Type');
        } else {
          delete config.headers['Content-Type'];
        }
      }

      return config;
    },
    isBackendSuccess(response) {
      return String(response.data.code) === import.meta.env.VITE_SERVICE_SUCCESS_CODE;
    },
    async onBackendFail(response) {
      const authStore = useAuthStore();
      const errorBody = normalizeErrorResponse(response.data as ApiErrorBody);
      const responseCode = errorBody.code;

      function handleLogout() {
        authStore.resetStore();
      }

      function logoutAndCleanup() {
        handleLogout();
        window.removeEventListener('beforeunload', handleLogout);
        request.state.errMsgStack = request.state.errMsgStack.filter(msg => msg !== errorBody.msg);
      }

      const logoutCodes = getLogoutCodes();
      if (logoutCodes.includes(responseCode) || response.status === 401) {
        handleLogout();
        return null;
      }

      const modalLogoutCodes = import.meta.env.VITE_SERVICE_MODAL_LOGOUT_CODES?.split(',') || [];
      if (modalLogoutCodes.includes(responseCode) && !request.state.errMsgStack?.includes(errorBody.msg)) {
        request.state.errMsgStack = [...(request.state.errMsgStack || []), errorBody.msg];
        window.addEventListener('beforeunload', handleLogout);
        window.$dialog?.error({
          title: $t('common.error'),
          content: errorBody.msg,
          positiveText: $t('common.confirm'),
          maskClosable: false,
          closeOnEsc: false,
          onPositiveClick() {
            logoutAndCleanup();
          },
          onClose() {
            logoutAndCleanup();
          }
        });
        return null;
      }

      return null;
    },
    async onError(error) {
      const { httpStatus, message, backendErrorCode } = resolveRequestErrorInfo(error);

      if (shouldForceLogout(httpStatus, backendErrorCode)) {
        await forceLogout();
        return;
      }

      let displayMessage = message;
      if (error.code === BACKEND_ERROR_CODE && !displayMessage) {
        const parsed = parseApiErrorBody(error.response?.data as ApiErrorBody);
        displayMessage = parsed.msg || displayMessage;
      }

      const modalLogoutCodes = import.meta.env.VITE_SERVICE_MODAL_LOGOUT_CODES?.split(',') || [];
      if (modalLogoutCodes.includes(backendErrorCode)) {
        return;
      }

      showErrorMsg(request.state, displayMessage);
    }
  }
);
