<template>
  <div class="china-map">
    <div ref="chartEl" class="chart-body"></div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LinesChart } from 'echarts/charts'
import { GeoComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import rawChinaCities from '@/assets/game/china-cities.geo.json'
import rawChinaProvinces from '@/assets/game/china-provinces.geo.json'
import type { GameCityState } from '@/types/game'

// 三沙市包含南海诸岛（最低纬度 3.8°N），会把整图包围盒往南拉得过长、让大陆显得很小，这里从渲染中剔除
const chinaCities = {
  ...rawChinaCities,
  features: (
    rawChinaCities as unknown as { features: Array<{ properties: { name: string } }> }
  ).features.filter((f) => f.properties.name !== '三沙市'),
}

// 省界轮廓：从省 geoJSON 提取每个多边形外环，作为共享 geo 坐标系上的描边线（不填色）
const buildProvinceBorders = (): Array<{ coords: number[][] }> => {
  const borders: Array<{ coords: number[][] }> = []
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const features = (rawChinaProvinces as any).features as Array<{
    geometry: { type: string; coordinates: any }
  }>
  for (const f of features) {
    const geom = f.geometry
    if (geom.type !== 'MultiPolygon' && geom.type !== 'Polygon') continue
    const polygons = geom.type === 'Polygon' ? [geom.coordinates] : geom.coordinates
    for (const poly of polygons) {
      const ring = poly && poly[0]
      if (ring && ring.length >= 3) {
        borders.push({ coords: ring as number[][] })
      }
    }
  }
  return borders
}
const provinceBorders = buildProvinceBorders()

echarts.use([GeoComponent, TooltipComponent, LinesChart, CanvasRenderer])
echarts.registerMap('china-cities', chinaCities as unknown as Parameters<typeof echarts.registerMap>[1])

const props = defineProps<{
  cities: GameCityState[]
  myUserId: number | null
}>()

const emit = defineEmits<{
  (e: 'select', cityId: number): void
}>()

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null
const selectedName = ref<string | null>(null)

const colorOf = (c: GameCityState): string => {
  if (c.ownerUserId == null) return '#e8edf3'
  if (props.myUserId != null && c.ownerUserId === props.myUserId) return '#2f7cf6'
  return '#f59e0b'
}

const buildRegions = () =>
  props.cities
    .filter((c) => c.name !== '三沙市')
    .map((c) => ({
      name: c.name,
      itemStyle: { areaColor: colorOf(c) },
      ...(selectedName.value === c.name ? { selected: true } : {}),
    }))

const buildOption = (): echarts.EChartsCoreOption => ({
  // geo 组件既是共享坐标系，也负责渲染市级地图（填色、选中、缩放平移）
  geo: {
    map: 'china-cities',
    roam: true,
    scaleLimit: { min: 1, max: 20 },
    zoom: 1,
    selectedMode: 'single',
    label: { show: false },
    itemStyle: {
      areaColor: '#e8edf3',
      borderColor: '#ffffff',
      borderWidth: 0.5,
    },
    emphasis: {
      label: { show: true, fontSize: 10, color: '#12304f' },
      itemStyle: { areaColor: '#7fb2fb' },
    },
    select: {
      label: { show: true, fontSize: 10, color: '#12304f' },
      itemStyle: { areaColor: '#8b5cf6', borderColor: '#ffffff', borderWidth: 2.5 },
    },
    regions: buildRegions(),
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255,255,255,0.94)',
      borderColor: 'rgba(47,124,246,0.18)',
      borderWidth: 1,
      textStyle: { color: '#12304f', fontSize: 12 },
      formatter: (params: unknown) => {
        const p = params as { name?: string }
        const city = props.cities.find((c) => c.name === p.name)
        if (!city) return String(p.name ?? '')
        const owner =
          city.ownerUserId == null
            ? '无主'
            : city.ownerUserId === props.myUserId
              ? '我方'
              : '他人'
        return `${city.name}（${city.province}）<br/>归属：${owner}<br/>等级：${city.level} · 驻防：${city.garrison}`
      },
    },
  },
  series: [
    {
      // 省界轮廓层：压在城市填色之上，共享 geo 坐标系，随缩放平移同步移动
      type: 'lines',
      coordinateSystem: 'geo',
      geoIndex: 0,
      polyline: true,
      silent: true,
      z: 10,
      lineStyle: {
        color: '#64748b',
        width: 1.2,
        opacity: 1,
      },
      data: provinceBorders,
    },
  ],
})

const initChart = () => {
  if (!chartEl.value) return
  chart = echarts.init(chartEl.value)
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartEl.value)
  chart.on('click', (params) => {
    const p = params as { name?: string }
    const city = props.cities.find((c) => c.name === p.name)
    if (city) {
      selectedName.value = city.name
      emit('select', city.id)
    }
  })
  chart.setOption(buildOption())
}

// 仅更新市级 regions，不触碰 zoom/center，避免 SSE 刷新把用户的缩放/平移视角重置
const updateData = () => {
  chart?.setOption({ geo: { regions: buildRegions() } })
}

const dispose = () => {
  resizeObserver?.disconnect()
  resizeObserver = null
  chart?.dispose()
  chart = null
}

onMounted(initChart)
watch(() => props.cities, updateData, { deep: true })
onBeforeUnmount(dispose)
</script>

<style scoped lang="scss">
.china-map {
  width: 100%;
  height: 100%;
  position: relative;
}

.chart-body {
  position: absolute;
  inset: 0;
}
</style>
