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
 */
export function currentPeriodLabel(): string {
  const now = new Date()
  const base = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  if (now.getDate() >= 26) {
    base.setMonth(base.getMonth() + 1)
  }
  return `${base.getFullYear()}-${String(base.getMonth() + 1).padStart(2, '0')}`
}
