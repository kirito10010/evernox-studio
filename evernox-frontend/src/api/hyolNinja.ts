import { get, post } from '@/utils/request'
import type { Result } from '@/types/user'
import type { PageResult } from '@/api/image'
import type { HyolNinja, NinjaRefreshResult } from '@/types/hyolNinja'

/** 忍者列表（首次访问后端会自动全量抓取，可能较慢，放宽超时） */
export const getNinjaList = (
  page: number,
  size: number,
  keyword?: string,
  attr?: string,
  hurtType?: string,
  chaseStatus?: string,
  hurtStatus?: string,
  rare?: string
): Promise<Result<PageResult<HyolNinja>>> => {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (keyword) params.set('keyword', keyword)
  if (attr) params.set('attr', attr)
  if (hurtType) params.set('hurtType', hurtType)
  if (chaseStatus) params.set('chaseStatus', chaseStatus)
  if (hurtStatus) params.set('hurtStatus', hurtStatus)
  if (rare) params.set('rare', rare)
  return get(`/hyol/ninja/list?${params.toString()}`, { timeout: 180000 })
}

/** 管理员手动增量刷新忍者数据 */
export const refreshNinjas = (): Promise<Result<NinjaRefreshResult>> => {
  return post('/hyol/ninja/refresh', undefined, { timeout: 300000 })
}
