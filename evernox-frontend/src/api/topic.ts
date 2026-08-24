import { get, post, put, del } from '@/utils/request'
import request from '@/utils/request'
import type { Result } from '@/types/user'
import type { PageResult } from '@/api/image'
import type {
  TopicCircle,
  TopicCircleListParams,
  TopicCircleRequest,
  TopicComment,
  TopicCommentRequest,
  TopicInteraction,
  TopicMember,
  TopicPost,
  TopicPostListParams,
  TopicPostRequest,
  TopicRank,
} from '@/types/topic'

// ========== 圈子 ==========

export const createTopicCircle = (data: TopicCircleRequest): Promise<Result<TopicCircle>> => {
  return post('/topic/circle', data)
}

export const getTopicCircles = (params: TopicCircleListParams): Promise<Result<PageResult<TopicCircle>>> => {
  const kw = params.keyword ? `&keyword=${encodeURIComponent(params.keyword)}` : ''
  return get(`/topic/circle/list?page=${params.page}&size=${params.size}&mine=${params.mine ?? false}${kw}`)
}

export const getTopicCircleDetail = (id: number): Promise<Result<TopicCircle>> => {
  return get(`/topic/circle/${id}`)
}

export const updateTopicCircle = (id: number, data: TopicCircleRequest): Promise<Result<TopicCircle>> => {
  return put(`/topic/circle/${id}`, data)
}

export const deleteTopicCircle = (id: number): Promise<Result<void>> => {
  return del(`/topic/circle/${id}`)
}

export const followCircle = (id: number): Promise<Result<void>> => {
  return post(`/topic/circle/${id}/follow`)
}

export const unfollowCircle = (id: number): Promise<Result<void>> => {
  return del(`/topic/circle/${id}/follow`)
}

export const transferCircle = (id: number, userId: number): Promise<Result<void>> => {
  return post(`/topic/circle/${id}/transfer?userId=${userId}`)
}

export const getCircleMembers = (id: number): Promise<Result<TopicMember[]>> => {
  return get(`/topic/circle/${id}/members`)
}

export const getTopicRank = (): Promise<Result<TopicRank>> => {
  return get('/topic/rank')
}

// ========== 帖子 ==========

export const createTopicPost = (data: TopicPostRequest): Promise<Result<TopicPost>> => {
  return post('/topic/post', data)
}

export const getSquarePosts = (params: TopicPostListParams): Promise<Result<PageResult<TopicPost>>> => {
  return get(`/topic/post/list?page=${params.page}&size=${params.size}&sort=${params.sort ?? 'hot'}`)
}

export const getCirclePosts = (
  circleId: number,
  page: number,
  size: number
): Promise<Result<PageResult<TopicPost>>> => {
  return get(`/topic/post/circle/${circleId}?page=${page}&size=${size}`)
}

export const getMyPosts = (page: number, size: number): Promise<Result<PageResult<TopicPost>>> => {
  return get(`/topic/post/mine?page=${page}&size=${size}`)
}

export const getMyFavorites = (page: number, size: number): Promise<Result<PageResult<TopicPost>>> => {
  return get(`/topic/post/favorites?page=${page}&size=${size}`)
}

export const getTopicPostDetail = (id: number): Promise<Result<TopicPost>> => {
  return get(`/topic/post/${id}`)
}

export const updateTopicPost = (id: number, data: TopicPostRequest): Promise<Result<TopicPost>> => {
  return put(`/topic/post/${id}`, data)
}

export const deleteTopicPost = (id: number): Promise<Result<void>> => {
  return del(`/topic/post/${id}`)
}

export const likePost = (id: number): Promise<Result<TopicInteraction>> => {
  return post(`/topic/post/${id}/like`)
}

export const favoritePost = (id: number): Promise<Result<TopicInteraction>> => {
  return post(`/topic/post/${id}/favorite`)
}

// ========== 评论 ==========

export const getComments = (
  postId: number,
  page: number,
  size: number
): Promise<Result<PageResult<TopicComment>>> => {
  return get(`/topic/comment/list?postId=${postId}&page=${page}&size=${size}`)
}

export const createComment = (data: TopicCommentRequest): Promise<Result<TopicComment>> => {
  return post('/topic/comment', data)
}

export const deleteComment = (id: number): Promise<Result<void>> => {
  return del(`/topic/comment/${id}`)
}

// ========== 图片 ==========

/** 获取话题帖子图片 Blob（走话题专用通道，任何登录用户可访问） */
export const getTopicImageBlob = async (id: number): Promise<Blob> => {
  const response = await request.get(`/topic/image/${id}/file`, {
    responseType: 'blob',
    adapter: 'fetch',
  })
  return response.data as Blob
}
