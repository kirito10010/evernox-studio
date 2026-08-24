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

export interface QuizImportResult {
  imported: number
  skipped: number
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
