import { get, post } from '@/utils/request'
import type { Result } from '@/types/user'
import type { GameActionRequest, GameMapResponse, GameRankItem } from '@/types/game'

/** 获取当前地图全量状态与我的参赛状态 */
export const getGameMap = (): Promise<Result<GameMapResponse>> => {
  return get('/game/map')
}

/** 选底盘加入本轮 */
export const joinGame = (baseCityId: number): Promise<Result<void>> => {
  return post(`/game/join?baseCityId=${baseCityId}`)
}

/** 执行一次操作 */
export const doGameAction = (data: GameActionRequest): Promise<Result<void>> => {
  return post('/game/action', data)
}

/** 排行榜 */
export const getGameRank = (): Promise<Result<GameRankItem[]>> => {
  return get('/game/rank')
}
