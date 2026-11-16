<template>
  <div class="activity-timeline">
    <div class="page-header">
      <div class="header-text">
        <h2>活动时间轴</h2>
        <p>
          数据范围：2024 年至今，来自官网「停机更新公告」正文【限时热点】。
          活动名保持官网原文不做归类；默认展示出现次数最多的 5 个活动，可多选查看。
        </p>
      </div>
      <div class="header-actions">
        <span v-if="syncedText" class="sync-time">最后同步：{{ syncedText }}</span>
        <el-button v-if="userStore.isAdmin" type="primary" :loading="syncing" @click="onSync">
          同步最新
        </el-button>
      </div>
    </div>

    <div v-if="syncing" class="sync-bar">
      <span class="sync-phase">{{ syncStatus?.phase || '同步中…' }}</span>
      <el-progress class="sync-progress" :percentage="progressPercent" :show-text="false" :stroke-width="6" />
      <span class="sync-count">{{ syncStatus?.done || 0 }} / {{ syncStatus?.total || 0 }}</span>
    </div>

    <div class="toolbar">
      <el-radio-group v-model="viewMode" class="view-switch">
        <el-radio-button value="list">明细列表</el-radio-button>
        <el-radio-button value="timeline">时间轴</el-radio-button>
      </el-radio-group>
      <span class="toolbar-hint">
        {{ selectedNames.length === 0 ? '全部活动' : `已选 ${selectedNames.length} 个` }}
        · {{ filteredItems.length }} 条记录
      </span>
    </div>

    <!-- 明细列表：下拉多选 + 搜索框 -->
    <div v-if="viewMode === 'list'" class="filter-bar">
      <el-select
        v-model="selectedNames"
        multiple
        filterable
        collapse-tags
        collapse-tags-tooltip
        clearable
        placeholder="选择活动（按出现次数从多到少）"
        class="filter-names"
      >
        <el-option
          v-for="item in pickerOptions"
          :key="item.name"
          :label="`${item.name}（${item.count} 次）`"
          :value="item.name"
        />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索活动名，过滤列表…"
        class="filter-search"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button @click="resetFilters">清空</el-button>
    </div>

    <!-- 时间轴：多列勾选面板 + 搜索框 -->
    <section v-else class="picker-panel">
      <div class="picker-head">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索活动名…"
          class="picker-search"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button size="small" @click="selectTop">热门 5 个</el-button>
        <el-button size="small" @click="resetFilters">清空</el-button>
        <span class="picker-summary">
          共 {{ nameCounts.length }} 个活动 · 当前显示 {{ pickerOptions.length }} 个
        </span>
      </div>
      <div class="picker-grid">
        <label
          v-for="item in pickerOptions"
          :key="item.name"
          class="picker-item"
          :class="{ checked: isPicked(item.name) }"
          :title="`${item.name}（${item.count} 次）`"
        >
          <input
            type="checkbox"
            :checked="isPicked(item.name)"
            @change="toggleName(item.name)"
          />
          <span class="dot" :style="{ background: colorOf(item.name) }" />
          <span class="picker-name">{{ item.name }}</span>
          <span class="picker-count">{{ item.count }}</span>
        </label>
        <span v-if="pickerOptions.length === 0" class="picker-empty">没有匹配的活动名</span>
      </div>
    </section>

    <div v-loading="loading" class="timeline-wrap">
      <el-empty
        v-if="!loading && filteredItems.length === 0"
        description="暂无活动数据，点右上角「同步最新」抓取 2024 年至今的官网公告"
      />

      <template v-if="viewMode === 'timeline'">
        <section v-for="block in yearBlocks" :key="block.year" class="year-panel">
          <div class="year-head">
            <h3>{{ block.year }} 年</h3>
            <span>{{ block.rows.length }} 个活动 · {{ block.segmentCount }} 条区间</span>
          </div>

          <div class="tl-scroll">
            <div class="tl-inner">
              <div class="tl-months">
                <span class="tl-month-spacer" />
                <div class="tl-month-track">
                  <span
                    v-for="m in 12"
                    :key="m"
                    class="tl-month"
                    :style="{ left: ((m - 1) / 12) * 100 + '%' }"
                  >{{ m }}月</span>
                  <span
                    v-if="block.year === currentYear"
                    class="tl-today"
                    :style="{ left: todayPercent + '%' }"
                  >今天</span>
                </div>
              </div>

              <div v-for="row in block.rows" :key="row.name" class="tl-row">
                <div class="tl-row-label" :title="row.name">
                  <span class="dot" :style="{ background: row.color }" />
                  <span class="row-name">{{ row.name }}</span>
                  <span class="row-count">{{ row.count }}</span>
                </div>
                <div class="tl-row-track">
                  <span
                    v-for="m in 12"
                    :key="m"
                    class="tl-grid-line"
                    :style="{ left: ((m - 1) / 12) * 100 + '%' }"
                  />
                  <span
                    v-if="block.year === currentYear"
                    class="tl-today-line"
                    :style="{ left: todayPercent + '%' }"
                  />
                  <span
                    v-for="(bar, index) in row.bars"
                    :key="index"
                    class="tl-bar"
                    :style="{ left: bar.left + '%', width: bar.width + '%', background: row.color }"
                    :title="bar.tip"
                  />
                </div>
              </div>
            </div>
          </div>
        </section>

        <p v-if="yearBlocks.length > 0" class="timeline-foot">
          横条为一次活动的开始→结束区间（跨年会在相邻年份按年截断），鼠标悬停可看到完整时间与来源公告。
        </p>
      </template>

      <template v-else>
        <section v-for="group in listGroups" :key="group.year" class="year-group">
          <div class="year-head clickable" @click="toggleYear(group.year)">
            <h3>{{ group.year }} 年</h3>
            <span>{{ group.items.length }} 条活动记录</span>
            <el-icon class="year-arrow" :class="{ collapsed: collapsedYears.has(group.year) }">
              <ArrowDown />
            </el-icon>
          </div>
          <div v-show="!collapsedYears.has(group.year)" class="year-body">
            <div v-for="item in group.items" :key="item.id" class="timeline-item">
              <div class="date-col">{{ dateRange(item) }}</div>
              <div class="dot-col"><span class="dot" :style="{ background: colorOf(item.name) }" /></div>
              <div class="card">
                <div class="card-head">
                  <span class="name">{{ item.name }}</span>
                  <span v-if="item.endTime === null" class="loose">未写结束时间</span>
                </div>
                <div v-if="item.sourceTitle" class="card-foot">
                  <span class="source">{{ item.sourceTitle }}</span>
                  <a v-if="item.sourceUrl" :href="item.sourceUrl" target="_blank" rel="noopener" class="link">
                    查看公告 ↗
                  </a>
                </div>
              </div>
            </div>
          </div>
        </section>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getActivitySyncStatus, getActivityTimeline, startActivitySync } from '@/api/hyolActivity'
