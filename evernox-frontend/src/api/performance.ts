import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/api/image'
import type { Result } from '@/types/user'
import type {
  LatePayload,
  LateRecord,
  OvertimePayload,
  OvertimeRecord,
  PerformanceChartData,
  PerformanceProject,
  PerformanceProjectPayload,
  PerformanceRecord,
  PerformanceRecordPayload,
} from '@/types/performance'

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

// ==================== 生产项目配置 ====================

/** 新建生产项目 */
export const createProject = (data: PerformanceProjectPayload): Promise<Result<PerformanceProject>> => {
  return post('/performance/project', data)
}

/** 生产项目列表 */
export const listProjects = (): Promise<Result<PerformanceProject[]>> => {
  return get('/performance/project/list')
}

/** 更新生产项目 */
export const updateProject = (id: number, data: PerformanceProjectPayload): Promise<Result<PerformanceProject>> => {
  return put(`/performance/project/${id}`, data)
}

/** 删除生产项目（有绩效记录时后端会拒绝） */
export const deleteProject = (id: number): Promise<Result<void>> => {
  return del(`/performance/project/${id}`)
}

// ==================== 绩效记录 ====================

/** 新增绩效记录 */
export const createRecord = (data: PerformanceRecordPayload): Promise<Result<PerformanceRecord>> => {
  return post('/performance/record', data)
}

/** 编辑绩效记录 */
export const updateRecord = (id: number, data: PerformanceRecordPayload): Promise<Result<PerformanceRecord>> => {
  return put(`/performance/record/${id}`, data)
}

/** 删除绩效记录 */
export const deleteRecord = (id: number): Promise<Result<void>> => {
  return del(`/performance/record/${id}`)
}

/** 分页查询绩效记录 */
export const listRecords = (params: {
  page: number
  size: number
  projectId?: number | null
  processType?: number | null
  startDate?: string | null
  endDate?: string | null
}): Promise<Result<PageResult<PerformanceRecord>>> => {
  return get(`/performance/record/list${buildQuery({ ...params })}`)
}

/** 有记录的月份列表（YYYY-MM，倒序） */
export const getMonths = (): Promise<Result<string[]>> => {
  return get('/performance/record/months')
}

/** 绩效趋势（按日聚合绩效人天） */
export const getChart = (startDate: string, endDate: string): Promise<Result<PerformanceChartData>> => {
  return get(`/performance/record/chart${buildQuery({ startDate, endDate })}`)
}

// ==================== 加班记录 ====================

/** 新增加班记录 */
export const createOvertime = (data: OvertimePayload): Promise<Result<OvertimeRecord>> => {
  return post('/performance/overtime', data)
}

/** 编辑加班记录 */
export const updateOvertime = (id: number, data: OvertimePayload): Promise<Result<OvertimeRecord>> => {
  return put(`/performance/overtime/${id}`, data)
}

/** 删除加班记录 */
export const deleteOvertime = (id: number): Promise<Result<void>> => {
  return del(`/performance/overtime/${id}`)
}

/** 多选删除加班记录 */
export const batchDeleteOvertime = (ids: number[]): Promise<Result<void>> => {
  return post('/performance/overtime/batch-delete', { ids })
}

/** 分页查询加班记录 */
export const listOvertime = (params: {
  page: number
  size: number
  startDate?: string | null
  endDate?: string | null
}): Promise<Result<PageResult<OvertimeRecord>>> => {
  return get(`/performance/overtime/list${buildQuery({ ...params })}`)
}

// ==================== 迟到记录 ====================

/** 新增迟到记录 */
export const createLate = (data: LatePayload): Promise<Result<LateRecord>> => {
  return post('/performance/late', data)
}

/** 删除迟到记录 */
export const deleteLate = (id: number): Promise<Result<void>> => {
  return del(`/performance/late/${id}`)
}

/** 多选删除迟到记录 */
export const batchDeleteLate = (ids: number[]): Promise<Result<void>> => {
  return post('/performance/late/batch-delete', { ids })
}

/** 分页查询迟到记录 */
export const listLate = (params: {
  page: number
  size: number
  startDate?: string | null
  endDate?: string | null
}): Promise<Result<PageResult<LateRecord>>> => {
  return get(`/performance/late/list${buildQuery({ ...params })}`)
}
