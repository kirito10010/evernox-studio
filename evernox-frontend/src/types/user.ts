export interface User {
  id: number
  username: string
  email: string
  role: 'admin' | 'super_member' | 'member'
  status: number
  points: number
  createdAt: string
  updatedAt: string
  lastLoginAt?: string
}

export interface UserInfoResponse {
  id: number
  username: string
  email: string
  role: 'admin' | 'super_member' | 'member'
  status: number
  points: number
  createdAt: string
  lastLoginAt?: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email: string
}

export interface PasswordResetSendCodeRequest {
  email: string
}

export interface PasswordResetConfirmRequest {
  email: string
  code: string
  newPassword: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

export interface Result<T> {
  code: number
  message: string
  data: T
}

export type UserRole = 'admin' | 'super_member' | 'member'

export const UserRoleMap: Record<UserRole, string> = {
  admin: '管理员',
  super_member: '超级会员',
  member: '普通成员',
}

export const UserRoleColor: Record<UserRole, string> = {
  admin: 'danger',
  super_member: 'warning',
  member: 'info',
}

/** 本模块可分配的角色，不含 admin */
export type AssignableRole = 'member' | 'super_member'

export interface AdminUserListParams {
  page: number
  size: number
  keyword?: string
  role?: AssignableRole | ''
  status?: number | null
  startDate?: string
  endDate?: string
  sortField?: 'createdAt' | 'lastLoginAt' | 'points' | 'username'
  sortOrder?: 'asc' | 'desc'
}

export interface AdminUserCreateRequest {
  username: string
  password: string
  email: string
  role: AssignableRole
  status: number
  points: number
}

export interface AdminUserUpdateRequest {
  email: string
  role: AssignableRole
  status: number
  points: number
  /** 留空表示不修改；为空时不要传该字段 */
  password?: string
}

export interface UserStats {
  total: number
  members: number
  superMembers: number
  disabled: number
}
