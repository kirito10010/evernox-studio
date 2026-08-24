import { get, post, put, del } from '@/utils/request'
import request from '@/utils/request'
import type { Result } from '@/types/user'

// ========== 类型定义 ==========

export interface ImageResponse {
  id: number
  userId: number
  originalName: string
  fileSize: number
  mimeType: string
  width: number | null
  height: number | null
  iv: string
  visibility: number
  /** 用途: 0图床照片/1相册封面/2网站分享封面 */
  purpose: number
  createdAt: string
  uploaderName: string
}

export interface AlbumResponse {
  id: number
  userId: number
  name: string
  description: string | null
  coverImageId: number | null
  visibility: number
  createdAt: string
  updatedAt: string
  creatorName: string
  imageCount: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface ImageStats {
  myImages: number
  publicImages: number
}

export interface AlbumStats {
  myAlbums: number
}

export interface StorageStats {
  /** 数据盘总容量（字节） */
  diskTotal: number
  /** 数据盘可用容量（字节） */
  diskFree: number
  /** 数据盘已用容量（字节） */
  diskUsed: number
  /** 当前用户照片占用（字节，含其相册封面） */
  imagesUsed: number
  /** 全平台所有用户照片占用（字节），占用率的分母 */
  allImagesUsed: number
  /** 当前用户照片数量 */
  imagesCount: number
}

// ========== 图片 API ==========

/**
 * 上传图片
 * 上传原始文件，落盘编码由服务端完成
 * @param file 原始图片文件
 * @param metadata 上传元数据（文件名、可见性、用途等）
 */
export const uploadImage = (
  file: File,
  metadata: {
    originalName: string
    mimeType: string
    fileSize: number
    width?: number
    height?: number
    visibility: number
    albumId?: number
    /** 0图床照片(默认)/1相册封面/2网站分享封面，封面不会出现在图床列表里 */
    purpose?: number
  }
): Promise<Result<ImageResponse>> => {
  const formData = new FormData()
  formData.append('file', file, metadata.originalName)
  formData.append(
    'metadata',
    new Blob([JSON.stringify(metadata)], { type: 'application/json' })
  )
  return post('/image/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    // 5M 带宽下 8MB 原图可能超 10 秒默认超时，放宽到 5 分钟
    timeout: 300000,
  })
}

/** 获取当前用户图片列表 */
export const getMyImages = (
  page = 1,
  size = 20
): Promise<Result<PageResult<ImageResponse>>> => {
  return get(`/image/list?page=${page}&size=${size}`)
}

/** 获取公开图片列表 */
export const getPublicImages = (
  page = 1,
  size = 20
): Promise<Result<PageResult<ImageResponse>>> => {
  return get(`/image/public?page=${page}&size=${size}`)
}

/** 获取图片详情 */
export const getImageById = (id: number): Promise<Result<ImageResponse>> => {
  return get(`/image/${id}`)
}

/** 获取图片文件（服务端已解码，返回原始图片字节） */
export const getImageFile = async (id: number): Promise<ArrayBuffer> => {
  const response = await request.get(`/image/${id}/file`, {
    responseType: 'arraybuffer',
    // 走 fetch 而非 XHR：部分浏览器扩展会 hook XMLHttpRequest 并无条件读
    // responseText，遇到二进制响应必抛 InvalidStateError，刷满控制台
    adapter: 'fetch',
  })
  return response.data
}

/** 获取图片 Blob（MIME 取自响应头，可直接生成 ObjectURL） */
export const getImageBlob = async (id: number): Promise<Blob> => {
  const response = await request.get(`/image/${id}/file`, {
    responseType: 'blob',
    adapter: 'fetch',
  })
  return response.data as Blob
}

/** 获取缩略图 Blob（服务端解码，无缩略图时回退原图） */
export const getImageThumbnail = async (id: number): Promise<Blob> => {
  const response = await request.get(`/image/${id}/thumbnail`, {
    responseType: 'blob',
    adapter: 'fetch',
  })
  return response.data as Blob
}

/** 删除图片 */
export const deleteImage = (id: number): Promise<Result<void>> => {
  return del(`/image/${id}`)
}

/** 更新图片可见性 (0=私密, 1=公开) */
export const updateImageVisibility = (
  id: number,
  visibility: number
): Promise<Result<ImageResponse>> => {
  return put(`/image/${id}/visibility?visibility=${visibility}`)
}

/** 校正图片真实像素尺寸（仅所有者可改，仅影响前端占位比例） */
export const updateImageDimensions = (
  id: number,
  width: number,
  height: number
): Promise<Result<void>> => {
  return put(`/image/${id}/dimensions`, { width, height })
}


/** 获取图片所属相册ID列表 */
export const getImageAlbums = (
  imageId: number
): Promise<Result<number[]>> => {
  return get(`/image/${imageId}/albums`)
}

/** 获取图片统计 */
export const getImageStats = (): Promise<Result<ImageStats>> => {
  return get('/image/stats')
}

/** 获取存储空间统计（数据盘容量 + 照片占用） */
export const getStorageStats = (): Promise<Result<StorageStats>> => {
  return get('/image/storage')
}

// ========== 相册 API ==========

/** 创建相册 */
export const createAlbum = (data: {
  name: string
  description?: string
  coverImageId?: number
  visibility?: number
}): Promise<Result<AlbumResponse>> => {
  return post('/album', data)
}

/** 更新相册 */
export const updateAlbum = (
  id: number,
  data: {
    name: string
    description?: string
    coverImageId?: number
    visibility?: number
  }
): Promise<Result<AlbumResponse>> => {
  return put(`/album/${id}`, data)
}

/** 删除相册 */
export const deleteAlbum = (id: number): Promise<Result<void>> => {
  return del(`/album/${id}`)
}

/** 获取当前用户相册列表 */
export const getMyAlbums = (
  page = 1,
  size = 20
): Promise<Result<PageResult<AlbumResponse>>> => {
  return get(`/album/list?page=${page}&size=${size}`)
}

/** 获取公开相册列表 */
export const getPublicAlbums = (
  page = 1,
  size = 20
): Promise<Result<PageResult<AlbumResponse>>> => {
  return get(`/album/public?page=${page}&size=${size}`)
}

/** 获取相册详情 */
export const getAlbumById = (id: number): Promise<Result<AlbumResponse>> => {
  return get(`/album/${id}`)
}

/** 获取相册内图片列表 */
export const getAlbumImages = (
  albumId: number,
  page = 1,
  size = 20
): Promise<Result<PageResult<ImageResponse>>> => {
  return get(`/album/${albumId}/images?page=${page}&size=${size}`)
}

/** 向相册添加图片 */
export const addImageToAlbum = (
  albumId: number,
  imageId: number
): Promise<Result<void>> => {
  return post(`/album/${albumId}/images?imageId=${imageId}`)
}

/** 从相册移除图片 */
export const removeImageFromAlbum = (
  albumId: number,
  imageId: number
): Promise<Result<void>> => {
  return del(`/album/${albumId}/images/${imageId}`)
}

/** 获取相册统计 */
export const getAlbumStats = (): Promise<Result<AlbumStats>> => {
  return get('/album/stats')
}
