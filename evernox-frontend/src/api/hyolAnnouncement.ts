import { get, post } from '@/utils/request'
import type { Result } from '@/types/user'
import type { PageResult } from '@/api/image'
import type { HyolAnnouncement, HyolRefreshResult } from '@/types/hyolAnnouncement'

/** 公告列表（首次访问后端会自动抓官网，可能较慢，放宽超时） */
export const getHyolAnnouncements = (
  page: number,
  size: number
): Promise<Result<PageResult<HyolAnnouncement>>> => {
  return get(`/hyol/announcement/list?page=${page}&size=${size}`, { timeout: 90000 })
}

/** 公告详情 */
export const getHyolAnnouncementDetail = (id: number): Promise<Result<HyolAnnouncement>> => {
  return get(`/hyol/announcement/${id}`)
}

/** 管理员手动刷新官方公告 */
export const refreshHyolAnnouncements = (): Promise<Result<HyolRefreshResult>> => {
  return post('/hyol/announcement/refresh', undefined, { timeout: 120000 })
}
