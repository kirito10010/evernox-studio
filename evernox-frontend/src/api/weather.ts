import { get } from '@/utils/request'
import type { Result } from '@/types/user'

export interface WeatherItem {
  city: string
  temp: string
  feelsLike: string
  text: string
  windDir: string
  windScale: string
  humidity: string
  pressure: string
}

export interface CityItem {
  name: string
  id: string
  adm1: string
  adm2: string
}

export const getWeatherList = (): Promise<Result<WeatherItem[]>> => {
  return get('/utility/weather/list')
}

export const searchCities = (keyword: string): Promise<Result<CityItem[]>> => {
  return get('/utility/weather/search', { params: { keyword } })
}

export const getWeatherNow = (location: string, name: string): Promise<Result<WeatherItem>> => {
  return get('/utility/weather/now', { params: { location, name } })
}
