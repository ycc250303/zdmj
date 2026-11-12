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
      password
    }
  });
}

/**
 * 发送验证码
 *
 * @param email 邮箱地址
 * @param type 验证码类型，register 为注册，reset_password 为重置密码
 */
export function fetchGetVerificationCode(email: string, type: 'register' | 'reset_password') {
  return request({
    url: '/users/verification-codes',
    method: 'post',
    params: {
      email,
      type
    }
  });
}

/**
 * 注册接口
 * * @param data
 */
export function fetchRegister(payload: {
  username: string;
  password: string;
  email: string;
  verificationCode: string;
}) {
  return request({
    url: '/users',
    method: 'post',
    data: payload
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
      email,
      verificationCode,
      newPassword
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
