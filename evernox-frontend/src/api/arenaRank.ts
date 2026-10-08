import request, { get, post } from '@/utils/request'
import type { Result } from '@/types/user'

export interface ArenaRankItem {
  id: number
  category: string
  rank: number | null
  rankSpreadMin: number | null
  rankSpreadMax: number | null
  modelName: string
  modelUrl: string | null
  org: string | null
  license: string | null
  score: number | null
  scoreCiPlus: number | null
  scoreCiMinus: number | null
  votes: number | null
  inputPrice: number | null
  outputPrice: number | null
  contextText: string | null
  contextTokens: number | null
  isPreliminary: number
  syncedAt: string | null
}

export interface ArenaCategoryOption {
  slug: string
  label: string
  labelEn: string
  group: string
  modelCount: number
}

export interface ArenaOrgOption {
  key: string
  label: string
  count: number
}

export interface ArenaFilterOptions {
  orgs: ArenaOrgOption[]
  minInput: number | null
  maxInput: number | null
  minOutput: number | null
  maxOutput: number | null
}

export interface ArenaSyncStatus {
  running: boolean
  phase: string | null
  currentCategory: string | null
  categoryTotal: number
  categoryDone: number
  rowTotal: number
  added: number
  updated: number
  removed: number
  lastRunAt: string | null
  lastSuccess: boolean
  durationMs: number
  message: string | null
}

export const getArenaCategories = (): Promise<Result<ArenaCategoryOption[]>> => {
  return get('/ai-arena/categories')
}

export const getArenaItems = (params: {
  category: string
  org?: string
  priceType?: 'input' | 'output'
  minPrice?: number
  maxPrice?: number
  keyword?: string
}): Promise<Result<ArenaRankItem[]>> => {
  return get('/ai-arena/items', { params })
}

export const getArenaFilters = (category: string): Promise<Result<ArenaFilterOptions>> => {
  return get('/ai-arena/filters', { params: { category } })
}

export const getArenaSyncStatus = (): Promise<Result<ArenaSyncStatus>> => {
  return get('/ai-arena/sync/status')
}

export const triggerArenaSync = (): Promise<Result<void>> => {
  return post('/ai-arena/sync')
}

/**
 * 导入本地抓取好的榜单数据包（zip）。
 * 服务器 IP 被官网风控拦截（403）时的替代通道：本地脚本下载页面，服务端解析入库。
 */
export const importArenaPackage = (file: File): Promise<Result<ArenaSyncStatus>> => {
  const formData = new FormData()
  formData.append('file', file)
  return post('/ai-arena/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    // 2MB 数据包在 5M 带宽下上传 + 服务端解析入库，放宽到 5 分钟
    timeout: 300000,
  })
}

/**
 * 下载「本地抓取」用的单文件脚本。
 *
 * 由后端按 AiArenaCrawler.CATEGORIES 动态生成，所以脚本里的分类列表不会和服务器漂移；
 * 脚本内不含任何密钥，抓完仍需在本页「导入数据包」上传 zip。
 */
export const downloadArenaFetcher = async (): Promise<Blob> => {
  const response = await request.get('/ai-arena/fetcher', {
    responseType: 'blob',
    // 同 api/image.ts：走 fetch 适配器，避开扩展 hook XHR 读 responseText 的报错
    adapter: 'fetch',
    timeout: 30000,
  })
  return response.data as Blob
}

