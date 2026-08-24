export interface NinjaSkill {
  title: string | null
  type: string | null
  moment: string | null
  desc: string | null
  hurtType: string | null
  chaseStatus: string | null
  hurtStatus: string | null
  rare: string | null
  iconUrl: string | null
}

export interface HyolNinja {
  id: number
  nid: string
  name: string | null
  nickname: string | null
  attr: string | null
  star: string | null
  org: string | null
  pos: string | null
  getWay: string | null
  effect: string | null
  effectChase: string | null
  avatarUrl: string | null
  avatarUrl3: string | null
  skills: NinjaSkill[]
}

export interface NinjaRefreshResult {
  fetched: number
  failed: number
}
