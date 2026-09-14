import { get, post, del } from '@/utils/request'
import type { Result } from '@/types/user'

export interface AiNewsItem {
  id: number
  source: string
  title: string
  titleZh: string | null
  url: string
  summary: string | null
  summaryZh: string | null
  tag: string
  score: number
  publishedAt: string
  favorited: boolean
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

export const getAiNewsList = (params: {
  source?: string
  tag?: string
  keyword?: string
  sort?: string
  page?: number
  size?: number
}): Promise<Result<PageResult<AiNewsItem>>> => {
  return get('/ai-news/list', { params })
}

export const getAiNewsTags = (): Promise<Result<string[]>> => {
  return get('/ai-news/tags')
}

export const favoriteAiNews = (id: number): Promise<Result<void>> => {
  return post(`/ai-news/${id}/favorite`)
}

export const unfavoriteAiNews = (id: number): Promise<Result<void>> => {
  return del(`/ai-news/${id}/favorite`)
}

export const getAiNewsFavorites = (params: {
  page?: number
  size?: number
}): Promise<Result<PageResult<AiNewsItem>>> => {
  return get('/ai-news/favorites', { params })
}

export interface AiModelRankItem {
  rank: number | null
  modelName: string
  provider: string
  releaseDate: string
  evidence: string
  confidence: string
  inputPrice: string
  outputPrice: string
  score: number | null
}

export const getAiLeaderboard = (category: string): Promise<Result<AiModelRankItem[]>> => {
  return get('/ai-news/leaderboard', { params: { category } })
}

export interface AiZhizhiRankItem {
  category: string
  reportDate: string
  rank: number
  modelName: string
  extremeScore: number | null
  medianScore: number | null
  medianGap: string
  change: string
  avgTime: string
  token: string
  testCost: string
  price: string
  releaseDate: string
  think: number
  country: string
}

export const getZhizhiRank = (category: string, month?: string): Promise<Result<AiZhizhiRankItem[]>> => {
  return get('/ai-news/zhizhi-rank', { params: { category, month } })
}

export const getZhizhiMonths = (category: string): Promise<Result<string[]>> => {
  return get('/ai-news/zhizhi-months', { params: { category } })
}
