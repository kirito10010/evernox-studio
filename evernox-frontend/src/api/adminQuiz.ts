import { get, post, put, del } from '@/utils/request'
import { buildQuery } from '@/utils/query'
import type { Result } from '@/types/user'
import type { PageResult } from '@/api/image'
import type { AdminQuizListParams, QuizImportResult, QuizQuestion, QuizQuestionRequest } from '@/types/quiz'

export const getAdminQuizList = (
  params: AdminQuizListParams
): Promise<Result<PageResult<QuizQuestion>>> => {
  return get(`/admin/quiz/list${buildQuery({ ...params })}`)
}

export const createQuiz = (data: QuizQuestionRequest): Promise<Result<QuizQuestion>> => {
  return post('/admin/quiz', data)
}

export const updateQuiz = (id: number, data: QuizQuestionRequest): Promise<Result<QuizQuestion>> => {
  return put(`/admin/quiz/${id}`, data)
}

export const deleteQuiz = (id: number): Promise<Result<void>> => {
  return del(`/admin/quiz/${id}`)
}

export const batchDeleteQuiz = (ids: number[]): Promise<Result<void>> => {
  return del(`/admin/quiz/batch?ids=${ids.join(',')}`)
}

export const approveQuiz = (id: number): Promise<Result<void>> => {
  return post(`/admin/quiz/${id}/approve`)
}

export const rejectQuiz = (id: number): Promise<Result<void>> => {
  return post(`/admin/quiz/${id}/reject`)
}

export const importQuizExcel = (file: File): Promise<Result<QuizImportResult>> => {
  const formData = new FormData()
  formData.append('file', file)
  return post('/admin/quiz/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}
