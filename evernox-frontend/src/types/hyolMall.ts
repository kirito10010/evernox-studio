/** 活动详情 · 商城特惠相关类型 */

/** 单日概览：一期固定 7 天，没导入数据的天 itemCount = 0 */
export interface MallDay {
  /** yyyy-MM-dd */
  date: string
  /** 周一…周日 */
  weekday: string
  itemCount: number
  windowStart: string
  windowEnd: string
}

/** 一期 = 周四 05:00 ~ 下周三 23:59:59 */
export interface MallPeriod {
  id: number
  activityId?: number
  shopId?: number
  name: string
  startTime: string
  endTime: string
  /** 2026-11-05 ~ 2026-11-11 */
  label: string
  dayCount: number
  itemCount: number
  /** 已导入数据的天数 */
  filledDayCount: number
  days: MallDay[]
}

/** 详情里的一条技能（奥义/普攻/追打/被动统一结构） */
export interface MallSkill {
  /** 技能ID（新版导出提供） */
  skillId?: number
  /** 奥义 / 普攻 / 追打 / 被动 */
  slot: string
  name: string
  /** 技能图标文件名（新版导出提供） */
  icon?: string
  description?: string
  /** 带【】标记的说明，前端据此渲染高亮词 */
  marked?: string
  highlights?: string[]
  /** 以下三项只有奥义有 */
  chakra?: number
  cooldown?: number
  entryCooldown?: number
}

/** 礼包掉落的一项 */
export interface MallDrop {
  name: string
  count: number
  itemId: number
}

/** 通灵兽碎片的「上阵忍者属性加成」 */
export interface MallBonus {
  life?: number
  attack?: number
  defense?: number
  ninjutsu?: number
}

/** 商品详情：忍者技能 / 通灵兽碎片 / 礼包掉落 / 普通材料说明 */
export interface MallDetail {
  /** ninja=忍者技能，beast=通灵兽碎片，drops=礼包掉落，item=普通材料 */
  type: 'ninja' | 'beast' | 'drops' | 'item'
  /** 导出数据里的忍者ID，仅作标识，不关联忍者图鉴 */
  ninjaId?: number
  /** 忍者名：商品名去掉「碎片」 */
  ninjaName?: string
  /** 物品说明（beast / drops / item）；忍者不返回 */
  description?: string
  /** 带【】标记的说明，前端据此渲染高亮词 */
  marked?: string
  /** 招募所需碎片数（通灵兽碎片） */
  needFragments?: number
  /** 上阵忍者属性加成（通灵兽碎片；导出数据没带时为 undefined） */
  bonus?: MallBonus | null
  skills?: MallSkill[]
  fixedDrops?: MallDrop[]
  randomDrops?: MallDrop[]
}

/** 一个价格选项（新版导出明确了元宝/点券两种货币） */
export interface PriceOption {
  currency: string
  price: number
  /** 货币图标文件名 */
  icon?: string
}

export interface MallItem {
  id: number
  periodId: number
  /** 归属日（商城特惠按天快照；彩旗献礼没有这个字段，故可选） */
  dayDate?: string
  slot: number
  itemId: number
  name: string
  description?: string
  iconId?: number
  price: number
  currency: string
  /** 价格选项；旧格式数据为空 */
  priceOptions?: PriceOption[]
  /** 兑换一次获得数量（战场的召唤使用） */
  quantity?: number
  dailyLimit: number
  startTime?: string
  endTime?: string
  /** 图标文件名，前端拼 /api/hyol/mall/image/{image} */
  image?: string
  /** 普通道具没有详情 */
  detail?: MallDetail | null
}

export interface MallImportResult {
  periodId: number
  periodLabel: string
  day: string
  weekday: string
  itemCount: number
  imageCount: number
  missingImages: number
  created: boolean
}
