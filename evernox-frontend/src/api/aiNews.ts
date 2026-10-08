import { get, post, del } from '@/utils/request'
import type { Result } from '@/types/user'
import type { PageResult } from '@/types/api'

export type { PageResult }

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

