import { get, post, put, del } from '@/utils/request'
import type { Result } from '@/types/user'
import type { QuizVoteRequest, QuizVoteResult } from '@/types/quiz'
import type { MiaoPendingResult, MiaoQuestion, MiaoQuestionRequest } from '@/types/miao'

/** 模糊搜索已通过题目 */
export const searchMiao = (keyword: string): Promise<Result<MiaoQuestion[]>> => {
  return get(`/miao/search?keyword=${encodeURIComponent(keyword)}`)
}

/** 待确认题目面板：总数量 + 当前用户还能作答的题目 */
export const getUnansweredMiao = (limit = 2): Promise<Result<MiaoPendingResult>> => {
  return get(`/miao/unanswered?limit=${limit}`)
}

/** 对无答案题目投票 */
export const voteMiao = (data: QuizVoteRequest): Promise<Result<QuizVoteResult>> => {
  return post('/miao/vote', data)
}

/** 用户提交题目（待审批） */
export const submitMiao = (data: MiaoQuestionRequest): Promise<Result<MiaoQuestion>> => {
  return post('/miao/submit', data)
}

/** 我的提交列表 */
export const getMyMiaoSubmissions = (): Promise<Result<MiaoQuestion[]>> => {
  return get('/miao/my-submissions')
}

/** 修改我的提交（保存后重新进入待审批） */
export const updateMyMiaoSubmission = (id: number, data: MiaoQuestionRequest): Promise<Result<MiaoQuestion>> => {
  return put(`/miao/my-submission/${id}`, data)
}

/** 删除我的提交 */
export const deleteMyMiaoSubmission = (id: number): Promise<Result<void>> => {
  return del(`/miao/my-submission/${id}`)
}

/** 被驳回的题目重新提交 */
export const resubmitMiao = (id: number): Promise<Result<void>> => {
  return post(`/miao/my-submission/${id}/resubmit`)
}
