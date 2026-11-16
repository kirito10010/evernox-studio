import { get, post, del } from '@/utils/request'
import type { Result } from '@/types/user'
import type { MallImportResult, MallItem, MallPeriod } from '@/types/hyolMall'

/** 期列表（最新在前），每期带 7 天逐日概览 */
export const getMallPeriods = (): Promise<Result<MallPeriod[]>> => {
  return get('/hyol/mall/periods')
}

/** 某期商品；day 为空返回整期 */
export const getMallItems = (periodId: number, day?: string): Promise<Result<MallItem[]>> => {
  const params = new URLSearchParams({ periodId: String(periodId) })
  if (day) params.set('day', day)
  return get(`/hyol/mall/items?${params.toString()}`)
}

/**
 * 导入某一天的数据：formData 里 manifest = 商城特惠.json，images = images 文件夹下的全部图标。
 *
 * 必须显式声明 multipart/form-data：request 实例默认是 application/json，
 * 否则 axios 会把 FormData 序列化成 JSON，后端收到空请求。
 */
export const importMallFolder = (
  formData: FormData,
  onProgress?: (percent: number) => void
): Promise<Result<MallImportResult>> => {
  return post('/hyol/mall/import', formData, {
    timeout: 180000,
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return
      onProgress(Math.round((event.loaded / event.total) * 100))
    },
  })
}

/** 删除整期（含全部商品），仅管理员 */
export const deleteMallPeriod = (periodId: number): Promise<Result<void>> => {
  return del(`/hyol/mall/period/${periodId}`)
}

/** 删除某一天的导入数据，仅管理员；删完为空则整期一并删除 */
export const deleteMallDay = (periodId: number, day: string): Promise<Result<void>> => {
  return del(`/hyol/mall/day?periodId=${periodId}&day=${encodeURIComponent(day)}`)
}
