import { get, post, put, del } from '@/utils/request'
import { buildQuery } from '@/utils/query'
import type { Result } from '@/types/user'
import type { PageResult } from '@/api/image'
import type { AdminMiaoListParams, MiaoImportResult, MiaoQuestion, MiaoQuestionRequest } from '@/types/miao'

export const getAdminMiaoList = (
  params: AdminMiaoListParams
): Promise<Result<PageResult<MiaoQuestion>>> => {
  return get(`/admin/miao/list${buildQuery({ ...params })}`)
}

export const createMiao = (data: MiaoQuestionRequest): Promise<Result<MiaoQuestion>> => {
  return post('/admin/miao', data)
}

export const updateMiao = (id: number, data: MiaoQuestionRequest): Promise<Result<MiaoQuestion>> => {
  return put(`/admin/miao/${id}`, data)
}

export const deleteMiao = (id: number): Promise<Result<void>> => {
  return del(`/admin/miao/${id}`)
}

export const batchDeleteMiao = (ids: number[]): Promise<Result<void>> => {
  return del(`/admin/miao/batch?ids=${ids.join(',')}`)
}

export const approveMiao = (id: number): Promise<Result<void>> => {
  return post(`/admin/miao/${id}/approve`)
}

export const rejectMiao = (id: number): Promise<Result<void>> => {
  return post(`/admin/miao/${id}/reject`)
}

/**
 * 导入题库文件夹：formData 里 manifest = questions.json，images = 文件夹内所有截图。
 *
 * 必须显式声明 multipart/form-data：request 实例默认是 application/json，
 * 否则 axios 会把 FormData 序列化成 JSON，后端收到空请求。
 */
export const importMiaoFolder = (
  formData: FormData,
  onProgress?: (percent: number) => void
): Promise<Result<MiaoImportResult>> => {
  return post('/admin/miao/import', formData, {
    timeout: 180000,
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return
      onProgress(Math.round((event.loaded / event.total) * 100))
    },
  })
}
