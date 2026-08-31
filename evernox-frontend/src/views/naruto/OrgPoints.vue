<template>
  <div class="org-points">
    <div v-if="isAdmin || !!joinedMembership" class="toolbar">
      <span class="toolbar-label">组织：</span>
      <el-select v-model="selectedOrgId" placeholder="选择组织" style="width: 200px">
        <el-option v-for="o in viewableOrganizations" :key="o.id" :label="o.name" :value="o.id" />
      </el-select>
      <span class="toolbar-label">周（周日）：</span>
      <el-select v-model="selectedWeek" placeholder="选择周" clearable style="width: 180px" @change="loadRecords">
        <el-option v-for="w in weeks" :key="w" :label="w" :value="w" />
      </el-select>
      <el-button v-if="!isAdmin && joinedMembership" type="danger" plain size="small" @click="confirmLeave">
        退出组织
      </el-button>
      <el-input v-model="recordKeyword" placeholder="搜索玩家名" clearable style="width: 180px" />
    </div>

    <div v-if="!isAdmin && !joinedMembership" class="membership-gate">
      <el-alert
        v-if="pendingMembership"
        type="info"
        :closable="false"
        :title="`你已申请加入「${pendingMembership.organizationName}」，请等待管理员审批。`"
      />
      <el-alert
        v-else-if="rejectedMembership"
        type="warning"
        :closable="false"
        :title="`你申请加入「${rejectedMembership.organizationName}」已被拒绝。`"
      />
      <div v-if="!pendingMembership" class="membership-gate-actions">
        <el-empty :description="rejectedMembership ? '申请被拒绝' : '你尚未加入任何组织'" :image-size="80" />
        <el-button type="primary" @click="openApply">{{ rejectedMembership ? '重新申请' : '申请加入组织' }}</el-button>
      </div>
    </div>

    <div v-if="(isAdmin || !!joinedMembership) && records.length > 0" class="chart-card" @click="chartDialogVisible = true">
      <div ref="chartEl" class="chart-body"></div>
      <div class="chart-expand-hint">查看完整图表（共 {{ records.length }} 人）</div>
    </div>

    <el-table v-if="isAdmin || !!joinedMembership" :data="filteredRecords" border stripe :default-sort="{ prop: 'totalPoints', order: 'descending' }">
      <el-table-column prop="memberName" label="玩家名" min-width="90" fixed="left" />
      <el-table-column prop="position" label="职务" min-width="90" />
      <el-table-column prop="ninjaBattleCount" v-if="pointsConfig?.ninjaBattleVisible !== 0" label="忍战次数" min-width="90" sortable />
      <el-table-column prop="totalPower" v-if="pointsConfig?.totalPowerVisible !== 0" label="总战力" min-width="100" sortable />
      <el-table-column prop="powerIncrease" v-if="pointsConfig?.powerIncreaseVisible !== 0" label="战力增幅" min-width="90" sortable />
      <el-table-column prop="copperContribution" v-if="pointsConfig?.copperVisible !== 0" label="铜币" min-width="80" sortable />
      <el-table-column prop="beastSacrifice" v-if="pointsConfig?.beastVisible !== 0" label="通灵兽" min-width="80" sortable />
      <el-table-column prop="renegadeCount" v-if="pointsConfig?.renegadeVisible !== 0" label="叛忍" min-width="70" sortable />
      <el-table-column v-if="pointsConfig?.renegadeLeaderVisible !== 0" label="车头" min-width="70">
        <template #default="{ row }">{{ row.isRenegadeLeader === 1 ? '是' : '' }}</template>
      </el-table-column>
      <el-table-column label="上周剩余" min-width="100" sortable prop="lastWeekPoints">
        <template #default="{ row }">{{ fmt(row.lastWeekPoints) }}</template>
      </el-table-column>
      <el-table-column label="本周积分" min-width="100" sortable prop="thisWeekPoints">
        <template #default="{ row }">{{ fmt(row.thisWeekPoints) }}</template>
      </el-table-column>
      <el-table-column label="总积分" min-width="110" sortable prop="totalPoints">
        <template #default="{ row }">
          <span class="total-points">{{ fmt(row.totalPoints) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="rewardPackageName" label="奖励礼包" min-width="130">
        <template #default="{ row }">{{ row.rewardPackageName || '-' }}</template>
      </el-table-column>
      <el-table-column label="扣除后积分" min-width="110" sortable prop="pointsAfterDeduction">
        <template #default="{ row }">{{ fmt(row.pointsAfterDeduction) }}</template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && records.length === 0 && (isAdmin || !!joinedMembership)" description="暂无该周数据" />

    <el-dialog
      v-model="chartDialogVisible"
      title="组织积分图表（全部成员）"
      fullscreen
      :append-to-body="true"
    >
      <div class="full-chart-scroll">
        <div ref="fullChartEl" class="full-chart-body" :style="{ height: fullChartHeight }"></div>
      </div>
    </el-dialog>

    <el-dialog v-model="applyDialogVisible" title="申请加入组织" width="420px">
      <el-form label-width="80px">
        <el-form-item label="选择组织" required>
          <el-select v-model="applyOrgId" placeholder="请选择要加入的组织" style="width: 100%">
            <el-option v-for="o in applyableOrganizations" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useUserStore } from '@/stores/user'
import {
  getPublicOrgOrganizations,
  getPublicOrgWeeks,
  getPublicOrgRecords,
  getPublicOrgPointsConfig,
  getMyOrgMemberships,
  applyOrgMembership,
  leaveOrgMembership,
} from '@/api/orgPoints'
import type { OrgMembership, OrgOrganization, OrgPointsConfig, OrgWeekRecord } from '@/types/org'

echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const organizations = ref<OrgOrganization[]>([])
const selectedOrgId = ref<number | null>(null)
const weeks = ref<string[]>([])
const selectedWeek = ref('')
const records = ref<OrgWeekRecord[]>([])
const recordKeyword = ref('')
const filteredRecords = computed(() => {
  const kw = recordKeyword.value.trim().toLowerCase()
  if (!kw) return records.value
  return records.value.filter((r) => r.memberName.toLowerCase().includes(kw))
})
const loading = ref(false)
const pointsConfig = ref<OrgPointsConfig | null>(null)

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)

