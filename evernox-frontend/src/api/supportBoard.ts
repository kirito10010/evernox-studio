import request, { get, post, put, del } from '@/utils/request'
import type { Result } from '@/types/user'

export interface SupportBoardView {
  id: number
  name: string
  width: number
  height: number
  active: number
  /** 扁平数组：[x, y, colorInt, locked, ...] */
  pixels: number[]
}

export interface SupportPixelChange {
  x: number
  y: number
  color: string | null
  locked: number | null
}

export interface SupportPixelChangeEvent {
  boardId: number
  pixels: SupportPixelChange[]
}

export interface SupportBoardItem {
  id: number
  name: string
  width: number
  height: number
  active: number
  createdAt: string
}

export const getActiveSupportBoard = (): Promise<Result<SupportBoardView>> => {
  return get('/support-board/active')
}

/** 拉取画板紧凑二进制（8 字节头 + 每像素 [R,G,B,flags]），用于快速加载渲染 */
export const getActiveSupportBoardBinary = async (): Promise<ArrayBuffer> => {
  const res = await request.get('/support-board/active-binary', {
    responseType: 'arraybuffer',
    // 走 fetch 而非 XHR：部分浏览器扩展会 hook XMLHttpRequest 并无条件读
    // responseText，遇到二进制响应必抛 InvalidStateError，刷满控制台
    adapter: 'fetch',
  })
  return res.data as ArrayBuffer
}

export const drawSupportPixel = (
  x: number,
  y: number,
  color: string | null
): Promise<Result<void>> => {
  return post('/support-board/pixel', { x, y, color })
}

export const drawSupportPixels = (
  pixels: { x: number; y: number; color: string | null }[]
): Promise<Result<{ x: number; y: number }[]>> => {
  return post('/support-board/pixel-batch', pixels)
}

export const lockSupportPixel = (x: number, y: number): Promise<Result<void>> => {
  return post(`/admin/support-board/pixel/lock?x=${x}&y=${y}`)
}

export const unlockSupportPixel = (x: number, y: number): Promise<Result<void>> => {
  return post(`/admin/support-board/pixel/unlock?x=${x}&y=${y}`)
}

export const lockSupportPixels = (pixels: { x: number; y: number }[]): Promise<Result<void>> => {
  return post('/admin/support-board/pixel/lock-batch', pixels)
}

export const unlockSupportPixels = (pixels: { x: number; y: number }[]): Promise<Result<void>> => {
  return post('/admin/support-board/pixel/unlock-batch', pixels)
}

export const listSupportBoards = (): Promise<Result<SupportBoardItem[]>> => {
  return get('/admin/support-board')
}

export const createSupportBoard = (name: string): Promise<Result<SupportBoardItem>> => {
  return post('/admin/support-board', { name })
}

export const setSupportBoardActive = (id: number): Promise<Result<void>> => {
  return put(`/admin/support-board/${id}/active`)
}

export const deleteSupportBoard = (id: number): Promise<Result<void>> => {
  return del(`/admin/support-board/${id}`)
}
