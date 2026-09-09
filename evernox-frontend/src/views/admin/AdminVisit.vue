<template>
  <div class="admin-visit">
    <div class="overview-grid">
      <div class="stat-card">
        <div class="stat-icon blue"><el-icon><UserFilled /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ overview.todayActiveUsers }}</div>
          <div class="stat-label">今日活跃用户</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon cyan"><el-icon><View /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ overview.todayVisits }}</div>
          <div class="stat-label">今日访问量</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon green"><el-icon><Position /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ overview.todayLogins }}</div>
          <div class="stat-label">今日登录次数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon orange"><el-icon><TrendCharts /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ overview.weekActiveUsers }}</div>
          <div class="stat-label">近 7 天活跃用户</div>
        </div>
      </div>
    </div>

    <div class="panel chart-panel">
      <div class="panel-head">
        <span class="panel-title">访问趋势</span>
        <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
          <el-radio-button :value="7">近7天</el-radio-button>
          <el-radio-button :value="30">近30天</el-radio-button>
          <el-radio-button :value="90">近90天</el-radio-button>
        </el-radio-group>
      </div>
      <div ref="chartEl" class="chart-body"></div>
    </div>

    <div class="panel">
      <el-tabs v-model="activeTable">
        <el-tab-pane label="最近访问" name="recent">
          <div class="toolbar">
            <el-select v-model="filterType" placeholder="事件类型" clearable style="width: 140px" @change="reloadRecent">
              <el-option label="登录" value="LOGIN" />
              <el-option label="访问" value="VISIT" />
            </el-select>
            <el-input
              v-model="filterUsername"
              placeholder="搜索用户名"
              clearable
              style="width: 200px"
              @input="onUsernameInput"
              @clear="reloadRecent"
            />
          </div>
          <el-table :data="recentList" border stripe>
            <el-table-column prop="username" label="用户名" min-width="110" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">
                <el-tag :type="row.type === 'LOGIN' ? 'success' : 'info'" size="small">
                  {{ row.type === 'LOGIN' ? '登录' : '访问' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ip" label="IP" min-width="130" />
            <el-table-column prop="path" label="路径" min-width="140" show-overflow-tooltip />
            <el-table-column label="时间" min-width="160">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="recentPage"
              v-model:page-size="recentSize"
              :page-sizes="[20, 50, 100]"
              :total="recentTotal"
              layout="total, sizes, prev, pager, next"
              @current-change="loadRecent"
              @size-change="reloadRecent"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="活跃用户" name="rank">
          <el-table :data="rankList" border stripe>
            <el-table-column type="index" label="#" width="60" />
            <el-table-column prop="username" label="用户名" min-width="140" />
            <el-table-column prop="visitCount" label="访问次数" min-width="110" sortable />
            <el-table-column label="最近访问" min-width="170">
              <template #default="{ row }">{{ formatTime(row.lastVisitAt) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getVisitOverview, getVisitRank, getVisitRecent, getVisitTrend } from '@/api/visit'
import type {
  VisitLogItem,
  VisitOverview,
  VisitTrendItem,
  VisitUserRankItem,
} from '@/api/visit'

echarts.use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const overview = ref<VisitOverview>({
  todayActiveUsers: 0,
  todayVisits: 0,
  todayLogins: 0,
  weekActiveUsers: 0,
})

const trendDays = ref(7)
const trendList = ref<VisitTrendItem[]>([])
const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const activeTable = ref<'recent' | 'rank'>('recent')
const recentList = ref<VisitLogItem[]>([])
const recentTotal = ref(0)
const recentPage = ref(1)
const recentSize = ref(20)
const filterType = ref('')
const filterUsername = ref('')
let usernameTimer: ReturnType<typeof setTimeout> | null = null

const rankList = ref<VisitUserRankItem[]>([])

const loadOverview = async () => {
  const res = await getVisitOverview()
  overview.value = res.data ?? overview.value
}

const loadTrend = async () => {
  const res = await getVisitTrend(trendDays.value)
  trendList.value = res.data ?? []
  renderChart()
}

const loadRecent = async () => {
  const res = await getVisitRecent({
    page: recentPage.value,
    size: recentSize.value,
    type: filterType.value || undefined,
    username: filterUsername.value || undefined,
  })
  recentList.value = res.data?.records ?? []
  recentTotal.value = res.data?.total ?? 0
}

const reloadRecent = () => {
  recentPage.value = 1
  loadRecent()
}

const onUsernameInput = () => {
  if (usernameTimer) clearTimeout(usernameTimer)
  usernameTimer = setTimeout(reloadRecent, 300)
}

const loadRank = async () => {
  const res = await getVisitRank(20)
  rankList.value = res.data ?? []
}

const renderChart = () => {
  if (!chart) return
  const dates = trendList.value.map((t) => t.date)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 6, left: 'center', itemGap: 16, data: ['访问量', '活跃用户', '登录次数'] },
    grid: { left: 50, right: 24, top: 48, bottom: 40, containLabel: true },
    xAxis: { type: 'category', data: dates, boundaryGap: false, axisLabel: { hideOverlap: true } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '访问量', type: 'line', smooth: true, data: trendList.value.map((t) => t.visits) },
      { name: '活跃用户', type: 'line', smooth: true, data: trendList.value.map((t) => t.activeUsers) },
      { name: '登录次数', type: 'line', smooth: true, data: trendList.value.map((t) => t.logins) },
    ],
  })
}

const formatTime = (v?: string) => {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 16)
}

onMounted(() => {
  chart = echarts.init(chartEl.value!)
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartEl.value!)

  loadOverview()
  loadTrend()
  loadRecent()
  loadRank()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  if (usernameTimer) clearTimeout(usernameTimer)
})
</script>

<style scoped lang="scss">
.admin-visit {
  .overview-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
    margin-bottom: 16px;

    @media (max-width: 900px) {
      grid-template-columns: repeat(2, 1fr);
    }
  }

  .stat-card {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 18px;
    border-radius: 14px;
    background: var(--el-bg-color);
    border: 1px solid var(--ev-border-subtle);
    box-shadow: var(--ev-shadow-xs);

    .stat-icon {
      width: 46px;
      height: 46px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      color: #fff;

      &.blue { background: linear-gradient(135deg, #2f7cf6, #4fc3e8); }
      &.cyan { background: linear-gradient(135deg, #4fc3e8, #7fd8e8); }
      &.green { background: linear-gradient(135deg, #34c9a3, #7fd8e8); }
      &.orange { background: linear-gradient(135deg, #f5b942, #f2637f); }
    }

    .stat-body {
      .stat-value {
        font-size: 26px;
        font-weight: 800;
        color: var(--ev-text-primary);
        line-height: 1.2;
      }

      .stat-label {
        font-size: 13px;
        color: var(--ev-text-muted);
      }
    }
  }

  .panel {
    padding: 16px;
    border-radius: 14px;
    background: var(--el-bg-color);
    border: 1px solid var(--ev-border-subtle);
    margin-bottom: 16px;
  }

  .chart-panel {
    .panel-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }

    .panel-title {
      font-size: 15px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }

    .chart-body {
      height: 320px;
    }
  }

  .toolbar {
    display: flex;
    gap: 12px;
    margin-bottom: 12px;
  }

  .pager {
    display: flex;
    justify-content: flex-end;
    margin-top: 14px;
  }
}
</style>
