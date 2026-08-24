import { get, post, put, del } from '@/utils/request'
import request from '@/utils/request'
import type { Result } from '@/types/user'
import type { PageResult } from '@/api/image'
import type {
  AnnouncementAdminListParams,
  AnnouncementRequest,
  AnnouncementResponse,
  AnnouncementTag,
  AnnouncementTagRequest,
  UnreadCount,
} from '@/types/announcement'

// ========== 管理员 - 标签 ==========

export const getAdminAnnouncementTags = (): Promise<Result<AnnouncementTag[]>> => {
  return get('/admin/announcement/tag/list')
}

export const createAnnouncementTag = (
  data: AnnouncementTagRequest
): Promise<Result<AnnouncementTag>> => {
  return post('/admin/announcement/tag', data)
}

export const updateAnnouncementTag = (
  id: number,
  data: AnnouncementTagRequest
): Promise<Result<AnnouncementTag>> => {
  return put(`/admin/announcement/tag/${id}`, data)
}

export const deleteAnnouncementTag = (id: number): Promise<Result<void>> => {
  return del(`/admin/announcement/tag/${id}`)
}

// ========== 管理员 - 公告 ==========

export const getAdminAnnouncementList = (
  params: AnnouncementAdminListParams
): Promise<Result<PageResult<AnnouncementResponse>>> => {
  const kw = params.keyword ? `&keyword=${encodeURIComponent(params.keyword)}` : ''
  return get(`/admin/announcement/list?page=${params.page}&size=${params.size}${kw}`)
}

export const createAnnouncement = (
  data: AnnouncementRequest
): Promise<Result<AnnouncementResponse>> => {
  return post('/admin/announcement', data)
}

export const updateAnnouncement = (
  id: number,
  data: AnnouncementRequest
): Promise<Result<AnnouncementResponse>> => {
  return put(`/admin/announcement/${id}`, data)
}

export const deleteAnnouncement = (id: number): Promise<Result<void>> => {
  return del(`/admin/announcement/${id}`)
}

export const batchDeleteAnnouncements = (ids: number[]): Promise<Result<void>> => {
  return del(`/admin/announcement/batch?ids=${ids.join(',')}`)
}

// ========== 用户 ==========

export const getAnnouncementList = (): Promise<Result<AnnouncementResponse[]>> => {
  return get('/announcement/list')
}

export const getUnreadCount = (): Promise<Result<UnreadCount>> => {
  return get('/announcement/unread-count')
}

export const getAnnouncementDetail = (id: number): Promise<Result<AnnouncementResponse>> => {
  return get(`/announcement/${id}`)
}

export const markAnnouncementRead = (id: number): Promise<Result<void>> => {
  return post(`/announcement/${id}/read`)
}

/** 获取公告插图 Blob（走公告专用通道，任何登录用户可访问） */
export const getAnnouncementImageBlob = async (id: number): Promise<Blob> => {
  const response = await request.get(`/announcement/image/${id}/file`, {
    responseType: 'blob',
    adapter: 'fetch',
  })
  return response.data as Blob
}
