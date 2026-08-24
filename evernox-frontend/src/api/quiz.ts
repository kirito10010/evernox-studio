import { get, post, put, del } from '@/utils/request'
import type { Result } from '@/types/user'
import type { QuizQuestion, QuizQuestionRequest } from '@/types/quiz'

/** 模糊搜索已通过题目 */
export const searchQuiz = (keyword: string): Promise<Result<QuizQuestion[]>> => {
  return get(`/quiz/search?keyword=${encodeURIComponent(keyword)}`)
}

/** 用户提交题目（待审批） */
export const submitQuiz = (data: QuizQuestionRequest): Promise<Result<QuizQuestion>> => {
  return post('/quiz/submit', data)
}

/** 我的提交列表 */
export const getMySubmissions = (): Promise<Result<QuizQuestion[]>> => {
  return get('/quiz/my-submissions')
}

/** 修改我的提交（保存后重新进入待审批） */
export const updateMySubmission = (id: number, data: QuizQuestionRequest): Promise<Result<QuizQuestion>> => {
  return put(`/quiz/my-submission/${id}`, data)
}

/** 删除我的提交 */
export const deleteMySubmission = (id: number): Promise<Result<void>> => {
  return del(`/quiz/my-submission/${id}`)
}

/** 被驳回的题目重新提交 */
export const resubmitQuiz = (id: number): Promise<Result<void>> => {
  return post(`/quiz/my-submission/${id}/resubmit`)
}
