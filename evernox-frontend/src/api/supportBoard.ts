import { get, post, put, del } from '@/utils/request'
import type { Result } from '@/types/user'

export interface SupportPixelItem {
  x: number
  y: number
  color: string
  locked: number
}

export interface SupportBoardView {
  id: number
  name: string
  width: number
  height: number
  active: number
  pixels: SupportPixelItem[]
}

export interface SupportBoardItem {
  id: number
  name: string
  width: number
  height: number
  active: number
  createdAt: string
}

export interface SupportPixelChange {
  boardId: number
  x: number
  y: number
  color: string | null
  locked: number | null
}

export const getActiveSupportBoard = (): Promise<Result<SupportBoardView>> => {
  return get('/support-board/active')
}

export const drawSupportPixel = (
  x: number,
  y: number,
  color: string | null
): Promise<Result<void>> => {
  return post('/support-board/pixel', { x, y, color })
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
