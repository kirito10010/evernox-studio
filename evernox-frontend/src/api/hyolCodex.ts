import { get, post } from '@/utils/request'
import type { Result } from '@/types/user'
import type { CodexDataset, CodexImportResult, CodexMeta } from '@/types/ninjaCodex'

export const CODEX_API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

/**
 * 数据集不走 request 实例：接口直接返回原始 JSON（不是 Result 包装），
 * 且 ETag / 缓存头需要浏览器自行处理。
 */
export const getCodexDataset = (): Promise<CodexDataset> => {
  return fetch(`${CODEX_API_BASE}/hyol/codex/dataset`, { cache: 'no-cache' }).then((res) => {
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json() as Promise<CodexDataset>
  })
}

/** 导入元信息；未导入时 data 为 null */
export const getCodexMeta = (): Promise<Result<CodexMeta | null>> => {
  return get('/hyol/codex/meta')
}

/**
 * 导入抓包文件夹。formData 的 files 字段放 http-*.jsonl；
 * 必须显式声明 multipart/form-data，否则 axios 会把它序列化成 JSON。
 */
export const importCodexFolder = (
  formData: FormData,
  onProgress?: (percent: number) => void
): Promise<Result<CodexImportResult>> => {
  return post('/hyol/codex/import', formData, {
    timeout: 300000,
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return
      onProgress(Math.round((event.loaded / event.total) * 100))
    },
  })
}

/** 数据集立绘 / 头像地址（后端 404 时前端再回退静态资源） */
export const codexImageUrl = (filename: string): string =>
  filename ? `${CODEX_API_BASE}/hyol/codex/image/${filename}` : ''
