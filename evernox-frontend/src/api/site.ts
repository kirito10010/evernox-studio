import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/api/image'
import type { Result } from '@/types/user'
import type {
  PublicSiteListParams,
  SiteLink,
  SiteLinkPayload,
  SiteStats,
  SiteTag,
} from '@/types/site'

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

/** 新建分享（后端强制落为私有） */
export const createSite = (data: SiteLinkPayload): Promise<Result<SiteLink>> => {
  return post('/site', data)
}

/** 编辑分享（仅私有/已驳回可改） */
export const updateSite = (id: number, data: SiteLinkPayload): Promise<Result<SiteLink>> => {
  return put(`/site/${id}`, data)
}

/** 删除分享 */
export const deleteSite = (id: number): Promise<Result<void>> => {
  return del(`/site/${id}`)
}

/** 申请公开 */
export const submitSite = (id: number): Promise<Result<void>> => {
  return post(`/site/${id}/submit`)
}

/** 撤回申请 / 自行撤下 */
export const withdrawSite = (id: number): Promise<Result<void>> => {
  return post(`/site/${id}/withdraw`)
}

/** 我的分享列表 */
export const getMySites = (params: {
  page: number
  size: number
  status?: number | null
}): Promise<Result<PageResult<SiteLink>>> => {
  return get(`/site/list${buildQuery({ ...params })}`)
}

/** 公开导航列表 */
export const getPublicSites = (
  params: PublicSiteListParams
): Promise<Result<PageResult<SiteLink>>> => {
  return get(`/site/public${buildQuery({ ...params })}`)
}

/** 分享详情 */
export const getSiteById = (id: number): Promise<Result<SiteLink>> => {
  return get(`/site/${id}`)
}

/** 标签库（只读，供筛选） */
export const getSiteTags = (): Promise<Result<SiteTag[]>> => {
  return get('/site/tags')
}

/** 我的分享统计 */
export const getSiteStats = (): Promise<Result<SiteStats>> => {
  return get('/site/stats')
}
