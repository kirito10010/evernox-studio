/** 待办优先级 */
export const TodoPriority = {
  LOW: 0,
  MEDIUM: 1,
  HIGH: 2,
} as const

export const TodoPriorityMap: Record<number, string> = {
  0: '低',
  1: '中',
  2: '高',
}

export interface Todo {
  id: number
  content: string
  done: number
  priority: number
  /** yyyy-MM-dd，可空 */
  dueDate: string | null
  finishedAt: string | null
  createdAt: string
  updatedAt: string
  /** 是否逾期，由服务端按当天日期判定 */
  overdue: boolean
}

export interface TodoPayload {
  content: string
  priority: number
  dueDate: string | null
}

export interface TodoStats {
  pending: number | null
  dueToday: number | null
  overdue: number | null
  done: number | null
}

/** 截止范围筛选 */
export type TodoDueScope = 'today' | 'week' | 'overdue'
