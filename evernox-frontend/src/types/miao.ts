import type { QuizVoteRequest, QuizVoteResult } from '@/types/quiz'

export interface MiaoQuestion {
  id: number
  question: string
  optionA: string
  optionB: string
  optionC: string
  optionD: string
  answer: string
  /** 游戏内已排除的选项，前端需要划线且不可选 */
  removedOptions: string[]
  /** 题图文件名，配合 /api/miao/image/{image} 使用 */
  image?: string | null
  status: number
  score?: number | null
  /** 当前用户对无答案题目已投的选项 */
  myVote?: string | null
  createdAt: string
}

export interface MiaoQuestionRequest {
  question: string
  optionA: string
  optionB: string
  optionC: string
  optionD: string
  answer: string
  removedOptions?: string[]
  image?: string
}

export interface MiaoPendingResult {
  total: number
  items: MiaoQuestion[]
}

export interface MiaoImportResult {
  imported: number
  updated: number
  skipped: number
  images: number
  missingImages: number
}

export interface AdminMiaoListParams {
  page: number
  size: number
  status?: number | null
  keyword?: string
  startDate?: string
  endDate?: string
  sortField?: 'createdAt'
  sortOrder?: 'asc' | 'desc'
}

export type { QuizVoteRequest, QuizVoteResult }
