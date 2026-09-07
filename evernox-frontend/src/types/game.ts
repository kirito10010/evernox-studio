/** 沙盘争霸相关类型 */

export interface GameCityState {
  id: number
  adcode: string
  name: string
  province: string
  centerLng: number
  centerLat: number
  weight: number
  baseDefense: number
  ownerUserId: number | null
  level: number
  garrison: number
}

export interface GameMapResponse {
  roundId: number
  roundNo: number
  /** 0未开始/1进行中/2已结算，-1表示无轮次 */
  roundStatus: number
  startAt: string | null
  settleAt: string | null
  endAt: string | null
  joined: boolean
  baseCityId: number | null
  force: number
  actionPoints: number
  ownedCityCount: number
  /** 已完整占领的省数量 */
  completedProvinces: number
  powerScore: number
  cities: GameCityState[]
}

export interface GameRankItem {
  rank: number
  userId: number
  username: string
  ownedCityCount: number
  force: number
  powerScore: number
}

export type GameActionType = 'occupy' | 'attack' | 'garrison' | 'develop'

export interface GameActionRequest {
  type: GameActionType
  cityId: number
  force?: number
}

export interface GameMapChangeEvent {
  roundId: number
  cityId: number
  ownerUserId: number | null
  level: number
  garrison: number
}
