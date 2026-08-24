import { get, post, put, del } from '@/utils/request'
import type { Result } from '@/types/user'
import type { PageResult } from '@/api/image'
import type { TopicCircle, TopicCircleRequest, TopicComment, TopicPost } from '@/types/topic'

const buildQuery = (params: Record<string, unknown>): string => {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    search.append(key, String(value))
  }
  const qs = search.toString()
  return qs ? `?${qs}` : ''
}

// ========== 帖子 ==========

export const getAdminTopicPosts = (params: {
  page: number
  size: number
  keyword?: string
}): Promise<Result<PageResult<TopicPost>>> => {
  return get(`/admin/topic/post/list${buildQuery(params)}`)
}

export const deleteAdminTopicPost = (id: number): Promise<Result<void>> => {
  return del(`/admin/topic/post/${id}`)
}

export const batchDeleteAdminTopicPosts = (ids: number[]): Promise<Result<void>> => {
  return del(`/admin/topic/post/batch?ids=${ids.join(',')}`)
}

// ========== 评论 ==========

export const getAdminTopicComments = (params: {
  page: number
  size: number
  keyword?: string
}): Promise<Result<PageResult<TopicComment>>> => {
  return get(`/admin/topic/comment/list${buildQuery(params)}`)
}

export const deleteAdminTopicComment = (id: number): Promise<Result<void>> => {
  return del(`/admin/topic/comment/${id}`)
}

export const batchDeleteAdminTopicComments = (ids: number[]): Promise<Result<void>> => {
  return del(`/admin/topic/comment/batch?ids=${ids.join(',')}`)
}

// ========== 圈子 ==========

export const getAdminTopicCircles = (params: {
  page: number
  size: number
  keyword?: string
}): Promise<Result<PageResult<TopicCircle>>> => {
  return get(`/admin/topic/circle/list${buildQuery(params)}`)
}

export const createAdminTopicCircle = (data: TopicCircleRequest): Promise<Result<TopicCircle>> => {
  return post('/admin/topic/circle', data)
}

export const updateAdminTopicCircle = (id: number, data: TopicCircleRequest): Promise<Result<TopicCircle>> => {
  return put(`/admin/topic/circle/${id}`, data)
}

export const deleteAdminTopicCircle = (id: number): Promise<Result<void>> => {
  return del(`/admin/topic/circle/${id}`)
}

export const batchDeleteAdminTopicCircles = (ids: number[]): Promise<Result<void>> => {
  return del(`/admin/topic/circle/batch?ids=${ids.join(',')}`)
}
