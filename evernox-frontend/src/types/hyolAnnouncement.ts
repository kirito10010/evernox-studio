export interface HyolAnnouncement {
  id: number
  title: string
  sourceUrl: string
  publishTime: string | null
  content: string | null
  createdAt: string
}

export interface HyolRefreshResult {
  fetched: number
  failed: number
}