const memberships = ref<OrgMembership[]>([])
const applyDialogVisible = ref(false)
const applyOrgId = ref<number | null>(null)
const applying = ref(false)

const joinedMembership = computed(() => memberships.value.find((m) => m.status === 1) ?? null)
const pendingMembership = computed(() => memberships.value.find((m) => m.status === 0) ?? null)
const rejectedMembership = computed(
  () =>
    [...memberships.value]
      .filter((m) => m.status === 2)
      .sort((a, b) => b.id - a.id)[0] ?? null
)
const joinedOrgIds = computed(
  () => new Set(memberships.value.filter((m) => m.status === 1).map((m) => m.organizationId))
)
const viewableOrganizations = computed(() =>
  isAdmin.value ? organizations.value : organizations.value.filter((o) => joinedOrgIds.value.has(o.id))
)
const applyableOrganizations = computed(() =>
  organizations.value.filter((o) => {
    const m = memberships.value.find((x) => x.organizationId === o.id)
    return !m || m.status === 2
  })
)

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const fmt = (v: number | null | undefined): string => {
  if (v === null || v === undefined) return '-'
  return Number(v).toFixed(5)
}

const loadOrganizations = async () => {
  const res = await getPublicOrgOrganizations()
  organizations.value = res.data
}

const loadMemberships = async () => {
  const res = await getMyOrgMemberships()
  memberships.value = res.data
}

const selectDefaultOrg = () => {
  const list = viewableOrganizations.value
  if (list.length === 0) {
    selectedOrgId.value = null
    return
  }
  if (!selectedOrgId.value || !list.some((o) => o.id === selectedOrgId.value)) {
    selectedOrgId.value = list[0].id
  }
}

const loadWeeks = async () => {
  if (!selectedOrgId.value) {
    weeks.value = []
    selectedWeek.value = ''
    records.value = []
    return
  }
  const res = await getPublicOrgWeeks(selectedOrgId.value)
  weeks.value = res.data
  selectedWeek.value = weeks.value.length > 0 ? weeks.value[0] : ''
}

