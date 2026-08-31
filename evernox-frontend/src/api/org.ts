import { get, post, put, del } from '@/utils/request'
import type { Result } from '@/types/user'
import type {
  OrgImportResult,
  OrgMember,
  OrgMemberImportResult,
  OrgMemberRequest,
  OrgMembershipApplication,
  OrgOrganization,
  OrgOrganizationRequest,
  OrgPointsConfig,
  OrgRewardPackage,
  OrgRewardPackageRequest,
  OrgWeekRecord,
  OrgWeekRecordUpdateRequest,
} from '@/types/org'

// ==================== 组织 ====================

export const getOrgOrganizations = (): Promise<Result<OrgOrganization[]>> => {
  return get('/admin/org/organizations')
}

export const createOrgOrganization = (data: OrgOrganizationRequest): Promise<Result<OrgOrganization>> => {
  return post('/admin/org/organizations', data)
}

export const updateOrgOrganization = (
  id: number,
  data: OrgOrganizationRequest
): Promise<Result<OrgOrganization>> => {
  return put(`/admin/org/organizations/${id}`, data)
}

export const deleteOrgOrganization = (id: number): Promise<Result<void>> => {
  return del(`/admin/org/organizations/${id}`)
}

// ==================== 成员 ====================

export const getOrgMembers = (): Promise<Result<OrgMember[]>> => {
  return get('/admin/org/members')
}

export const createOrgMember = (data: OrgMemberRequest): Promise<Result<OrgMember>> => {
  return post('/admin/org/members', data)
}

export const updateOrgMember = (id: number, data: OrgMemberRequest): Promise<Result<OrgMember>> => {
  return put(`/admin/org/members/${id}`, data)
}

export const updateOrgMemberStatus = (id: number, status: number): Promise<Result<void>> => {
  return put(`/admin/org/members/${id}/status?status=${status}`)
}

export const importOrgMembers = (
  file: File,
  organizationId: number
): Promise<Result<OrgMemberImportResult>> => {
  const formData = new FormData()
  formData.append('file', file)
  return post(`/admin/org/members/import?organizationId=${organizationId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}

// ==================== 积分换算比 ====================

export const getOrgPointsConfig = (organizationId: number): Promise<Result<OrgPointsConfig>> => {
  return get(`/admin/org/points-config?organizationId=${organizationId}`)
}

export const saveOrgPointsConfig = (
  organizationId: number,
  data: OrgPointsConfig
): Promise<Result<OrgPointsConfig>> => {
  return put(`/admin/org/points-config?organizationId=${organizationId}`, data)
}

// ==================== 奖励礼包 ====================

export const getOrgPackages = (organizationId: number): Promise<Result<OrgRewardPackage[]>> => {
  return get(`/admin/org/packages?organizationId=${organizationId}`)
}

export const createOrgPackage = (
  organizationId: number,
  data: OrgRewardPackageRequest
): Promise<Result<OrgRewardPackage>> => {
  return post(`/admin/org/packages?organizationId=${organizationId}`, data)
}

export const updateOrgPackage = (
  id: number,
  data: OrgRewardPackageRequest
): Promise<Result<OrgRewardPackage>> => {
  return put(`/admin/org/packages/${id}`, data)
}

export const deleteOrgPackage = (id: number): Promise<Result<void>> => {
  return del(`/admin/org/packages/${id}`)
}

// ==================== 周记录 ====================

export const getOrgWeeks = (organizationId: number): Promise<Result<string[]>> => {
  return get(`/admin/org/weeks?organizationId=${organizationId}`)
}

export const getOrgRecords = (organizationId: number, weekDate: string): Promise<Result<OrgWeekRecord[]>> => {
  return get(`/admin/org/records?organizationId=${organizationId}&weekDate=${weekDate}`)
}

export const generateOrgRecords = (organizationId: number, weekDate?: string): Promise<Result<number>> => {
  const qs = weekDate ? `&weekDate=${weekDate}` : ''
  return post(`/admin/org/records/generate?organizationId=${organizationId}${qs}`)
}

export const calculateOrgRecords = (organizationId: number, weekDate: string): Promise<Result<number>> => {
  return post(`/admin/org/records/calculate?organizationId=${organizationId}&weekDate=${weekDate}`)
}

export const importOrgExcel = (
  file: File,
  organizationId: number,
  weekDate?: string
): Promise<Result<OrgImportResult>> => {
  const formData = new FormData()
  formData.append('file', file)
  const qs = weekDate ? `&weekDate=${weekDate}` : ''
  return post(`/admin/org/records/import?organizationId=${organizationId}${qs}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}

export const setOrgRecordPackage = (id: number, packageId: number): Promise<Result<OrgWeekRecord>> => {
  return put(`/admin/org/records/${id}/package?packageId=${packageId}`)
}

export const clearOrgRecordPackage = (id: number): Promise<Result<void>> => {
  return del(`/admin/org/records/${id}/package`)
}

export const updateOrgRecord = (
  id: number,
  data: OrgWeekRecordUpdateRequest
): Promise<Result<void>> => {
  return put(`/admin/org/records/${id}`, data)
}

export const deleteOrgWeek = (organizationId: number, weekDate: string): Promise<Result<void>> => {
  return del(`/admin/org/records/${organizationId}/${weekDate}`)
}

// ==================== 加入审批 ====================

export const getOrgApplications = (): Promise<Result<OrgMembershipApplication[]>> => {
  return get('/admin/org/applications')
}

export const approveOrgApplication = (id: number): Promise<Result<void>> => {
  return post(`/admin/org/applications/${id}/approve`)
}

export const rejectOrgApplication = (id: number): Promise<Result<void>> => {
  return post(`/admin/org/applications/${id}/reject`)
}
