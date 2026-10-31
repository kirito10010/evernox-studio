/** 格式化为 yyyy-MM-dd */
function fmt(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/**
 * 绩效周期范围：label = '2026-06' → ['2026-05-26', '2026-06-25']
 * 即「上月26号 ~ 当月25号」
 */
export function periodRange(label: string): [string, string] {
  const [y, m] = label.split('-').map(Number)
  const start = new Date(y, m - 2, 26) // 上月26
  const end = new Date(y, m - 1, 25) // 本月25
  return [fmt(start), fmt(end)]
}

/**
 * 当前绩效周期 label（yyyy-MM）
 * 今天 day >= 26 → 下月；否则本月
 *
 * 注意：不能用 new Date(...).setMonth(getMonth() + 1) 来跨月，
 * 因为「10 月 31 日 → setMonth(11)」会被 JS 解释成 11 月 31 日，
 * 自动溢出到 12 月 1 日，导致默认周期变成下下个月（如 2026-12）。
 */
export function currentPeriodLabel(): string {
  const now = new Date()
  let year = now.getFullYear()
  // getMonth() 是 0-based，先按「月份序号」加减，避免日号参与运算
  let monthIndex = now.getMonth()
  if (now.getDate() >= 26) {
    monthIndex += 1
    if (monthIndex > 11) {
      monthIndex = 0
      year += 1
    }
  }
  return `${year}-${String(monthIndex + 1).padStart(2, '0')}`
}
