/** 版本发布中心：公开地址与校验工具 */

const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '')

export const SLUG_PATTERN = /^[a-z0-9][a-z0-9-]{1,39}$/
export const TAG_PATTERN = /^[vV]?\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/

export const RESERVED_SLUGS = new Set([
  'latest',
  'latest-yml',
  'latest.yml',
  'public',
  'admin',
  'api',
  'assets',
  'release',
])

const INVALID_FILE_CHARS = /[<>:"/\\|?*\u0000-\u001f]/
const WINDOWS_RESERVED = /^(CON|PRN|AUX|NUL|CLOCK\$|COM[1-9]|LPT[1-9])$/i

/** `<BASE>` 的相对地址，如 /api/release/public/naruto-launcher */
export const releaseBaseUrl = (slug: string): string => `${API_BASE}/release/public/${slug}`

/** `<BASE>/latest.yml` */
export const releaseLatestUrl = (slug: string): string => `${releaseBaseUrl(slug)}/latest.yml`

/** `<BASE>/<fileName>` */
export const releaseAssetUrl = (slug: string, fileName: string): string =>
  `${releaseBaseUrl(slug)}/${encodeURIComponent(fileName)}`

/** 把相对地址转成可复制的绝对地址 */
export const releaseAbsoluteUrl = (path: string): string =>
  new URL(path, window.location.origin).toString()

/** tag 是否合法（semver，可带 v 前缀） */
export const isValidTag = (tag: string): boolean => TAG_PATTERN.test(tag.trim())

/** slug 是否合法 */
export const isValidSlug = (slug: string): boolean =>
  SLUG_PATTERN.test(slug.trim()) && !RESERVED_SLUGS.has(slug.trim())

/**
 * 文件名前端校验。
 * 返回错误文案；返回 null 表示通过。
 */
export const validateFileName = (fileName: string): string | null => {
  const name = fileName.trim()
  if (!name) return '文件名不能为空'
  if (name.includes('/') || name.includes('\\')) return '文件名不能包含路径分隔符'
  if (name.length > 180) return '文件名不能超过 180 个字符'
  if (INVALID_FILE_CHARS.test(name)) return '文件名包含 Windows 不允许的字符'
  if (name.endsWith('.') || name.endsWith(' ')) return '文件名不能以空格或点结尾'
  if (name.toLowerCase() === 'latest.yml' || name.toLowerCase() === 'latest.yaml') {
    return 'latest.yml 由系统自动生成，不能手动上传'
  }
  const stem = name.split('.')[0]
  if (WINDOWS_RESERVED.test(stem)) return '文件名使用了 Windows 保留名'
  return null
}

/** sha512 在列表里只显示首尾，完整值在详情/复制里 */
export const shortSha512 = (value: string, size = 10): string => {
  if (!value) return '—'
  if (value.length <= size * 2 + 3) return value
  return `${value.slice(0, size)}…${value.slice(-size)}`
}
