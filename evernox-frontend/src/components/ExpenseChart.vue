<template>
  <div class="expense-chart">
    <div ref="chartEl" class="chart-body"></div>
    <div v-if="isEmpty" class="chart-empty">暂无消费记录</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { LegacyGridContainLabel } from 'echarts/features'
import { CanvasRenderer } from 'echarts/renderers'
import type { ExpenseChartPoint } from '@/types/expense'

echarts.use([LineChart, GridComponent, TooltipComponent, LegacyGridContainLabel, CanvasRenderer])

const props = defineProps<{
  points: ExpenseChartPoint[]
  startDate: string
  endDate: string
}>()

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const isEmpty = computed(() => props.points.length === 0)

/** 金额格式化：千分位 + 两位小数 */
const formatAmount = (v: number): string => {
  return '¥' + v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 生成 startDate ~ endDate 的连续日期数组（yyyy-MM-dd） */
const buildDateRange = (start: string, end: string): string[] => {
  const result: string[] = []
  const cursor = new Date(start + 'T00:00:00')
  const endDate = new Date(end + 'T00:00:00')
  if (isNaN(cursor.getTime()) || isNaN(endDate.getTime()) || cursor > endDate) return result
  while (cursor <= endDate) {
    const y = cursor.getFullYear()
    const m = String(cursor.getMonth() + 1).padStart(2, '0')
    const d = String(cursor.getDate()).padStart(2, '0')
    result.push(`${y}-${m}-${d}`)
    cursor.setDate(cursor.getDate() + 1)
    if (result.length > 400) break // 防御：异常范围兜底
  }
  return result
}

const disposeChart = () => {
  resizeObserver?.disconnect()
  resizeObserver = null
  chart?.dispose()
  chart = null
}

const render = () => {
  if (isEmpty.value) {
    disposeChart()
    return
  }
  if (!chartEl.value) return

  if (!chart) {
    chart = echarts.init(chartEl.value)
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(chartEl.value)
  }

  const dates = buildDateRange(props.startDate, props.endDate)
  const totalMap = new Map(props.points.map((p) => [p.date, p.total]))
  const breakdownMap = new Map(props.points.map((p) => [p.date, p.breakdown]))
  const values = dates.map((d) => totalMap.get(d) ?? 0)

  const option: echarts.EChartsCoreOption = {
    grid: { left: 12, right: 16, top: 28, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.94)',
      borderColor: 'rgba(47,124,246,0.18)',
      borderWidth: 1,
      padding: [10, 14],
      textStyle: { color: '#12304f', fontSize: 12 },
      extraCssText: 'box-shadow: 0 8px 24px -8px rgba(47,124,246,0.24); border-radius: 12px;',
      axisPointer: { type: 'line', lineStyle: { color: 'rgba(47,124,246,0.35)', type: 'dashed' } },
      formatter: (params: unknown) => {
        const list = params as { dataIndex: number }[]
        if (!list || !list.length) return ''
        const idx = list[0].dataIndex
        const date = dates[idx]
        const total = values[idx]
        const items = breakdownMap.get(date) ?? []
        let html = `<div style="font-weight:700;margin-bottom:4px;">${date}</div>`
        html += `<div style="color:#2f7cf6;font-weight:600;margin-bottom:6px;">当日支出 ${formatAmount(total)}</div>`
        if (items.length) {
          for (const it of items) {
            html += `<div style="display:flex;justify-content:space-between;gap:16px;">
              <span style="color:#4c6b8a;">${it.categoryName ?? '未分类'}</span>
              <span>${formatAmount(it.amount)}</span></div>`
          }
        }
        return html
      },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLine: { lineStyle: { color: 'rgba(47,124,246,0.16)' } },
      axisTick: { show: false },
      axisLabel: {
        color: '#8aa3bb',
        fontSize: 11,
        formatter: (v: string) => {
          const parts = v.split('-')
          return `${Number(parts[1])}/${Number(parts[2])}`
        },
      },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(47,124,246,0.08)' } },
      axisLabel: {
        color: '#8aa3bb',
        fontSize: 11,
        formatter: (v: number) => (v >= 10000 ? `${(v / 10000).toFixed(1)}万` : String(v)),
      },
    },
    series: [
      {
        name: '支出',
        type: 'line',
        smooth: true,
        showSymbol: false,
        symbol: 'circle',
        symbolSize: 7,
        data: values,
        lineStyle: { width: 2.5, color: '#2f7cf6' },
        itemStyle: { color: '#2f7cf6', borderColor: '#fff', borderWidth: 2 },
        emphasis: { focus: 'series' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(47,124,246,0.28)' },
              { offset: 0.6, color: 'rgba(79,195,232,0.10)' },
              { offset: 1, color: 'rgba(79,195,232,0.01)' },
            ],
          },
        },
      },
    ],
  }
  chart.setOption(option, true)
}

onMounted(render)

watch(() => [props.points, props.startDate, props.endDate], render)

onBeforeUnmount(disposeChart)
</script>

<style scoped lang="scss">
.expense-chart {
  width: 100%;
  height: 300px;
  position: relative;
}

.chart-body {
  position: absolute;
  inset: 0;
}

.chart-empty {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ev-text-muted, #90a4bb);
  font-size: 13px;
}
</style>
