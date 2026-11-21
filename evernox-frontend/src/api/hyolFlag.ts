import { get, post, del } from '@/utils/request'
import type { Result } from '@/types/user'
import type { FlagImportResult, FlagItem, FlagPeriod } from '@/types/hyolFlag'

/** 期列表（最新在前） */
export const getFlagPeriods = (): Promise<Result<FlagPeriod[]>> => {
  return get('/hyol/flag/periods')
}

/** 某期全部商品 */
export const getFlagItems = (periodId: number): Promise<Result<FlagItem[]>> => {
  return get(`/hyol/flag/items?periodId=${periodId}`)
}

/**
 * 整期导入：formData 里 manifest = 彩旗献礼.json，images = images 文件夹下的全部图标。
 *
 * 必须显式声明 multipart/form-data：request 实例默认是 application/json，
 * 否则 axios 会把 FormData 序列化成 JSON，后端收到空请求。
 */
export const importFlagFolder = (
  formData: FormData,
  onProgress?: (percent: number) => void
): Promise<Result<FlagImportResult>> => {
  return post('/hyol/flag/import', formData, {
    timeout: 180000,
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return
      onProgress(Math.round((event.loaded / event.total) * 100))
    },
  })
}

/** 删除整期（含全部商品），仅管理员 */
export const deleteFlagPeriod = (periodId: number): Promise<Result<void>> => {
  return del(`/hyol/flag/period/${periodId}`)
}
