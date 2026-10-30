/** 版本发布中心类型 */

export type ReleaseStatus = 0 | 1

export interface ReleaseAsset {
  id: number
  fileName: string
  size: number
  sha512: string
  downloadCount: number
  createdAt: string
}

export interface ReleaseProject {
  id: number
  name: string
  slug: string
  description?: string | null
  sort: number
  releaseCount: number
  publishedCount: number
  latestTag?: string | null
  latestPublishedAt?: string | null
  assetCount: number
  storageBytes: number
  downloadTotal: number
  createdAt: string
  updatedAt: string
}

export interface ReleaseSummary {
  id: number
  projectId: number
  tag: string
  title: string
  summary?: string | null
  status: ReleaseStatus
  latest: boolean
  publishedAt?: string | null
  createdAt: string
  updatedAt: string
  downloadCount: number
  assetCount: number
  totalSize: number
}


export interface ReleaseDetail extends ReleaseSummary {
  notes?: string | null
  assets: ReleaseAsset[]
}

export interface ReleaseProjectPayload {
  name: string
  slug: string
  description?: string
  sort?: number
}

export interface ReleasePayload {
  projectId?: number
  tag: string
  title: string
  notes?: string
  assetIds: number[]
}

export interface ReleaseListParams {
  projectId: number
  page: number
  size: number
  keyword?: string
  status?: ReleaseStatus
  latest?: boolean
  sort?: 'latest' | 'published' | 'created' | 'tag'
}


export interface UploadInit {
  uploadId: string
  chunkSize: number
  totalChunks: number
}
