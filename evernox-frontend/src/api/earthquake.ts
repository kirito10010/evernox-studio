import { get } from '@/utils/request'
import type { Result } from '@/types/user'

export interface EarthquakeItem {
  time: string
  longitude: string
  latitude: string
  depth: string
  magnitude: string
  location: string
  type: string
}

export const getEarthquakeList = (): Promise<Result<EarthquakeItem[]>> => {
  return get('/utility/earthquake/list')
}
