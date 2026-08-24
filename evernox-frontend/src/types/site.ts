/** 网站分享相关类型 */

/** 站点状态：公开与否只看这一个字段 */
export const SiteStatus = {
  PRIVATE: 0,
  PENDING: 1,
  PUBLIC: 2,
  REJECTED: 3,
} as const

export type SiteStatusValue = (typeof SiteStatus)[keyof typeof SiteStatus]

export const SiteStatusMap: Record<number, string> = {
  0: '私有',
  1: '待审批',
  2: '已公开',
  3: '已驳回',
}

export const SiteStatusColor: Record<number, 'info' | 'warning' | 'success' | 'danger'> = {
  0: 'info',
  1: 'warning',
  2: 'success',
  3: 'danger',
}

export interface SiteTag {
  id: number
  name: string
  sort: number
  /** 关联站点数，仅标签管理场景返回 */
  siteCount?: number
}

export interface SiteLink {
  id: number
  userId: number
  title: string
  url: string
  description: string | null
  coverImageId: number | null
  status: number
  rejectReason: string | null
  submittedAt: string | null
  reviewedAt: string | null
  createdAt: string
  updatedAt: string
  ownerName: string | null
  reviewerName: string | null
  tags: SiteTag[]
}

export interface SiteLinkPayload {
  title: string
  url: string
  description?: string | null
  coverImageId?: number | null
}

export interface SiteStats {
  mine: number | null
  pending: number | null
  published: number | null
  rejected: number | null
}

export interface PublicSiteListParams {
  page: number
  size: number
  keyword?: string
  /** 逗号分隔的标签ID */
  tagIds?: string
}

export interface AdminSiteListParams {
  page: number
  size: number
  status?: number | null
  userId?: number | null
  keyword?: string
  sortField?: 'createdAt' | 'submittedAt' | 'reviewedAt' | 'title'
  sortOrder?: 'asc' | 'desc'
}
