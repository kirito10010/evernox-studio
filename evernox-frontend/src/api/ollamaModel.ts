import { get, post } from '@/utils/request'
import type { Result } from '@/types/user'
import type { PageResult } from '@/types/api'

export type { PageResult }

/** Ollama 模型变体（尺寸/量化标签） */
export interface OllamaTag {
  id: number
  name: string
  shortName: string
  digest: string | null
  sizeText: string | null
  sizeGb: number | null
  contextText: string | null
  contextTokens: number | null
  inputs: string | null
  isLatest: number
  isMlx: number
  isAbliterated: number
  paramSize: string | null
  command: string
  sourceUpdatedText: string | null
}

/** Ollama 模型（详情接口会带 tags） */
export interface OllamaModel {
  id: number
  name: string
  namespace: string | null
  baseName: string
  url: string
  description: string | null
  vendor: string | null
  vendorLabel: string | null
  pulls: number
  pullsText: string | null
  tagCount: number
  capabilities: string | null
  isVision: number
  isTools: number
  isThinking: number
  isEmbedding: number
  isAbliterated: number
  sizes: string | null
  minSizeB: number | null
  maxSizeB: number | null
  defaultCommand: string | null
  sourceUpdatedAt: string | null
  syncedAt: string | null
  tags?: OllamaTag[]
}

export interface OllamaVendorOption {
  key: string
  label: string
  count: number
}

export interface OllamaFilterOptions {
  vendors: OllamaVendorOption[]
  sizes: string[]
  capabilities: string[]
}

export interface OllamaSyncStatus {
  running: boolean
  lastRunAt: string | null
  lastSuccess: boolean
  durationMs: number
  modelTotal: number
  tagTotal: number
  modelAdded: number
  modelUpdated: number
  modelRemoved: number
  tagAdded: number
  tagRemoved: number
  message: string | null
}

export interface OllamaModelQuery {
  keyword?: string
  vendor?: string
  /** 参数量筛选，如 7b / 27b */
  size?: string
  abliterated?: boolean
  capability?: string
  sort?: 'popular' | 'new' | 'size'
  page?: number
  pageSize?: number
}

export const getOllamaModels = (params: OllamaModelQuery): Promise<Result<PageResult<OllamaModel>>> => {
  return get('/ai-ollama/models', { params })
}

export const getOllamaModelDetail = (id: number): Promise<Result<OllamaModel>> => {
  return get(`/ai-ollama/models/${id}`)
}

export const getOllamaFilters = (): Promise<Result<OllamaFilterOptions>> => {
  return get('/ai-ollama/filters')
}

export const getOllamaSyncStatus = (): Promise<Result<OllamaSyncStatus>> => {
  return get('/ai-ollama/sync/status')
}

export const triggerOllamaSync = (): Promise<Result<void>> => {
  return post('/ai-ollama/sync')
}

/**
 * 按关键词从官网补全模型库（只增不删）。
 * 官网默认列表页只返回官方库、社区模型无法枚举，所以本地搜不到时用这条通道补进来。
 */
export const backfillOllamaModels = (keyword: string): Promise<Result<void>> => {
  return post('/ai-ollama/backfill', null, { params: { keyword } })
}
