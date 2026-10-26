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

export interface OrgRenegadeUnmatchedItem {
  line: number
  name: string
}

export interface OrgRenegadeImportResult {
  allMatched: boolean
  totalLines: number
  matchedCount: number
  unmatchedCount: number
  importedCount: number
  unmatchedItems: OrgRenegadeUnmatchedItem[]
}

export interface OrgMemberImportCandidate {
  name: string
  position: string | null
}

export interface OrgMemberImportUpdateCandidate {
  memberId: number
  name: string
  oldPosition: string | null
  newPosition: string | null
}

export interface OrgMemberImportPreviewResult {
  toAdd: OrgMemberImportCandidate[]
  unchangedNames: string[]
  toRestore: OrgMember[]
  toUpdate: OrgMemberImportUpdateCandidate[]
  toLeave: OrgMember[]
}

export interface OrgMemberImportApplyRequest {
  organizationId: number
  add: OrgMemberImportCandidate[]
  updates: OrgMemberImportUpdateCandidate[]
  restoreIds: number[]
  leaveIds: number[]
}

export interface OrgMemberImportApplyResult {
  addedNames: string[]
  updatedNames: string[]
  restoredNames: string[]
  leftNames: string[]
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

// ==================== 一键发包方案 ====================

export type OrgDistributionRankField =
  | 'totalPoints'
  | 'thisWeekPoints'
  | 'powerIncrease'
  | 'totalPower'
  | 'copperContribution'
  | 'beastSacrifice'
  | 'renegadeCount'

export const OrgDistributionRankLabels: Record<OrgDistributionRankField, string> = {
  totalPoints: '总积分',
  thisWeekPoints: '本周积分',
  powerIncrease: '战力增幅',
  totalPower: '总战力',
  copperContribution: '铜币',
  beastSacrifice: '通灵兽',
  renegadeCount: '叛忍',
}

export interface OrgDistributionPlanItem {
  id?: number
  packageId: number
  packageName?: string
  quantity: number
  sortOrder?: number
}

export interface OrgDistributionForced {
  id?: number
  memberId: number
  memberName?: string
  packageId: number
  packageName?: string
}

export interface OrgDistributionPlan {
  id: number
  organizationId: number
  name: string
  rankField: OrgDistributionRankField
  items: OrgDistributionPlanItem[]
  forced: OrgDistributionForced[]
}

export interface OrgDistributionPlanRequest {
  name: string
  rankField: OrgDistributionRankField
  items: { packageId: number; quantity: number }[]
  forced: { memberId: number; packageId: number }[]
}

export interface OrgDistributionRunResult {
  planName: string
  assignedCount: number
  forcedCount: number
  rankedCount: number
  skippedZeroBattleCount: number
  skippedForcedNames: string[]
  packageSummary: { packageName: string; count: number }[]
}
