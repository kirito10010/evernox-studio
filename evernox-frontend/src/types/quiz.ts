export interface QuizQuestion {
  id: number
  question: string
  optionA: string
  optionB: string
  optionC: string
  optionD: string
  answer: string
  status: number
  score?: number | null
  /** 当前用户对无答案题目已投的选项 */
  myVote?: string | null
  createdAt: string
}


export interface QuizQuestionRequest {
  question: string
  optionA: string
  optionB: string
  optionC: string
  optionD: string
  answer: string
}

export interface QuizImportItem {
  question: string
  options: string[]
  answer: string
}

export interface QuizImportResult {
  imported: number
  updated: number
  skipped: number
}

export interface QuizVoteRequest {
  questionId: number
  answer: string
}

export interface QuizVoteResult {
  resolved: boolean
  answer?: string | null
  rewarded: boolean
  points: number
  votes: number
  message: string
}

export interface QuizPendingResult {
  total: number
  items: QuizQuestion[]
}



export const QuizStatusMap: Record<number, string> = {
  0: '待审批',
  1: '已通过',
  2: '已驳回',
}

export const QuizStatusType: Record<number, 'info' | 'success' | 'danger'> = {
  0: 'info',
  1: 'success',
  2: 'danger',
}

export interface AdminQuizListParams {
  page: number
  size: number
  status?: number | null
  keyword?: string
  startDate?: string
  endDate?: string
  sortField?: 'createdAt'
  sortOrder?: 'asc' | 'desc'
}
