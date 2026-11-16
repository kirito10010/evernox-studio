import { get } from '@/utils/request'
import type { Result } from '@/types/user'

export interface AdminOrgJoinedUser {
  userId: number
  username: string | null
  email: string | null
  appliedAt: string | null
  reviewedAt: string | null
}

export interface AdminOrgOverview {
  id: number
  name: string
  ownerId: number | null
  ownerUsername: string | null
  ownerEmail: string | null
  ownerRole: string | null
  createdAt: string | null
  joinedUserCount: number
  memberCount: number
  users: AdminOrgJoinedUser[]
}

/** 管理员组织总览：所有账号创建的组织与已加入用户 */
export const getAdminOrgOverview = (): Promise<Result<AdminOrgOverview[]>> => {
  return get('/admin/org-overview')
}
