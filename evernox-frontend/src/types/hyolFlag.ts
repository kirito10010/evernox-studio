import type { MallItem } from '@/types/hyolMall'

/** 彩旗献礼：一年约 3 期，一期导入一次 */
export interface FlagPeriod {
  id: number
  activityId?: number
  shopId?: number
  name: string
  startTime: string
  endTime: string
  /** 2026-11-19 ~ 2026-12-03 */
  label: string
  itemCount: number
}

/** 商品结构与商城特惠一致，多一个类别字段 */
export interface FlagItem extends MallItem {
  category?: string
}

export interface FlagImportResult {
  periodId: number
  periodLabel: string
  itemCount: number
  imageCount: number
  missingImages: number
  created: boolean
}
