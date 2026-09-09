<template>
  <div class="weather-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">天气查询</h2>
        <p class="page-sub">数据来源：和风天气 · 输入城市名搜索添加</p>
      </div>
    </div>

    <div class="search-box">
      <el-input
        v-model="keyword"
        placeholder="输入城市名（如：泰安岱岳区、北京、海淀），回车或稍等自动搜索"
        clearable
        @input="onInput"
        @keyup.enter="doSearch"
        @clear="searchResults = []"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div v-if="searchResults.length" class="search-results">
        <div
          v-for="c in searchResults"
          :key="c.id"
          class="search-result-item"
          @mousedown.prevent="addCity(c)"
        >
          <span class="city-name">{{ c.name }}</span>
          <span class="city-adm">{{ c.adm1 }} {{ c.adm2 }}</span>
        </div>
      </div>
    </div>

    <div v-loading="loading" class="weather-grid">
      <div v-for="w in weatherList" :key="w.city" class="weather-card">
        <div class="weather-card-head">
          <span class="city">{{ w.city }}</span>
          <el-icon class="remove-btn" @click="removeCity(w.city)"><Close /></el-icon>
        </div>
        <div class="temp">
          <span class="temp-num">{{ w.temp || '-' }}</span>
          <span class="temp-unit">℃</span>
          <span class="text">{{ w.text }}</span>
        </div>
        <div class="meta">
          <div class="meta-item"><span class="label">体感</span><span>{{ w.feelsLike || '-' }}℃</span></div>
          <div class="meta-item"><span class="label">风向</span><span>{{ w.windDir || '-' }} {{ w.windScale || '' }}</span></div>
          <div class="meta-item"><span class="label">湿度</span><span>{{ w.humidity || '-' }}%</span></div>
          <div class="meta-item"><span class="label">气压</span><span>{{ w.pressure || '-' }}hPa</span></div>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && weatherList.length === 0" description="搜索城市名添加天气" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getWeatherNow, searchCities } from '@/api/weather'
import type { CityItem, WeatherItem } from '@/api/weather'

const KEY = 'support_weather_cities'

const keyword = ref('')
const searchResults = ref<CityItem[]>([])
const cities = ref<CityItem[]>([])
const weatherList = ref<WeatherItem[]>([])
const loading = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | null = null

const onInput = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(doSearch, 1000)
}

const doSearch = async () => {
  if (searchTimer) clearTimeout(searchTimer)
  const kw = keyword.value.trim()
  if (!kw) {
    searchResults.value = []
    return
  }
  try {
    const res = await searchCities(kw)
    searchResults.value = res.data ?? []
  } catch {
    searchResults.value = []
  }
}

const addCity = (c: CityItem) => {
  searchResults.value = []
  keyword.value = ''
  if (cities.value.some((x) => x.id === c.id)) {
    return
  }
  cities.value.push(c)
  saveCities()
  loadWeather()
}

const removeCity = (city: string) => {
  cities.value = cities.value.filter((x) => x.name !== city)
  weatherList.value = weatherList.value.filter((w) => w.city !== city)
  saveCities()
}

const saveCities = () => {
  localStorage.setItem(KEY, JSON.stringify(cities.value))
}

const loadCities = () => {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) cities.value = JSON.parse(raw) as CityItem[]
  } catch {
    /* 忽略 */
  }
}

const loadWeather = async () => {
  loading.value = true
  try {
    const items: WeatherItem[] = []
    for (const c of cities.value) {
      try {
        const res = await getWeatherNow(c.id, c.name)
        if (res.data) items.push(res.data)
      } catch {
        /* 单个城市失败跳过 */
      }
    }
    weatherList.value = items
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadCities()
  loadWeather()
})
</script>

<style scoped lang="scss">
.weather-page {
  .page-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .page-title {
      margin: 0 0 4px;
      font-size: 20px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }

    .page-sub {
      margin: 0;
      font-size: 13px;
      color: var(--ev-text-muted);
    }
  }

  .search-box {
    position: relative;
    max-width: 420px;
    margin-bottom: 18px;

    .search-results {
      position: absolute;
      top: 100%;
      left: 0;
      right: 0;
      margin-top: 4px;
      background: var(--el-bg-color);
      border: 1px solid var(--ev-border-subtle);
      border-radius: 10px;
      box-shadow: var(--ev-shadow-sm);
      max-height: 280px;
      overflow-y: auto;
      z-index: 20;

      .search-result-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 10px 12px;
        cursor: pointer;

        &:hover {
          background: var(--ev-bg-tint);
        }

        .city-name {
          font-size: 14px;
          color: var(--ev-text-primary);
        }

        .city-adm {
          font-size: 12px;
          color: var(--ev-text-muted);
        }
      }
    }
  }

  .weather-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 14px;
    min-height: 120px;

    @media (max-width: 1100px) {
      grid-template-columns: repeat(3, 1fr);
    }

    @media (max-width: 800px) {
      grid-template-columns: repeat(2, 1fr);
    }
  }

  .weather-card {
    position: relative;
    padding: 16px;
    border-radius: 14px;
    background: var(--el-bg-color);
    border: 1px solid var(--ev-border-subtle);
    box-shadow: var(--ev-shadow-xs);

    .weather-card-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;

      .city {
        font-size: 15px;
        font-weight: 700;
        color: var(--ev-text-primary);
      }

      .remove-btn {
        cursor: pointer;
        color: var(--ev-text-muted);

        &:hover {
          color: var(--ev-danger);
        }
      }
    }

    .temp {
      display: flex;
      align-items: baseline;
      gap: 6px;
      margin-bottom: 12px;

      .temp-num {
        font-size: 34px;
        font-weight: 800;
        color: var(--ev-text-primary);
        line-height: 1;
      }

      .temp-unit {
        font-size: 16px;
        color: var(--ev-text-muted);
      }

      .text {
        font-size: 14px;
        color: var(--ev-text-secondary);
        margin-left: auto;
      }
    }

    .meta {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px 12px;
      font-size: 13px;

      .meta-item {
        display: flex;
        justify-content: space-between;
        color: var(--ev-text-secondary);

        .label {
          color: var(--ev-text-muted);
        }
      }
    }
  }
}
</style>
