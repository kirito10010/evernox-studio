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
  /** 分类：official / third_party / community */
  category: string | null
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
  /** 数据最后一次更新时间（获取/更新作业写入） */
  lastUpdatedAt: string | null
  tags?: OllamaTag[]
}

export interface OllamaVendorOption {
  key: string
  label: string
  count: number
}

export interface OllamaFilterOptions {
  categories: OllamaVendorOption[]
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
  /** 分类：official / third_party / community */
  category?: string
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

// ===== 获取新模型 / 更新模型（后台作业） =====

export interface OllamaUpdateRequest {
  category?: string
  vendor?: string
  size?: string
  capability?: string
  abliterated?: boolean
  limit?: number
  /** 筛选后手动勾选的模型；非空时忽略筛选与 limit */
  ids?: number[]
}

/** 「更新模型」候选列表项 */
export interface OllamaUpdateCandidate {
  id: number
  name: string
  category: string | null
  vendorLabel: string | null
  pullsText: string | null
  tagCount: number
  lastUpdatedAt: string | null
}

export interface OllamaCandidateQuery {
  keyword?: string
  category?: string
  vendor?: string
  size?: string
  abliterated?: boolean
  capability?: string
  limit?: number
}

export interface OllamaDiscoveredModel {
  id: number
  name: string
  pullsText: string | null
  tagCount: number
}

export interface OllamaDiscoverResult {
  added: number
  variants: number
  scanned: number
  keywords: string[]
  complete: boolean
  models: OllamaDiscoveredModel[]
}

export interface OllamaUpdatedModel {
  id: number
  name: string
  /** UPDATED / UNCHANGED / DELETED / FAILED */
  status: string
  pullsBefore: number | null
  pullsAfter: number | null
  pullsBeforeText: string | null
  pullsAfterText: string | null
  tagsBefore: number
  tagsAfter: number
  addedTags: string[] | null
  removedTags: string[] | null
  message: string | null
}

export interface OllamaUpdateResult {
  requested: number
  updated: number
  unchanged: number
  deleted: number
  failed: number
  items: OllamaUpdatedModel[]
}

export interface OllamaTaskStatus {
  /** DISCOVER / UPDATE；null 表示还没跑过 */
  type: 'DISCOVER' | 'UPDATE' | null
  running: boolean
  total: number
  done: number
  phase: string | null
  startedAt: string | null
  finishedAt: string | null
  message: string | null
  discover: OllamaDiscoverResult | null
  update: OllamaUpdateResult | null
}

/** 获取新模型：只入库库里没有的模型并抓取它们的变体 */
export const startOllamaDiscover = (keywords: string[]): Promise<Result<OllamaTaskStatus>> => {
  return post('/ai-ollama/discover', { keywords })
}

/** 更新模型：按范围或「最久未更新的 N 条」更新下载量与变体 */
export const startOllamaUpdate = (data: OllamaUpdateRequest): Promise<Result<OllamaTaskStatus>> => {
  return post('/ai-ollama/update', data)
}

export const getOllamaTaskStatus = (): Promise<Result<OllamaTaskStatus>> => {
  return get('/ai-ollama/task/status')
}

/** 「更新模型」候选列表（筛选后勾选要更新的模型） */
export const getOllamaUpdateCandidates = (
  params: OllamaCandidateQuery
): Promise<Result<OllamaUpdateCandidate[]>> => {
  return get('/ai-ollama/update/candidates', { params: { limit: 2000, ...params } })
}
