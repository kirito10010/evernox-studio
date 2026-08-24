/** 笔记状态：与后端 NoteStatus 一致 */
export const NoteStatus = {
  PRIVATE: 0,
  PENDING: 1,
  PUBLIC: 2,
  REJECTED: 3,
} as const

export const NoteStatusMap: Record<number, string> = {
  0: '私有',
  1: '待审批',
  2: '已公开',
  3: '已驳回',
}

export interface Note {
  id: number
  userId: number
  title: string
  /** 列表接口不返回正文，只有详情才带 */
  content: string | null
  summary: string | null
  pinned: number
  status: number
  rejectReason: string | null
  submittedAt: string | null
  reviewedAt: string | null
  createdAt: string
  updatedAt: string
  ownerName: string | null
  reviewerName: string | null
}

export interface NotePayload {
  title: string
  content: string
}

export interface NoteStats {
  mine: number | null
  pending: number | null
  published: number | null
  rejected: number | null
}
