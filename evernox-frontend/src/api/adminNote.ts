import { get, post } from '@/utils/request'
import type { PageResult } from '@/api/image'
import type { Result } from '@/types/user'
import type { Note, NoteStats } from '@/types/note'

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

/** 笔记审批列表 */
export const getAdminNotes = (params: {
  page: number
  size: number
  status?: number | null
  keyword?: string
}): Promise<Result<PageResult<Note>>> => {
  return get(`/admin/note/list${buildQuery({ ...params })}`)
}

/** 笔记详情（含正文），审批前阅读 */
export const getAdminNoteById = (id: number): Promise<Result<Note>> => {
  return get(`/admin/note/${id}`)
}

/** 通过 */
export const approveNote = (id: number): Promise<Result<void>> => {
  return post(`/admin/note/${id}/approve`)
}

/** 驳回 */
export const rejectNote = (id: number, reason: string): Promise<Result<void>> => {
  return post(`/admin/note/${id}/reject`, { reason })
}

/** 撤下 */
export const offlineNote = (id: number): Promise<Result<void>> => {
  return post(`/admin/note/${id}/offline`)
}

/** 审批统计 */
export const getAdminNoteStats = (): Promise<Result<NoteStats>> => {
  return get('/admin/note/stats')
}
