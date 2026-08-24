import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/api/image'
import type { Result } from '@/types/user'
import type { AdminSiteListParams, SiteLink, SiteStats, SiteTag } from '@/types/site'

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

/** 站点列表（管理端，可按状态筛选） */
export const getAdminSites = (
  params: AdminSiteListParams
): Promise<Result<PageResult<SiteLink>>> => {
  return get(`/admin/site/list${buildQuery({ ...params })}`)
}

/** 审批通过，必须带标签 */
export const approveSite = (id: number, tagIds: number[]): Promise<Result<void>> => {
  return post(`/admin/site/${id}/approve`, { tagIds })
}

/** 审批驳回，必须带原因 */
export const rejectSite = (id: number, reason: string): Promise<Result<void>> => {
  return post(`/admin/site/${id}/reject`, { reason })
}

/** 撤下已公开站点 */
export const offlineSite = (id: number): Promise<Result<void>> => {
  return post(`/admin/site/${id}/offline`)
}

/** 调整已公开站点的标签 */
export const updateSiteTags = (id: number, tagIds: number[]): Promise<Result<void>> => {
  return put(`/admin/site/${id}/tags`, { tagIds })
}

/** 标签库列表（含关联站点数） */
export const getAdminSiteTags = (): Promise<Result<SiteTag[]>> => {
  return get('/admin/site/tag')
}

/** 新建标签 */
export const createSiteTag = (data: {
  name: string
  sort: number
}): Promise<Result<SiteTag>> => {
  return post('/admin/site/tag', data)
}

/** 更新标签 */
export const updateSiteTag = (
  id: number,
  data: { name: string; sort: number }
): Promise<Result<SiteTag>> => {
  return put(`/admin/site/tag/${id}`, data)
}

/** 删除标签（后端级联清理关联） */
export const deleteSiteTag = (id: number): Promise<Result<void>> => {
  return del(`/admin/site/tag/${id}`)
}

/** 审批统计 */
export const getAdminSiteStats = (): Promise<Result<SiteStats>> => {
  return get('/admin/site/stats')
}
