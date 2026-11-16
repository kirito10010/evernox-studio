import { get, post } from '@/utils/request'
import type { Result } from '@/types/user'
import type { HyolActivitySyncStatus, HyolActivityTimeline } from '@/types/hyolActivity'

/** 活动时间轴数据（names 为活动全名多选，keyword 为名称模糊匹配；不传返回全部） */
export const getActivityTimeline = (
  names: string[] = [],
  keyword = ''
): Promise<Result<HyolActivityTimeline>> => {
  const params = new URLSearchParams()
  if (names.length > 0) params.set('names', names.join(','))
  if (keyword.trim()) params.set('keyword', keyword.trim())
  const qs = params.toString()
  return get(`/hyol/activity/timeline${qs ? `?${qs}` : ''}`)
}

/** 同步进度（前端轮询） */
export const getActivitySyncStatus = (): Promise<Result<HyolActivitySyncStatus>> => {
  return get('/hyol/activity/sync/status')
}

/** 触发同步（仅管理员）：默认增量，full=true 时全量重扫 */
export const startActivitySync = (full = false): Promise<Result<HyolActivitySyncStatus>> => {
  return post(`/hyol/activity/sync?full=${full}`)
}
