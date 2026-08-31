import { post } from '@/utils/request'
import type { Result } from '@/types/user'

/**
 * 每日签到
 */
export const signIn = (): Promise<Result<void>> => {
  return post('/points/sign-in')
}

/**
 * 自助用积分开通/续费超级会员
 */
export const upgradeSuperMember = (days: number): Promise<Result<void>> => {
  return post(`/points/upgrade-super-member?days=${days}`)
}

/**
 * 使用卡密兑换超级会员
 */
export const redeemCode = (code: string): Promise<Result<void>> => {
  return post('/redemption/redeem', { code })
}

