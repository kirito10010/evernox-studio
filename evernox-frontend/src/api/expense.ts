import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/api/image'
import type { Result } from '@/types/user'
import type {
  ExpenseCategory,
  ExpenseChartData,
  ExpenseRecord,
  ExpenseRecordPayload,
} from '@/types/expense'

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

// ==================== 消费类型 ====================

/** 新建消费类型 */
export const createCategory = (data: { name: string }): Promise<Result<ExpenseCategory>> => {
  return post('/expense/category', data)
}

/** 我的消费类型列表 */
export const listCategories = (): Promise<Result<ExpenseCategory[]>> => {
  return get('/expense/category/list')
}

/** 重命名消费类型 */
export const updateCategory = (id: number, data: { name: string }): Promise<Result<ExpenseCategory>> => {
  return put(`/expense/category/${id}`, data)
}

/** 删除消费类型（有消费记录时后端会拒绝） */
export const deleteCategory = (id: number): Promise<Result<void>> => {
  return del(`/expense/category/${id}`)
}

// ==================== 消费记录 ====================

/** 新增消费 */
export const createRecord = (data: ExpenseRecordPayload): Promise<Result<ExpenseRecord>> => {
  return post('/expense/record', data)
}

/** 编辑消费 */
export const updateRecord = (id: number, data: ExpenseRecordPayload): Promise<Result<ExpenseRecord>> => {
  return put(`/expense/record/${id}`, data)
}

/** 删除消费 */
export const deleteRecord = (id: number): Promise<Result<void>> => {
  return del(`/expense/record/${id}`)
}

/** 分页查询消费记录 */
export const listRecords = (params: {
  page: number
  size: number
  categoryId?: number | null
  startDate?: string | null
  endDate?: string | null
  keyword?: string | null
}): Promise<Result<PageResult<ExpenseRecord>>> => {
  return get(`/expense/record/list${buildQuery({ ...params })}`)
}

/** 有记录的月份列表（YYYY-MM，倒序） */
export const getMonths = (): Promise<Result<string[]>> => {
  return get('/expense/record/months')
}

/** 消费趋势（按日聚合） */
export const getChart = (startDate: string, endDate: string): Promise<Result<ExpenseChartData>> => {
  return get(`/expense/record/chart${buildQuery({ startDate, endDate })}`)
}
