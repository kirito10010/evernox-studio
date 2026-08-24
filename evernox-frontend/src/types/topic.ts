export type TopicSort = 'hot' | 'latest' | 'like' | 'favorite' | 'comment'

export interface TopicCircle {
  id: number
  name: string
  description: string | null
  ownerId: number
  ownerName?: string
  postCount: number
  memberCount: number
  followed?: boolean
  createdAt: string
}

export interface TopicPost {
  id: number
  circleId: number
  circleName?: string
  userId: number
  authorName?: string
  title: string
  content: string | null
  likeCount: number
  commentCount: number
  favoriteCount: number
  liked?: boolean
  favorited?: boolean
  createdAt: string
}

export interface TopicComment {
  id: number
  postId: number
  postTitle?: string
  userId: number
  authorName?: string
  content: string
  createdAt: string
}

export interface TopicCircleRequest {
  name: string
  description?: string
}

export interface TopicPostRequest {
  circleId: number
  title: string
  content: string
}

export interface TopicCommentRequest {
  postId: number
  content: string
}

export interface TopicInteraction {
  liked?: boolean
  likeCount?: number
  favorited?: boolean
  favoriteCount?: number
}

export interface TopicCircleListParams {
  page: number
  size: number
  keyword?: string
  mine?: boolean
}

export interface TopicPostListParams {
  page: number
  size: number
  sort?: TopicSort
}

export interface TopicMember {
  userId: number
  username: string
  isOwner: boolean
  createdAt: string
}

export interface TopicUserRank {
  userId: number
  username: string
  postCount: number
}

export interface TopicRank {
  circles: TopicCircle[]
  users: TopicUserRank[]
}