import type {
  HyolActivityItem,
  HyolActivityNameCount,
  HyolActivitySyncStatus,
} from '@/types/hyolActivity'

interface Bar {
  left: number
  width: number
  tip: string
}

interface Row {
  name: string
  count: number
  color: string
  bars: Bar[]
}

interface YearBlock {
  year: number
  rows: Row[]
  segmentCount: number
}

const userStore = useUserStore()

const items = ref<HyolActivityItem[]>([])
const nameCounts = ref<HyolActivityNameCount[]>([])
const syncedAt = ref<string | null>(null)
const loading = ref(false)

const selectedNames = ref<string[]>([])
const keyword = ref('')
const viewMode = ref<'list' | 'timeline'>('list')
const collapsedYears = ref<Set<number>>(new Set())

const syncing = ref(false)
const syncStatus = ref<HyolActivitySyncStatus | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null
let autoSelected = false

const nowDate = new Date()
const currentYear = nowDate.getFullYear()

const parseDate = (value?: string | null): Date | null => {
  if (!value) return null
  const parsed = new Date(value.replace(' ', 'T'))
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

const yearOf = (item: HyolActivityItem): number => {
  const raw = item.startTime || item.announceDate || ''
  const year = Number(raw.slice(0, 4))
  return Number.isFinite(year) && year > 0 ? year : 0
}

const fmtMoment = (value?: string | null): string =>
  value ? value.replace('T', ' ').slice(0, 16) : '未写结束时间'

/** 活动名 → 稳定颜色 */
const colorOf = (name: string): string => {
  let hash = 0
  for (let i = 0; i < name.length; i += 1) {
    hash = (hash * 31 + name.charCodeAt(i)) % 360
  }
  return `hsl(${hash} 70% 56%)`
}

const countByName = computed(() => {
  const map = new Map<string, number>()
  for (const entry of nameCounts.value) {
    map.set(entry.name, entry.count)
  }
  return map
})

/** 搜索框过滤后的活动名（两种视图共用） */
const pickerOptions = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) return nameCounts.value
  return nameCounts.value.filter((entry) => entry.name.includes(kw))
})

