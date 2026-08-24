import request, { get, post, put, del } from '@/utils/request'
import type { AlbumResponse, ImageResponse, PageResult } from '@/api/image'
import type { Result } from '@/types/user'

export interface AdminImageListParams {
  page: number
  size: number
  userId?: number | null
  visibility?: number | null
  keyword?: string
  albumId?: number | null
  startDate?: string
  endDate?: string
  sortField?: 'createdAt' | 'fileSize' | 'originalName'
  sortOrder?: 'asc' | 'desc'
}

export interface AdminAlbumListParams {
  page: number
  size: number
  userId?: number | null
  visibility?: number | null
  keyword?: string
  startDate?: string
  endDate?: string
  sortField?: 'createdAt' | 'updatedAt' | 'name'
  sortOrder?: 'asc' | 'desc'
}

export interface AdminAssetStats {
  totalImages: number
  privateImages: number
  totalAlbums: number
  privateAlbums: number
  imagesUsedBytes: number
}

export interface UserOption {
  id: number
  username: string
}

export interface AlbumUpdateRequest {
  name: string
  description?: string | null
  coverImageId?: number | null
  visibility: number
}

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

/** 筛选下拉用的用户清单 */
export const getUserOptions = (): Promise<Result<UserOption[]>> => {
  return get('/admin/user/options')
}

/** 全平台资产统计 */
export const getAdminAssetStats = (): Promise<Result<AdminAssetStats>> => {
  return get('/admin/asset/stats')
}

// ==================== 图片 ====================

export const getAdminImages = (
  params: AdminImageListParams
): Promise<Result<PageResult<ImageResponse>>> => {
  return get(`/admin/asset/image/list${buildQuery({ ...params })}`)
}

/**
 * 管理员通道取图片字节：可读取他人私密图，服务端已解码
 * （不能复用 `getImageBlob`，那条通道对他人私密图返回 403）
 */
export const getAdminImageBlob = async (id: number): Promise<Blob> => {
  const response = await request.get(`/admin/asset/image/${id}/file`, {
    responseType: 'blob',
    // 同 api/image.ts：走 fetch 适配器，避开扩展 hook XHR 读 responseText 的报错
    adapter: 'fetch',
  })
  return response.data as Blob
}

export const getAdminImageAlbums = (id: number): Promise<Result<number[]>> => {
  return get(`/admin/asset/image/${id}/albums`)
}

export const updateAdminImageVisibility = (
  id: number,
  visibility: number
): Promise<Result<ImageResponse>> => {
  return put(`/admin/asset/image/${id}/visibility?visibility=${visibility}`)
}

export const updateAdminImageVisibilityBatch = (
  ids: number[],
  visibility: number
): Promise<Result<void>> => {
  return put('/admin/asset/image/visibility/batch', { ids, visibility })
}

export const deleteAdminImage = (id: number): Promise<Result<void>> => {
  return del(`/admin/asset/image/${id}`)
}

export const deleteAdminImages = (ids: number[]): Promise<Result<void>> => {
  return del(`/admin/asset/image/batch?ids=${ids.join(',')}`)
}

// ==================== 相册 ====================

export const getAdminAlbums = (
  params: AdminAlbumListParams
): Promise<Result<PageResult<AlbumResponse>>> => {
  return get(`/admin/asset/album/list${buildQuery({ ...params })}`)
}

export const getAdminAlbumImages = (
  id: number,
  page: number,
  size: number
): Promise<Result<PageResult<ImageResponse>>> => {
  return get(`/admin/asset/album/${id}/images?page=${page}&size=${size}`)
}

export const updateAdminAlbum = (
  id: number,
  data: AlbumUpdateRequest
): Promise<Result<AlbumResponse>> => {
  return put(`/admin/asset/album/${id}`, data)
}

export const addAdminAlbumImages = (id: number, imageIds: number[]): Promise<Result<void>> => {
  return post(`/admin/asset/album/${id}/images`, { imageIds })
}

export const removeAdminAlbumImage = (id: number, imageId: number): Promise<Result<void>> => {
  return del(`/admin/asset/album/${id}/images/${imageId}`)
}

export const deleteAdminAlbum = (id: number): Promise<Result<void>> => {
  return del(`/admin/asset/album/${id}`)
}

export const deleteAdminAlbums = (ids: number[]): Promise<Result<void>> => {
  return del(`/admin/asset/album/batch?ids=${ids.join(',')}`)
}
