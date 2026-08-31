export interface OrgOrganization {
  id: number
  name: string
  createdAt: string
}

export interface OrgOrganizationRequest {
  name: string
}

export interface OrgMember {
  id: number
  organizationId: number
  organizationName: string | null
  name: string
  position: string | null
  status: number
  createdAt: string
}

export interface OrgMemberRequest {
  organizationId: number
  name: string
  position?: string
}

export interface OrgRewardPackage {
  id: number
  organizationId: number
  name: string
  deductionRatio: number
  sortOrder: number
}

export interface OrgRewardPackageRequest {
  name: string
  deductionRatio: number
  sortOrder?: number
}

export interface OrgPointsConfig {
  id?: number
  organizationId?: number
  ninjaBattlePoints: number
  ninjaBattleEnabled: number
  totalPowerPoints: number
  totalPowerEnabled: number
  powerIncreasePoints: number
  powerIncreaseEnabled: number
  copperPoints: number
  copperEnabled: number
  beastPoints: number
  beastEnabled: number
  renegadePoints: number
  renegadeEnabled: number
  renegadeLeaderBonus: number
  renegadeLeaderEnabled: number
  noPackageAdjustment: number
  ninjaBattleVisible: number
  totalPowerVisible: number
  powerIncreaseVisible: number
  copperVisible: number
  beastVisible: number
  renegadeVisible: number
  renegadeLeaderVisible: number
}

export interface OrgWeekRecord {
  id: number
  organizationId: number | null
  organizationName: string | null
  weekDate: string
  memberId: number | null
  memberName: string
  position: string | null
  ninjaBattleCount: number | null
  totalPower: number | null
  powerIncrease: number | null
  copperContribution: number | null
  beastSacrifice: number | null
  renegadeCount: number | null
  isRenegadeLeader: number | null
  lastWeekPoints: number | null
  thisWeekPoints: number | null
  totalPoints: number | null
  deductionRatio: number | null
  pointsAfterDeduction: number | null
  rewardPackageId: number | null
  rewardPackageName: string | null
}

export interface OrgWeekRecordUpdateRequest {
  ninjaBattleCount?: number
  totalPower?: number
  powerIncrease?: number
  copperContribution?: number
  beastSacrifice?: number
  renegadeCount?: number
  isRenegadeLeader?: number
}

export interface OrgImportResult {
  importedNames: string[]
  unmatchedNames: string[]
  emptyNames: string[]
}

export interface OrgMemberImportResult {
  importedNames: string[]
  skippedNames: string[]
}

export interface OrgMembership {
  id: number
  organizationId: number
  organizationName: string | null
  status: number
}

export interface OrgMembershipApplication {
  id: number
  organizationId: number
  organizationName: string | null
  userId: number
  username: string | null
  email: string | null
  appliedAt: string | null
}
