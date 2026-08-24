import { get, post } from '@/utils/request'
import type {
  LoginRequest,
  RegisterRequest,
  AuthResponse,
  UserInfoResponse,
  PasswordResetSendCodeRequest,
  PasswordResetConfirmRequest,
  Result,
} from '@/types/user'

/**
 * 用户登录
 */
export const login = (data: LoginRequest): Promise<Result<AuthResponse>> => {
  return post('/auth/login', data)
}

/**
 * 用户注册
 */
export const register = (data: RegisterRequest): Promise<Result<void>> => {
  return post('/auth/register', data)
}

/**
 * 发送密码重置验证码
 */
export const sendResetCode = (data: PasswordResetSendCodeRequest): Promise<Result<void>> => {
  return post('/auth/password-reset/send-code', data)
}

/**
 * 校验验证码并重置密码
 */
export const resetPassword = (data: PasswordResetConfirmRequest): Promise<Result<void>> => {
  return post('/auth/password-reset/confirm', data)
}

/**
 * 获取当前用户信息
 */
export const getUserInfo = (): Promise<Result<UserInfoResponse>> => {
  return get('/auth/me')
}

/**
 * 刷新Token
 */
export const refreshToken = (token: string): Promise<Result<AuthResponse>> => {
  return post('/auth/refresh', null, {
    headers: {
      'Refresh-Token': token,
    },
  })
}

/**
 * 登出
 */
export const logout = (): Promise<Result<void>> => {
  return post('/auth/logout')
}
