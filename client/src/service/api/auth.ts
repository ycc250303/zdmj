import { request } from '../request';

/**
 * Login
 *
 * @param userName User name
 * @param password Password
 */
export function fetchLogin(userName: string, password: string) {
  return request<Api.Auth.LoginToken>({
    url: '/users/login',
    method: 'post',
    data: {
      usernameOrEmail: userName,
      password: password
    }
  });
}

/** Get user info */
export function fetchGetUserInfo() {
  return request<Api.Auth.UserInfo>({ url: '/auth/getUserInfo' });
}

/**
 * Refresh token
 *
 * @param refreshToken Refresh token
 */
export function fetchRefreshToken(refreshToken: string) {
  return request<Api.Auth.LoginToken>({
    url: '/auth/refreshToken',
    method: 'post',
    data: {
      refreshToken
    }
  });
}

/**
 * return custom backend error
 *
 * @param code error code
 * @param msg error message
 */
export function fetchCustomBackendError(code: string, msg: string) {
  return request({ url: '/auth/error', params: { code, msg } });
}

/**
 * 发送注册验证码
 *
 * @param email 邮箱地址
 */
export function fetchGetVerificationCode(email: string) {
  return request({
    url: '/users/verification-codes',
    method: 'post',
    params: {
      email: email
    }
  });
}

/**
 * 注册接口
 * * @param data
 */
export function fetchRegister(username: string, password: string, email: string, verificationCode: string) {
  return request({
    url: '/users',
    method: 'post',
    data: {
      username: username,
      password: password,
      email: email,
      verificationCode: verificationCode
    }
  });
}

/**
 * 重置密码
 */
export function fetchResetPassword(email: string, verificationCode: string, newPassword: string) {
  return request({
    url: '/users/password',
    method: 'put',
    data: {
      email: email,
      verificationCode: verificationCode,
      newPassword: newPassword
    }
  });
}