/** 消费类型 */
export interface ExpenseCategory {
  id: number
  name: string
  createdAt: string
}

/** 消费记录 */
export interface ExpenseRecord {
  id: number
  categoryId: number
  categoryName: string | null
  amount: number
  remark: string | null
  /** yyyy-MM-dd */
  expenseDate: string
  createdAt: string
  updatedAt: string
}

/** 消费记录创建/更新载荷 */
export interface ExpenseRecordPayload {
  categoryId: number
  amount: number
  expenseDate: string
  remark: string | null
}

/** 图表某日的类型明细 */
export interface ExpenseChartBreakdown {
  categoryId: number
  categoryName: string | null
  amount: number
}

/** 图表某日数据点 */
export interface ExpenseChartPoint {
  date: string
  total: number
  breakdown: ExpenseChartBreakdown[]
}

export interface ExpenseChartData {
  points: ExpenseChartPoint[]
  totalAmount: number
  count: number
}
