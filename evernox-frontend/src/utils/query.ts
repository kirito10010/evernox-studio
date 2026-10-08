/**
 * 拼接查询串，跳过 undefined / null / 空串。
 *
 * 全站 api 层共用；原先 13 个文件各自复制了一份完全相同的实现。
 * 注意：值为 0 或 false 不会被跳过（只跳过 undefined/null/''），
 * 因为 0 是合法的分页页码、状态码等参数。
 */
export const buildQuery = (params: Record<string, unknown>): string => {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    search.append(key, String(value))
  }
  const qs = search.toString()
  return qs ? `?${qs}` : ''
}
