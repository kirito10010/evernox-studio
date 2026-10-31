<template>
  <div class="expense-pie-chart">
    <div ref="chartEl" class="chart-body"></div>
    <div v-if="isEmpty" class="chart-empty">暂无消费记录</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ExpenseChartPoint } from '@/types/expense'

echarts.use([PieChart, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{ points: ExpenseChartPoint[] }>()

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const isEmpty = computed(() => props.points.length === 0)

const formatAmount = (v: number): string =>
  '¥' + (Number(v) || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** 按分类聚合各日 breakdown */
const aggregate = (): { name: string; value: number }[] => {
  const map = new Map<string, number>()
  for (const p of props.points) {
    for (const b of p.breakdown || []) {
      const name = b.categoryName || '未分类'
      map.set(name, (map.get(name) || 0) + (Number(b.amount) || 0))
    }
  }
  return [...map.entries()]
    .map(([name, value]) => ({ name, value: Math.round(value * 100) / 100 }))
    .filter((d) => d.value > 0)
    .sort((a, b) => b.value - a.value)
}

const totalAmount = computed(() => props.points.reduce((s, p) => s + (Number(p.total) || 0), 0))

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

  const option: echarts.EChartsCoreOption = {
    tooltip: {
      trigger: 'item',
      // 图表容器只有 300px 高、又嵌在卡片里，tooltip 画在容器内会被裁掉；
      // 挂到 body 上后可以盖到卡片外面，不会被 overflow/尺寸限制截断
      appendTo: 'body',
      confine: false,
      backgroundColor: 'rgba(255,255,255,0.94)',
      borderColor: 'rgba(47,124,246,0.18)',
      borderWidth: 1,
      padding: [6, 10],
      // 分类名较长时换行，而不是被压成一条看不见的窄条
      extraCssText: 'max-width: 260px; white-space: normal; line-height: 1.5; z-index: 3000;',
      textStyle: { color: '#12304f', fontSize: 12 },
      formatter: (params: unknown) => {
        const p = params as { name: string; value: number; percent: number }
        return `${p.name}<br/>${formatAmount(p.value)}（${p.percent}%）`
      },
    },
    legend: {
      bottom: 0,
      type: 'scroll',
      textStyle: { color: '#4c6b8a', fontSize: 12 },
    },
    title: {
      text: formatAmount(totalAmount.value),
      subtext: '总支出',
      left: 'center',
      top: '38%',
      textStyle: { fontSize: 16, fontWeight: 'bold', color: '#12304f' },
      subtextStyle: { fontSize: 12, color: '#8aa3bb' },
    },
    series: [
      {
        name: '支出',
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        // 只在鼠标跟随的 tooltip 里展示信息；扇区旁的文字标签全部关掉，避免窄卡片里挤出界
        label: { show: false },
        labelLine: { show: false },
        emphasis: {
          label: { show: false },
          labelLine: { show: false },
        },
        data: aggregate(),
      },
    ],
  }
  chart.setOption(option, true)
}

onMounted(render)
watch(() => props.points, render, { deep: true })
onBeforeUnmount(disposeChart)
</script>

<style scoped lang="scss">
.expense-pie-chart {
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
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ev-text-muted, #90a4bb);
  font-size: 13px;
}
</style>
