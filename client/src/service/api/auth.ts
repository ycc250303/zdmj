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

/**
 * 校验用户名是否已存在（注册/编辑表单实时校验用）
 * 后端：GET /users/validation/username?username=xxx
 * @returns true 表示已存在
 */
export function fetchValidateUsername(username: string) {
  return request<boolean>({
    url: '/users/validation/username',
    method: 'get',
    params: { username }
  });
}

/**
 * 校验邮箱是否已存在
 * 后端：GET /users/validation/email?email=xxx
 * @returns true 表示已存在
 */
export function fetchValidateEmail(email: string) {
  return request<boolean>({
    url: '/users/validation/email',
    method: 'get',
    params: { email }
  });
}

/**
 * 根据 ID 查询用户信息
 * 后端：GET /users/{id}
 */
export function fetchGetUserById(id: number | string) {
  return request<Api.Auth.UserInfo>({
    url: `/users/${id}`,
    method: 'get'
  });
}