const loadRecords = async () => {
  if (!selectedOrgId.value || !selectedWeek.value) {
    records.value = []
    return
  }
  loading.value = true
  try {
    const res = await getPublicOrgRecords(selectedOrgId.value, selectedWeek.value)
    records.value = res.data
  } finally {
    loading.value = false
  }
}

const loadConfig = async () => {
  if (!selectedOrgId.value) {
    pointsConfig.value = null
    return
  }
  const res = await getPublicOrgPointsConfig(selectedOrgId.value)
  pointsConfig.value = res.data
}

const openApply = () => {
  applyOrgId.value = null
  applyDialogVisible.value = true
}

const submitApply = async () => {
  if (!applyOrgId.value) {
    ElMessage.warning('请选择要加入的组织')
    return
  }
  applying.value = true
  try {
    await applyOrgMembership(applyOrgId.value)
    ElMessage.success('申请已提交，请等待管理员审批')
    applyDialogVisible.value = false
    await loadMemberships()
  } finally {
    applying.value = false
  }
}

const confirmLeave = async () => {
  if (!joinedMembership.value) return
  await ElMessageBox.confirm(
    `确定退出组织「${joinedMembership.value.organizationName}」？`,
    '提示',
    { type: 'warning' }
  )
  await leaveOrgMembership(joinedMembership.value.organizationId)
  ElMessage.success('已退出组织')
  await loadMemberships()
  selectDefaultOrg()
  await loadWeeks()
  await loadRecords()
}

const disposeChart = () => {
  resizeObserver?.disconnect()
  resizeObserver = null
  chart?.dispose()
  chart = null
}

const buildOption = (sorted: OrgWeekRecord[]): echarts.EChartsCoreOption => {
  const names = sorted.map((r) => r.memberName)
  const values = sorted.map((r) => Number(r.totalPoints) || 0)
  const packages = sorted.map((r) => {
    const n = r.rewardPackageName
    if (!n) return ''
    return n.replace('功勋礼包', '').replace(/-/g, '')
  })

  return {
    grid: { left: 8, right: 120, top: 16, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(255,255,255,0.94)',
      borderColor: 'rgba(47,124,246,0.18)',
      borderWidth: 1,
      textStyle: { color: '#12304f', fontSize: 12 },
      formatter: (params: unknown) => {
        const list = params as { name: string; value: number }[]
        if (!list || !list.length) return ''
        const item = list[0]
        return `<div style="font-weight:600;">${item.name}</div><div style="color:#2f7cf6;">总积分 ${Number(item.value).toFixed(5)}</div>`
      },
    },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(47,124,246,0.08)' } },
      axisLabel: { color: '#8aa3bb', fontSize: 11 },
    },
    yAxis: {
      type: 'category',
      data: names,
      inverse: true,
      axisLine: { lineStyle: { color: 'rgba(47,124,246,0.16)' } },
      axisTick: { show: false },
      axisLabel: { color: '#8aa3bb', fontSize: 11 },
    },
    series: [
      {
        name: '总积分',
        type: 'bar',
        data: values,
        barMaxWidth: 20,
        label: {
          show: true,
          position: 'right',
          color: '#12304f',
          fontSize: 11,
          formatter: (p: unknown) => {
            const idx = (p as { dataIndex: number }).dataIndex
            const v = Number((p as { value: number }).value) || 0
            const valStr = Number(v.toFixed(5)).toString()
            const pkg = packages[idx] || ''
            return pkg ? `${valStr} · ${pkg}` : valStr
          },
        },
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#4fc3e8' },
              { offset: 1, color: '#2f7cf6' },
            ],
          },
        },
      },
    ],
  }
}

const renderChart = () => {
  if (!chartEl.value || records.value.length === 0) {
    disposeChart()
    return
  }
  if (!chart) {
    chart = echarts.init(chartEl.value)
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(chartEl.value)
  }
  const sorted = [...records.value].sort((a, b) => (b.totalPoints ?? 0) - (a.totalPoints ?? 0))
  chart.setOption(buildOption(sorted.slice(0, 10)), true)
}

