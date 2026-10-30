import type { AxiosProgressEvent } from 'axios'
import { del, get, post, put } from '@/utils/request'
import { buildQuery } from '@/utils/query'
import type { PageResult } from '@/types/api'
import type { Result } from '@/types/user'
import type {
  ReleaseAsset,
  ReleaseDetail,
  ReleaseListParams,
  ReleasePayload,
  ReleaseProject,
  ReleaseProjectPayload,
  ReleaseSummary,
  UploadInit,
} from '@/types/release'

// ==================== 项目 ====================

export const getReleaseProjects = (keyword?: string): Promise<Result<ReleaseProject[]>> =>
  get(`/admin/release/project/list${buildQuery({ keyword })}`)

export const getReleaseProject = (id: number): Promise<Result<ReleaseProject>> =>
  get(`/admin/release/project/${id}`)

export const createReleaseProject = (
  data: ReleaseProjectPayload
): Promise<Result<ReleaseProject>> => post('/admin/release/project', data)

export const updateReleaseProject = (
  id: number,
  data: ReleaseProjectPayload
): Promise<Result<ReleaseProject>> => put(`/admin/release/project/${id}`, data)

export const deleteReleaseProject = (
  id: number,
  confirmSlug: string
): Promise<Result<void>> =>
  del(`/admin/release/project/${id}${buildQuery({ confirmSlug })}`)

// ==================== 版本 ====================

export const getReleases = (
  params: ReleaseListParams
): Promise<Result<PageResult<ReleaseSummary>>> =>
  get(`/admin/release/list${buildQuery({ ...params })}`)

export const getReleaseDetail = (id: number): Promise<Result<ReleaseDetail>> =>
  get(`/admin/release/${id}`)

export const createRelease = (data: ReleasePayload): Promise<Result<ReleaseDetail>> =>
  post('/admin/release', data)

export const updateRelease = (
  id: number,
  data: ReleasePayload
): Promise<Result<ReleaseDetail>> => put(`/admin/release/${id}`, data)

export const deleteRelease = (id: number): Promise<Result<void>> =>
  del(`/admin/release/${id}`)

export const publishRelease = (
  id: number,
  latest = true
): Promise<Result<ReleaseDetail>> =>
  post(`/admin/release/${id}/publish`, { latest })

export const setReleaseLatest = (
  id: number,
  latest: boolean
): Promise<Result<ReleaseDetail>> =>
  post(`/admin/release/${id}/latest`, { latest })

export const getReleaseLatestYml = (id: number): Promise<Result<string>> =>
  get(`/admin/release/${id}/latest-yml`)

// ==================== 分片上传 ====================

export const initReleaseUpload = (
  projectId: number,
  fileName: string,
  fileSize: number
): Promise<Result<UploadInit>> =>
  post('/admin/release/upload/init', { projectId, fileName, fileSize })

export const uploadReleaseChunk = (
  uploadId: string,
  index: number,
  chunk: Blob,
  onProgress?: (event: AxiosProgressEvent) => void
): Promise<Result<number>> => {
  const form = new FormData()
  form.append('chunk', chunk, `chunk-${index}`)
  return post(
    `/admin/release/upload/chunk?uploadId=${encodeURIComponent(uploadId)}&index=${index}`,
    form,
    {
      timeout: 120_000,
      // 必须显式声明 multipart：request.ts 默认 Content-Type 是 application/json，
      // Axios 会把 FormData 转成 JSON，后端收不到 chunk 分段。
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: onProgress,
    }
  )

}

export const completeReleaseUpload = (uploadId: string): Promise<Result<ReleaseAsset>> =>
  post('/admin/release/upload/complete', { uploadId }, { timeout: 120_000 })

export const cancelReleaseUpload = (uploadId: string): Promise<Result<void>> =>
  del(`/admin/release/upload/${uploadId}`)

export const deleteStagedReleaseAsset = (id: number): Promise<Result<void>> =>
  del(`/admin/release/asset/${id}`)
