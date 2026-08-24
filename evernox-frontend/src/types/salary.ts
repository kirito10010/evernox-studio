/** 工资配置 */
export interface SalaryConfig {
  id: number
  userId: number
  baseSalary: number
  postPerformance: number
  mealAllowance: number
  housingAllowance: number
  fullAttendanceBonus: number
  otherBonus: number
  pension: number
  medicalInsurance: number
  unemploymentInsurance: number
  createdAt: string
  updatedAt: string
}

/** 工资配置创建/更新载荷 */
export interface SalaryConfigPayload {
  baseSalary: number
  postPerformance: number
  mealAllowance: number
  housingAllowance: number
  fullAttendanceBonus: number
  otherBonus: number
  pension: number
  medicalInsurance: number
  unemploymentInsurance: number
}

/** 工资记录（预览时 id 为 null） */
export interface SalaryRecord {
  id: number | null
  month: string
  startDate: string
  endDate: string
  attendanceDays: number
  actualAttendanceDays: number
  performanceDays: number
  performanceSalary: number
  overtimeDays: number
  overtimeSalary: number
  lateMinutes: number
  attendanceRatio: number
  baseSalary: number
  postPerformance: number
  mealAllowance: number
  housingAllowance: number
  fullAttendanceBonus: number
  otherBonus: number
  pension: number
  medicalInsurance: number
  unemploymentInsurance: number
  totalSalary: number
  createdAt?: string
  updatedAt?: string
}

/** 工资记录创建载荷 */
export interface SalaryRecordPayload {
  month: string
  attendanceDays: number
}
