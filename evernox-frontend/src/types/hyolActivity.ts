/** 火影忍者OL 活动时间轴 */

export interface HyolActivityItem {
  id: number
  /** 活动全名（官网原文，不做归类/改名） */
  name: string
  /** 旧字段，仅兼容后端返回，前端不再用它筛选 */
  category?: string | null
  startTime: string | null
  endTime: string | null
  announceDate: string | null
  sourceTitle: string | null
  sourceUrl: string
}

/** 活动名及出现次数（按次数从多到少） */
export interface HyolActivityNameCount {
  name: string
  count: number
}

export interface HyolActivityTimeline {
  items: HyolActivityItem[]
  names: HyolActivityNameCount[]
  total: number
  syncedAt: string | null
}

export interface HyolActivitySyncStatus {
  running: boolean
  full: boolean
  phase: string | null
  done: number
  total: number
  added: number
  updated: number
  failed: number
  skipped: number
  startedAt: string | null
  finishedAt: string | null
  message: string | null
}