const isPicked = (name: string): boolean => selectedNames.value.includes(name)

const toggleName = (name: string) => {
  const next = new Set(selectedNames.value)
  if (next.has(name)) {
    next.delete(name)
  } else {
    next.add(name)
  }
  selectedNames.value = Array.from(next)
}

const selectTop = () => {
  selectedNames.value = pickerOptions.value.slice(0, 5).map((entry) => entry.name)
}

const filteredItems = computed(() => {
  const kw = keyword.value.trim()
  const picked = new Set(selectedNames.value)
  return items.value.filter((item) => {
    if (picked.size > 0 && !picked.has(item.name)) return false
    if (kw && !item.name.includes(kw)) return false
    return true
  })
})

const buildBar = (item: HyolActivityItem, year: number): Bar | null => {
  const yearStart = new Date(year, 0, 1).getTime()
  const yearEnd = new Date(year + 1, 0, 1).getTime()
  const start = parseDate(item.startTime) ?? parseDate(item.announceDate)
  if (!start) return null
  const end = parseDate(item.endTime) ?? new Date(start.getTime() + 86400000)
  const from = Math.max(start.getTime(), yearStart)
  const to = Math.min(end.getTime(), yearEnd)
  if (to <= from) return null
  const span = yearEnd - yearStart
  const left = ((from - yearStart) / span) * 100
  const width = Math.max(((to - from) / span) * 100, 0.5)
  const range = `${fmtMoment(item.startTime ?? item.announceDate)} ~ ${fmtMoment(item.endTime)}`
  const tip = `${item.name}\n${range}\n${item.sourceTitle || ''}`
  return { left, width, tip }
}

/** 按年份分组、年份内按活动名成行（行按全局出现次数从多到少） */
const yearBlocks = computed<YearBlock[]>(() => {
  const byYear = new Map<number, Map<string, HyolActivityItem[]>>()
  for (const item of filteredItems.value) {
    const year = yearOf(item)
    if (!year) continue
    let nameMap = byYear.get(year)
    if (!nameMap) {
      nameMap = new Map()
      byYear.set(year, nameMap)
    }
    const list = nameMap.get(item.name)
    if (list) {
      list.push(item)
    } else {
      nameMap.set(item.name, [item])
    }
  }

  const blocks: YearBlock[] = []
  const years = Array.from(byYear.keys()).sort((a, b) => b - a)
  for (const year of years) {
    const nameMap = byYear.get(year)
    if (!nameMap) continue
    const rows: Row[] = []
    for (const [name, list] of Array.from(nameMap.entries())) {
      const bars: Bar[] = []
      for (const item of list) {
        const bar = buildBar(item, year)
        if (bar) bars.push(bar)
      }
      bars.sort((a, b) => a.left - b.left)
      rows.push({ name, count: list.length, color: colorOf(name), bars })
    }
    rows.sort((a, b) => {
      const left = countByName.value.get(a.name) ?? 0
      const right = countByName.value.get(b.name) ?? 0
      return right - left || a.name.localeCompare(b.name)
    })
    let segmentCount = 0
    for (const row of rows) segmentCount += row.bars.length
    blocks.push({ year, rows, segmentCount })
  }
  return blocks
})

/** 明细列表：按年分组，年内按时间倒序 */
const listGroups = computed(() => {
  const byYear = new Map<number, HyolActivityItem[]>()
  for (const item of filteredItems.value) {
    const year = yearOf(item)
    if (!year) continue
    const list = byYear.get(year)
    if (list) {
      list.push(item)
    } else {
      byYear.set(year, [item])
    }
  }
  return Array.from(byYear.entries())
    .sort((a, b) => b[0] - a[0])
    .map(([year, list]) => ({ year, items: list }))
})

const todayPercent = computed(() => {
  const start = new Date(currentYear, 0, 1).getTime()
  const end = new Date(currentYear + 1, 0, 1).getTime()
  return ((nowDate.getTime() - start) / (end - start)) * 100
})

const syncedText = computed(() =>
  syncedAt.value ? syncedAt.value.replace('T', ' ').slice(0, 16) : ''
)

const progressPercent = computed(() => {
  const total = syncStatus.value?.total || 0
  const done = syncStatus.value?.done || 0
  if (total <= 0) return syncing.value ? 5 : 0
  return Math.min(100, Math.round((done / total) * 100))
})

