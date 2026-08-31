import { get, post } from '@/utils/request'
import type { Result } from '@/types/user'
import type { OrgMembership, OrgOrganization, OrgPointsConfig, OrgWeekRecord } from '@/types/org'

export const getPublicOrgOrganizations = (): Promise<Result<OrgOrganization[]>> => {
  return get('/org/points/organizations')
}

export const getPublicOrgWeeks = (organizationId: number): Promise<Result<string[]>> => {
  return get(`/org/points/weeks?organizationId=${organizationId}`)
}

export const getPublicOrgRecords = (
  organizationId: number,
  weekDate: string
): Promise<Result<OrgWeekRecord[]>> => {
  return get(`/org/points/records?organizationId=${organizationId}&weekDate=${weekDate}`)
}

export const getPublicOrgPointsConfig = (organizationId: number): Promise<Result<OrgPointsConfig>> => {
  return get(`/org/points/config?organizationId=${organizationId}`)
}

export const getMyOrgMemberships = (): Promise<Result<OrgMembership[]>> => {
  return get('/org/membership/mine')
}

export const applyOrgMembership = (organizationId: number): Promise<Result<void>> => {
  return post(`/org/membership/apply?organizationId=${organizationId}`)
}

export const leaveOrgMembership = (organizationId: number): Promise<Result<void>> => {
  return post(`/org/membership/leave?organizationId=${organizationId}`)
}
