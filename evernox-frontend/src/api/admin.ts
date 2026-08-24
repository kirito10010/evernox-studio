import { get, post, put, del } from '@/utils/request'
import type {
  AdminUserCreateRequest,
  AdminUserListParams,
  AdminUserUpdateRequest,
  Result,
  UserInfoResponse,
  UserStats,
} from '@/types/user'
import type { PageResult } from '@/api/image'

/** 拼接查询串，跳过空值 */
const buildQuery = (params: Record<string, unknown>): string => {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    search.append(key, String(value))
  }
  const qs = search.toString()
  return qs ? `?${qs}` : ''
}

/** 分页查询用户（后端已排除管理员账号） */
export const getAdminUsers = (
  params: AdminUserListParams
): Promise<Result<PageResult<UserInfoResponse>>> => {
  return get(`/admin/user/list${buildQuery({ ...params })}`)
}

/** 用户统计 */
export const getAdminUserStats = (): Promise<Result<UserStats>> => {
  return get('/admin/user/stats')
}

/** 用户详情 */
export const getAdminUser = (id: number): Promise<Result<UserInfoResponse>> => {
  return get(`/admin/user/${id}`)
}

/** 创建用户 */
export const createAdminUser = (
  data: AdminUserCreateRequest
): Promise<Result<UserInfoResponse>> => {
  return post('/admin/user', data)
}

/** 更新用户 */
export const updateAdminUser = (
  id: number,
  data: AdminUserUpdateRequest
): Promise<Result<UserInfoResponse>> => {
  return put(`/admin/user/${id}`, data)
}

/** 启用/禁用 */
export const updateAdminUserStatus = (
  id: number,
  status: number
): Promise<Result<UserInfoResponse>> => {
  return put(`/admin/user/${id}/status?status=${status}`)
}

/** 删除用户（级联清理其图片、相册与磁盘文件，不可恢复） */
export const deleteAdminUser = (id: number): Promise<Result<void>> => {
  return del(`/admin/user/${id}`)
}

/** 批量删除 */
export const deleteAdminUsers = (ids: number[]): Promise<Result<void>> => {
  return del(`/admin/user/batch?ids=${ids.join(',')}`)
}