const dateRange = (item: HyolActivityItem): string => {
  const start = item.startTime ? item.startTime.slice(5, 10) : item.announceDate?.slice(5, 10) || ''
  const end = item.endTime ? item.endTime.slice(5, 10) : ''
  return end ? `${start} ~ ${end}` : start
}

const toggleYear = (year: number) => {
  const next = new Set(collapsedYears.value)
  if (next.has(year)) {
    next.delete(year)
  } else {
    next.add(year)
  }
  collapsedYears.value = next
}

const load = async () => {
  loading.value = true
  try {
    const res = await getActivityTimeline()
    items.value = res.data?.items || []
    nameCounts.value = res.data?.names || []
    syncedAt.value = res.data?.syncedAt || null
    // 首次加载默认选中出现次数最多的 5 个活动，避免一屏几十行
    if (!autoSelected && selectedNames.value.length === 0 && nameCounts.value.length > 0) {
      selectedNames.value = nameCounts.value.slice(0, 5).map((entry) => entry.name)
      autoSelected = true
    }
  } catch (e) {
    ElMessage.error('加载活动时间轴失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  selectedNames.value = []
  keyword.value = ''
}

const stopPoll = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const pollStatus = () => {
  stopPoll()
  pollTimer = setInterval(async () => {
    try {
      const res = await getActivitySyncStatus()
      syncStatus.value = res.data || null
      if (!res.data?.running) {
        stopPoll()
        syncing.value = false
        ElMessage.success(res.data?.message || '同步完成')
        await load()
      }
    } catch (e) {
      stopPoll()
      syncing.value = false
    }
  }, 1500)
}

const onSync = async () => {
  syncing.value = true
  try {
    const res = await startActivitySync(false)
    syncStatus.value = res.data || null
    pollStatus()
  } catch (e) {
    syncing.value = false
    ElMessage.error('同步失败，请稍后再试')
  }
}

onMounted(async () => {
  await load()
  try {
    const res = await getActivitySyncStatus()
    syncStatus.value = res.data || null
    if (res.data?.running) {
      syncing.value = true
      pollStatus()
    }
  } catch (e) {
    // 状态接口失败不影响浏览
  }
})

onBeforeUnmount(stopPoll)
</script>

<style scoped>
.activity-timeline {
  padding: 20px 24px 40px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.header-text h2 {
  margin: 0 0 6px;
  font-size: 20px;
  color: #12304f;
}

.header-text p {
  margin: 0;
  max-width: 760px;
  font-size: 13px;
  line-height: 1.6;
  color: #8aa3bb;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sync-time {
  font-size: 12px;
  color: #8aa3bb;
}

.sync-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  margin-bottom: 14px;
  background: rgba(47, 124, 246, 0.06);
  border: 1px solid rgba(47, 124, 246, 0.15);
  border-radius: 8px;
  font-size: 13px;
  color: #2f7cf6;
}

.sync-progress {
  flex: 1;
}

.sync-count {
  font-variant-numeric: tabular-nums;
  color: #8aa3bb;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.toolbar-hint {
  font-size: 12px;
  color: #8aa3bb;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  margin-bottom: 18px;
  background: #fff;
  border: 1px solid rgba(47, 124, 246, 0.12);
  border-radius: 10px;
}

.filter-names {
  width: 420px;
  max-width: 100%;
}

.filter-search {
  width: 240px;
  max-width: 100%;
}

.picker-panel {
  padding: 12px 14px;
  margin-bottom: 18px;
  background: #fff;
  border: 1px solid rgba(47, 124, 246, 0.12);
  border-radius: 10px;
}

.picker-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.picker-search {
  width: 260px;
  max-width: 100%;
}

.picker-summary {
  margin-left: auto;
  font-size: 12px;
  color: #8aa3bb;
}

.picker-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 2px 10px;
  max-height: 240px;
  overflow-y: auto;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid rgba(47, 124, 246, 0.08);
}

.picker-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  border-radius: 6px;
  font-size: 12px;
  color: #12304f;
  cursor: pointer;
}

.picker-item:hover {
  background: rgba(47, 124, 246, 0.06);
}

.picker-item.checked {
  background: rgba(47, 124, 246, 0.1);
}

.picker-item input[type='checkbox'] {
  margin: 0;
  accent-color: #2f7cf6;
  flex: 0 0 auto;
}

.picker-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picker-count {
  flex: 0 0 34px;
  text-align: right;
  font-size: 11px;
  color: #8aa3bb;
  font-variant-numeric: tabular-nums;
}

