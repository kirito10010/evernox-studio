import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/api/image'
import type { Result } from '@/types/user'
import type { Note, NotePayload, NoteStats } from '@/types/note'

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

/** 新建笔记（后端强制落为私有） */
export const createNote = (data: NotePayload): Promise<Result<Note>> => {
  return post('/note', data)
}

/** 编辑笔记（仅私有/已驳回可改） */
export const updateNote = (id: number, data: NotePayload): Promise<Result<Note>> => {
  return put(`/note/${id}`, data)
}

/** 删除笔记（同时清理正文插图） */
export const deleteNote = (id: number): Promise<Result<void>> => {
  return del(`/note/${id}`)
}

/** 置顶开关 */
export const pinNote = (id: number, pinned: boolean): Promise<Result<Note>> => {
  return put(`/note/${id}/pin?pinned=${pinned}`)
}

/** 申请公开 */
export const submitNote = (id: number): Promise<Result<void>> => {
  return post(`/note/${id}/submit`)
}

/** 撤回申请 / 自行撤下 */
export const withdrawNote = (id: number): Promise<Result<void>> => {
  return post(`/note/${id}/withdraw`)
}

/** 我的笔记列表（不含正文） */
export const getMyNotes = (params: {
  page: number
  size: number
  keyword?: string
  status?: number | null
}): Promise<Result<PageResult<Note>>> => {
  return get(`/note/list${buildQuery({ ...params })}`)
}

/** 公开笔记列表 */
export const getPublicNotes = (params: {
  page: number
  size: number
  keyword?: string
}): Promise<Result<PageResult<Note>>> => {
  return get(`/note/public${buildQuery({ ...params })}`)
}

/** 笔记详情（含正文） */
export const getNoteById = (id: number): Promise<Result<Note>> => {
  return get(`/note/${id}`)
}

/** 我的笔记统计 */
export const getNoteStats = (): Promise<Result<NoteStats>> => {
  return get('/note/stats')
}
