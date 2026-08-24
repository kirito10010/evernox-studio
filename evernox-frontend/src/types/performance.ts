/** 工序类型：0作业 / 1质检 */
export type ProcessType = 0 | 1

/** 生产项目配置 */
export interface PerformanceProject {
  id: number
  name: string
  workQuota: number
  inspectQuota: number
  createdAt: string
  updatedAt: string
}

/** 生产项目创建/更新载荷 */
export interface PerformanceProjectPayload {
  name: string
  workQuota: number
  inspectQuota: number
}

/** 绩效记录 */
export interface PerformanceRecord {
  id: number
  projectId: number
  projectName: string | null
  workDate: string
  processType: ProcessType
  quota: number
  actualWorkload: number
  performanceDays: number
  createdAt: string
  updatedAt: string
}

/** 绩效记录创建/更新载荷（定额与绩效人天由后端计算） */
export interface PerformanceRecordPayload {
  projectId: number
  workDate: string
  processType: ProcessType
  actualWorkload: number
}

/** 图表某日数据点 */
export interface PerformanceChartPoint {
  date: string
  total: number
}

export interface PerformanceChartData {
  points: PerformanceChartPoint[]
  /** 范围内净绩效总和（扣底量后） */
  totalDays: number
  /** 范围内上班天数（有记录的天数） */
  workDays: number
  count: number
}

/** 加班记录 */
export interface OvertimeRecord {
  id: number
  workDate: string
  overtimeHours: number
  overtimeDays: number
  createdAt: string
  updatedAt: string
}

export interface OvertimePayload {
  workDate: string
  overtimeHours: number
}

/** 迟到记录 */
export interface LateRecord {
  id: number
  workDate: string
  lateMinutes: number
  lateDays: number
  createdAt: string
  updatedAt: string
}

export interface LatePayload {
  workDate: string
  lateMinutes: number
}
