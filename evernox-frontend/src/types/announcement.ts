export interface AnnouncementTag {
  id: number
  name: string
  color: string
}

export interface AnnouncementTagRequest {
  name: string
  color: string
}

export interface AnnouncementRequest {
  tagId: number | null
  title: string
  content: string
}

export interface AnnouncementResponse {
  id: number
  title: string
  content: string | null
  tagId: number | null
  tagName: string | null
  tagColor: string | null
  createdBy: number
  createdByName?: string
  /** 用户列表用：当前用户是否已读 */
  read?: boolean
  createdAt: string
  updatedAt: string
}

export interface UnreadCount {
  unread: number
}

export interface AnnouncementAdminListParams {
  page: number
  size: number
  keyword?: string
}
