import { get, post } from '@/utils/request'
import type { Result } from '@/types/user'
import type { PageResult } from '@/api/image'

export interface VisitOverview {
  todayActiveUsers: number
  todayVisits: number
  todayLogins: number
  weekActiveUsers: number
}

export interface VisitTrendItem {
  date: string
  visits: number
  activeUsers: number
  logins: number
}

export interface VisitUserRankItem {
  userId: number
  username: string
  visitCount: number
  lastVisitAt: string
}

export interface VisitLogItem {
  id: number
  userId: number
  username: string
  type: string
  ip: string
  userAgent: string
  path: string
  createdAt: string
}

export const trackVisit = (path: string): Promise<Result<void>> => {
  return post('/visit/track', { path })
}

export const getVisitOverview = (): Promise<Result<VisitOverview>> => {
  return get('/admin/visit/overview')
}

export const getVisitTrend = (days: number): Promise<Result<VisitTrendItem[]>> => {
  return get(`/admin/visit/trend?days=${days}`)
}

export const getVisitRank = (top: number): Promise<Result<VisitUserRankItem[]>> => {
  return get(`/admin/visit/rank?top=${top}`)
}

export const getVisitRecent = (params: {
  page: number
  size: number
  type?: string
  username?: string
}): Promise<Result<PageResult<VisitLogItem>>> => {
  const qs = new URLSearchParams()
  qs.set('page', String(params.page))
  qs.set('size', String(params.size))
  if (params.type) qs.set('type', params.type)
  if (params.username) qs.set('username', params.username)
  return get(`/admin/visit/recent?${qs.toString()}`)
}
