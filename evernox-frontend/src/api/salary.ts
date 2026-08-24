import { get, post, put, del } from '@/utils/request'
import type { Result } from '@/types/user'
import type { SalaryConfig, SalaryConfigPayload, SalaryRecord, SalaryRecordPayload } from '@/types/salary'

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

// ==================== 工资配置 ====================

/** 获取当前用户工资配置 */
export const getConfig = (): Promise<Result<SalaryConfig>> => {
  return get('/salary/config')
}

/** 更新工资配置 */
export const updateConfig = (data: SalaryConfigPayload): Promise<Result<SalaryConfig>> => {
  return put('/salary/config', data)
}

// ==================== 工资记录 ====================

/** 计算工资预览（不落库） */
export const preview = (month: string, attendanceDays?: number | null): Promise<Result<SalaryRecord>> => {
  return get(`/salary/preview${buildQuery({ month, attendanceDays })}`)
}

/** 保存工资记录 */
export const createRecord = (data: SalaryRecordPayload): Promise<Result<SalaryRecord>> => {
  return post('/salary/record', data)
}

/** 工资记录列表 */
export const listRecords = (): Promise<Result<SalaryRecord[]>> => {
  return get('/salary/record/list')
}

/** 删除工资记录 */
export const deleteRecord = (id: number): Promise<Result<void>> => {
  return del(`/salary/record/${id}`)
}