const chartDialogVisible = ref(false)
const fullChartEl = ref<HTMLDivElement | null>(null)
let fullChart: echarts.ECharts | null = null
let fullResizeObserver: ResizeObserver | null = null

const fullChartHeight = computed(() => `${Math.max(records.value.length * 36, 500)}px`)

const disposeFullChart = () => {
  fullResizeObserver?.disconnect()
  fullResizeObserver = null
  fullChart?.dispose()
  fullChart = null
}

const renderFullChart = () => {
  if (!fullChartEl.value || records.value.length === 0) {
    disposeFullChart()
    return
  }
  if (!fullChart) {
    fullChart = echarts.init(fullChartEl.value)
    fullResizeObserver = new ResizeObserver(() => fullChart?.resize())
    fullResizeObserver.observe(fullChartEl.value)
  }
  const sorted = [...records.value].sort((a, b) => (b.totalPoints ?? 0) - (a.totalPoints ?? 0))
  fullChart.setOption(buildOption(sorted), true)
}

watch(chartDialogVisible, (val) => {
  if (val) {
    nextTick(renderFullChart)
  } else {
    disposeFullChart()
  }
})

watch(selectedOrgId, async () => {
  selectedWeek.value = ''
  records.value = []
  await loadConfig()
  await loadWeeks()
  await loadRecords()
})

watch(records, renderChart)

let membershipTimer: ReturnType<typeof setInterval> | null = null

const stopMembershipPolling = () => {
  if (membershipTimer) {
    clearInterval(membershipTimer)
    membershipTimer = null
  }
}

const startMembershipPolling = () => {
  stopMembershipPolling()
  membershipTimer = setInterval(async () => {
    await loadMemberships()
    if (joinedMembership.value) {
      stopMembershipPolling()
      selectDefaultOrg()
      await loadConfig()
      await loadWeeks()
      await loadRecords()
      ElMessage.success('申请已通过，已为你加载组织数据')
    }
  }, 5000)
}

watch(
  pendingMembership,
  (val) => {
    if (val) {
      startMembershipPolling()
    } else {
      stopMembershipPolling()
    }
  },
  { immediate: true }
)

onMounted(async () => {
  await loadOrganizations()
  await loadMemberships()
  selectDefaultOrg()
  await loadConfig()
  await loadWeeks()
  await loadRecords()
})

onBeforeUnmount(() => {
  disposeChart()
  disposeFullChart()
  stopMembershipPolling()
})
</script>

<style scoped lang="scss">
.org-points {
  .toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;
    flex-wrap: wrap;

    .toolbar-label {
      font-size: 13px;
      color: var(--ev-text-secondary);
    }
  }

  .membership-gate {
    margin-bottom: 16px;

    .membership-gate-actions {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 24px 0;
    }
  }

  .chart-card {
    position: relative;
    height: 320px;
    margin-bottom: 16px;
    background: rgba(255, 255, 255, 0.6);
    border: 1px solid var(--ev-border-subtle);
    border-radius: 16px;
    padding: 12px;
    box-shadow: var(--ev-shadow-xs);
    cursor: pointer;
    transition: border-color 0.2s;

    &:hover {
      border-color: #2f7cf6;
    }

    .chart-body {
      position: absolute;
      inset: 12px;
    }

    .chart-expand-hint {
      position: absolute;
      top: 16px;
      right: 16px;
      z-index: 2;
      font-size: 12px;
      color: #2f7cf6;
      background: rgba(255, 255, 255, 0.9);
      border: 1px solid rgba(47, 124, 246, 0.24);
      border-radius: 999px;
      padding: 4px 12px;
    }
  }

  .full-chart-scroll {
    height: calc(100vh - 120px);
    overflow: auto;
  }

  .full-chart-body {
    width: 100%;
  }

  .total-points {
    font-weight: 700;
    color: var(--ev-primary);
  }

  :deep(.el-table th .cell) {
    white-space: nowrap;
  }
}
</style>
