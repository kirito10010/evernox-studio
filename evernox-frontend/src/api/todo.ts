import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/api/image'
import type { Result } from '@/types/user'
import type { Todo, TodoDueScope, TodoPayload, TodoStats } from '@/types/todo'

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

/** 新建待办 */
export const createTodo = (data: TodoPayload): Promise<Result<Todo>> => {
  return post('/todo', data)
}

/** 编辑待办 */
export const updateTodo = (id: number, data: TodoPayload): Promise<Result<Todo>> => {
  return put(`/todo/${id}`, data)
}

/** 切换完成态 */
export const setTodoDone = (id: number, done: boolean): Promise<Result<Todo>> => {
  return put(`/todo/${id}/done?done=${done}`)
}

/** 删除待办 */
export const deleteTodo = (id: number): Promise<Result<void>> => {
  return del(`/todo/${id}`)
}

/** 我的待办列表 */
export const getTodos = (params: {
  page: number
  size: number
  done?: number | null
  priority?: number | null
  dueScope?: TodoDueScope | null
}): Promise<Result<PageResult<Todo>>> => {
  return get(`/todo/list${buildQuery({ ...params })}`)
}

/** 我的待办统计 */
export const getTodoStats = (): Promise<Result<TodoStats>> => {
  return get('/todo/stats')
}
