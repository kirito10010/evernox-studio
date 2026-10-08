/**
 * 日期/时间格式化。
 *
 * 为什么统一做 replace(' ', 'T') 归一化：后端返回的是 MySQL datetime
 * （形如 "2026-10-08 12:00:00"），带空格的写法不是标准 ISO 格式，
 * Chrome 容忍但 Safari 会解析成 Invalid Date，输出 "NaN-NaN-NaN NaN:NaN"。
 * 归一化后 Chrome 下输出与此前完全一致，Safari 下从乱码变为正确值。
 */

/** 把后端 datetime 字符串转成 Date；空/非法输入由调用方用 NaN 判断兜住 */
const toDate = (value: string): Date => new Date(value.replace(' ', 'T'))

const pad = (n: number): string => String(n).padStart(2, '0')

/**
 * `YYYY-MM-DD HH:mm`
 * @param fallback 入参为空或非法时返回的占位符
 */
export const formatDateTime = (value: string | null | undefined, fallback = '—'): string => {
  if (!value) return fallback
  const d = toDate(value)
  if (Number.isNaN(d.getTime())) return fallback
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
    d.getMinutes()
  )}`
}

/**
 * `YYYY-MM-DD`
 * @param fallback 入参为空或非法时返回的占位符
 */
export const formatDate = (value: string | null | undefined, fallback = ''): string => {
  if (!value) return fallback
  const d = toDate(value)
  if (Number.isNaN(d.getTime())) return fallback
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/**
 * `M/D`（首页「最近上传」用的紧凑格式）
 * @param fallback 入参为空或非法时返回的占位符
 */
export const formatDateShort = (value: string | null | undefined, fallback = ''): string => {
  if (!value) return fallback
  const d = toDate(value)
  if (Number.isNaN(d.getTime())) return fallback
  return `${d.getMonth() + 1}/${d.getDate()}`
}

/**
 * 截断到分钟：`2026-10-08T12:00:00` → `2026-10-08 12:00`。
 *
 * 不做时区换算（后端返回的就是本地时间字符串），因此直接字符串切片。
 * 需要 `-` 占位的地方在调用处写 `formatTime(v) || '-'`。
 */
export const formatTime = (value: string | null | undefined): string =>
  value ? value.replace('T', ' ').slice(0, 16) : ''