.picker-empty {
  grid-column: 1 / -1;
  padding: 12px;
  font-size: 12px;
  color: #8aa3bb;
  text-align: center;
}

.timeline-wrap {
  min-height: 200px;
}

.year-panel {
  margin-bottom: 18px;
  padding: 14px 16px 18px;
  background: #fff;
  border: 1px solid rgba(47, 124, 246, 0.12);
  border-radius: 10px;
}

.year-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}

.year-head.clickable {
  cursor: pointer;
  user-select: none;
}

.year-head h3 {
  margin: 0;
  font-size: 17px;
  color: #12304f;
}

.year-head span {
  font-size: 12px;
  color: #8aa3bb;
}

.tl-scroll {
  overflow-x: auto;
}

.tl-inner {
  min-width: 720px;
}

.tl-months {
  display: flex;
  align-items: flex-end;
  height: 22px;
}

.tl-month-spacer {
  flex: 0 0 150px;
}

.tl-month-track {
  position: relative;
  flex: 1;
  height: 100%;
}

.tl-month {
  position: absolute;
  bottom: 2px;
  font-size: 11px;
  color: #8aa3bb;
  transform: translateX(2px);
}

.tl-today {
  position: absolute;
  bottom: 2px;
  transform: translateX(-50%);
  font-size: 11px;
  color: #e25555;
  white-space: nowrap;
}

.tl-row {
  display: flex;
  align-items: center;
}

.tl-row-label {
  flex: 0 0 150px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding-right: 10px;
  font-size: 12px;
  color: #12304f;
}

.row-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-count {
  margin-left: auto;
  font-size: 11px;
  color: #8aa3bb;
  font-variant-numeric: tabular-nums;
}

.tl-row-track {
  position: relative;
  flex: 1;
  height: 26px;
}

.tl-grid-line {
  position: absolute;
  top: 3px;
  bottom: 3px;
  width: 1px;
  background: rgba(47, 124, 246, 0.1);
}

.tl-today-line {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  border-left: 1px dashed rgba(226, 85, 85, 0.6);
}

.tl-bar {
  position: absolute;
  top: 6px;
  height: 14px;
  min-width: 3px;
  border-radius: 4px;
  opacity: 0.85;
  cursor: default;
  transition: opacity 0.15s;
}

.tl-bar:hover {
  opacity: 1;
  box-shadow: 0 0 0 2px rgba(18, 48, 79, 0.12);
}

.timeline-foot {
  margin: 0;
  font-size: 12px;
  color: #8aa3bb;
}

.year-group {
  margin-bottom: 20px;
}

.year-arrow {
  margin-left: auto;
  color: #8aa3bb;
  transition: transform 0.2s;
}

.year-arrow.collapsed {
  transform: rotate(-90deg);
}

.year-body {
  padding: 12px 0 0 8px;
}

.timeline-item {
  display: grid;
  grid-template-columns: 96px 20px minmax(0, 1fr);
  align-items: stretch;
  gap: 8px;
}

.date-col {
  padding-top: 12px;
  font-size: 12px;
  color: #8aa3bb;
  text-align: right;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.dot-col {
  position: relative;
  display: flex;
  justify-content: center;
}

.dot-col::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  background: rgba(47, 124, 246, 0.16);
}

.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex: 0 0 auto;
}

.dot-col .dot {
  position: relative;
  margin-top: 16px;
}

.card {
  margin-bottom: 10px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid rgba(47, 124, 246, 0.12);
  border-radius: 8px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.card:hover {
  border-color: rgba(47, 124, 246, 0.4);
  box-shadow: 0 4px 14px rgba(47, 124, 246, 0.1);
}

.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.name {
  font-size: 14px;
  font-weight: 600;
  color: #12304f;
}

.loose {
  padding: 1px 8px;
  font-size: 11px;
  color: #d08b2a;
  background: rgba(208, 139, 42, 0.12);
  border-radius: 10px;
}

.card-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
  font-size: 12px;
  color: #8aa3bb;
}

.source {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.link {
  margin-left: auto;
  color: #2f7cf6;
  text-decoration: none;
  white-space: nowrap;
}

.link:hover {
  text-decoration: underline;
}

@media (max-width: 900px) {
  .activity-timeline {
    padding: 16px;
  }

  .filter-names,
  .filter-search,
  .picker-search {
    width: 100%;
  }

  .picker-grid {
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  }

  .timeline-item {
    grid-template-columns: 78px 16px minmax(0, 1fr);
  }
}
</style>
