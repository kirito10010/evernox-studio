import { get, post, del } from '@/utils/request'
import type { Result } from '@/types/user'
import type { BattleImportResult, BattleItem, BattlePeriod } from '@/types/hyolBattle'

/** 期列表（最新在前） */
export const getBattlePeriods = (): Promise<Result<BattlePeriod[]>> => {
  return get('/hyol/battle/periods')
}

/** 某期全部商品（带阵营） */
export const getBattleItems = (periodId: number): Promise<Result<BattleItem[]>> => {
  return get(`/hyol/battle/items?periodId=${periodId}`)
}

/**
 * 整期导入：formData 里 manifest = 战场的召唤.json，images = images 文件夹下的全部图标。
 *
 * 必须显式声明 multipart/form-data：request 实例默认是 application/json，
 * 否则 axios 会把 FormData 序列化成 JSON，后端收到空请求。
 */
export const importBattleFolder = (
  formData: FormData,
  onProgress?: (percent: number) => void
): Promise<Result<BattleImportResult>> => {
  return post('/hyol/battle/import', formData, {
    timeout: 180000,
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return
      onProgress(Math.round((event.loaded / event.total) * 100))
    },
  })
}

/** 删除整期（含阵营与商品），仅管理员 */
export const deleteBattlePeriod = (periodId: number): Promise<Result<void>> => {
  return del(`/hyol/battle/period/${periodId}`)
}
