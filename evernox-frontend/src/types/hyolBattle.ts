import type { MallDetail } from '@/types/hyolMall'

/** 战场的召唤：导出只有结束时间，一期导入一次 */
export interface BattleCamp {
  /** 阵营名称，同时也是该阵营的货币名 */
  name: string
  /** 代表忍者 */
  representative?: string
}

export interface BattlePeriod {
  id: number
  activityId?: number
  name: string
  endTime: string
  /** 截止 2026-12-03 */
  label: string
  itemCount: number
  camps: BattleCamp[]
}

export interface BattleItem {
  id: number
  periodId: number
  camp: string
  slot: number
  itemId: number
  name: string
  /** 兑换一次获得数量 */
  quantity: number
  description?: string
  iconId?: number
  price: number
  priceText?: string
  currency: string
  limitCount: number
  image?: string
  detail?: MallDetail | null
}

export interface BattleImportResult {
  periodId: number
  periodLabel: string
  itemCount: number
  campCount: number
  imageCount: number
  missingImages: number
  created: boolean
}
