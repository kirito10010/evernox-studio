<template>
  <div class="work-calendar-page">
    <header class="page-head">
      <div class="head-copy">
        <h2 class="page-title">工作日历</h2>
        <p class="page-sub">2026-11 至 2027-12 · 大小周自 2026-12-01 起实行</p>
      </div>
      <div class="head-actions">
        <div class="mode-switch">
          <button type="button" :class="{ active: mode === 'tri' }" @click="mode = 'tri'">
            近三月
          </button>
          <button type="button" :class="{ active: mode === 'year' }" @click="mode = 'year'">
            全部月份
          </button>
        </div>
        <a class="notice-link" :href="NOTICE_URL" target="_blank" rel="noreferrer">原文</a>
      </div>
    </header>

    <div class="body">
      <section class="cal-panel">
        <div class="sub-bar">
          <ul class="legend">
            <li v-for="item in LEGEND" :key="item.kind">
              <i :class="`kind-${item.kind}`" />
              <span>{{ item.text }}</span>
            </li>
          </ul>

          <div v-if="mode === 'tri'" class="tri-nav">
            <button type="button" class="nav-btn" aria-label="上一个月" @click="shiftWindow(-1)">
              <el-icon><ArrowLeft /></el-icon>
            </button>
            <span class="tri-range">{{ windowLabel }}</span>
            <button type="button" class="nav-btn" aria-label="下一个月" @click="shiftWindow(1)">
              <el-icon><ArrowRight /></el-icon>
            </button>
            <button type="button" class="tri-today" @click="goToday">本月</button>
          </div>
          <span v-else class="sub-hint">整段区间 14 个月，点日期查看当日</span>
        </div>

        <div v-if="mode === 'tri'" class="tri">
          <div class="tri-grid">
            <article
              v-for="item in triMonths"
              :key="item.key"
              class="tri-month"
              :class="{ 'is-anchor': item.isAnchor, 'is-out': !item.inRange }"
            >
              <header class="tm-head">
                <span class="tm-title">{{ item.year }} 年 {{ item.month }} 月</span>
                <span class="tm-meta">{{ item.work }} 班 · {{ item.rest }} 休</span>
              </header>
              <div class="tm-week">
                <span v-for="head in WEEK_HEADS" :key="head">{{ head }}</span>
              </div>
              <div class="tm-grid">
                <template v-for="(cell, index) in item.cells" :key="cell.date">
                  <button
                    v-if="cell.inMonth"
                    type="button"
                    class="day"
                    :class="[
                      `kind-${cell.kind}`,
                      {
                        'is-today': cell.isToday,
                        'is-active': cell.date === selected && !activePeriod,
                        hsel: isInActivePeriod(cell),
                      },
                    ]"
                    :style="[
                      { gridRow: Math.floor(index / 7) + 1, gridColumn: (index % 7) + 1 },
                      rangeStyle(item, index) ?? {},
                    ]"
                    :title="`${cell.date} ${cell.label}`"
                    @click="selected = cell.date"
                  >
                    <span class="d-num">{{ cell.day }}</span>
                    <span class="d-tag">{{ shortState(cell) }}</span>
                  </button>
                  <span
                    v-else
                    class="day is-blank"
                    :style="{ gridRow: Math.floor(index / 7) + 1, gridColumn: (index % 7) + 1 }"
                  />
                </template>
              </div>
            </article>
          </div>
        </div>

        <div v-else class="year-grid">
          <article v-for="item in rangeMonths" :key="item.key" class="ym">
            <header class="ym-head">
              <span class="ym-title">{{ item.year }}.{{ String(item.month).padStart(2, '0') }}</span>
              <span class="ym-meta">{{ item.work }} 班</span>
            </header>
            <div class="ym-week">
              <span v-for="head in WEEK_HEADS" :key="head">{{ head }}</span>
            </div>
            <div class="ym-grid">
              <template v-for="(cell, index) in item.cells" :key="cell.date">
                <button
                  v-if="cell.inMonth"
                  type="button"
                  class="ynum"
                  :class="[
                    `kind-${cell.kind}`,
                    {
                      'is-today': cell.isToday,
                      'is-active': cell.date === selected && !activePeriod,
                      hsel: isInActivePeriod(cell),
                    },
                  ]"
                  :style="rangeStyle(item, index) ?? {}"
                  :title="`${cell.date} ${cell.label}`"
                  @click="selected = cell.date"
                >
                  {{ cell.day }}
                </button>
                <span v-else class="ynum is-blank" />
              </template>
            </div>
          </article>
        </div>

        <div class="panel-foot">
          <span>区间内 上班 {{ total.work + total.makeup }} 天</span>
          <i class="foot-sep" />
          <span>休息 {{ total.rest + total.holiday }} 天</span>
          <i class="foot-sep" />
          <span>法定节假日 {{ total.holiday }} 天</span>
          <i class="foot-sep" />
          <span>调休上班 {{ total.makeup }} 天</span>
        </div>
      </section>

      <aside class="side">
        <section class="today-card">
          <span class="tc-label">
            选中日期
            <em v-if="isToday">今天</em>
          </span>
          <strong class="tc-date">{{ selectedLabel }}</strong>
          <div class="tc-state">
            <span class="chip" :class="`kind-${selectedInfo.kind}`">
              {{ stateName(selectedInfo.kind) }}
            </span>
            <span class="tc-reason">{{ selectedInfo.label }}</span>
          </div>

          <div class="tc-counts">
            <div class="tc-count">
              <span class="tc-k">距下次休息</span>
              <span class="tc-v">{{ nextRest ? nextRestDays : '—' }}<em>天</em></span>
              <span class="tc-m">{{ nextRest ? nextRestText : '区间内无' }}</span>
            </div>
            <div class="tc-count">
              <span class="tc-k">距下个假期</span>
              <span class="tc-v">{{ nextHoliday ? nextHolidayDays : '—' }}<em>天</em></span>
              <span class="tc-m">{{ nextHoliday ? nextHolidayText : '区间内无' }}</span>
            </div>
          </div>

          <button v-if="!isToday" type="button" class="tc-btn" @click="resetToday">回到今天</button>
        </section>

        <section class="rail-holidays">
          <h3>
            2027 法定节假日
            <em>{{ holidayRows.length }} 个</em>
          </h3>
          <ul>
            <li
              v-for="row in holidayRows"
              :key="row.name"
              class="h-item"
              :class="{ active: row.active }"
              role="button"
              tabindex="0"
              @click="focusHoliday(row.start)"
              @keydown.enter.prevent="focusHoliday(row.start)"
              @keydown.space.prevent="focusHoliday(row.start)"
            >
              <i class="h-dot" />
              <div class="h-main">
                <div class="h-line">
                  <span class="r-name">{{ row.name }}</span>
                  <span class="r-days">{{ row.days }} 天</span>
                </div>
                <span class="r-range">{{ row.range }}</span>
                <span v-if="row.makeup" class="r-makeup">调休 {{ row.makeup }}</span>
              </div>
            </li>
          </ul>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import dayjs from 'dayjs'
import {
  HOLIDAYS_2027,
  buildMonthGrid,
  monthStats,
  resolveWorkDay,
} from '@/utils/workCalendar'
import type { CalendarCell, WorkDayKind } from '@/utils/workCalendar'

const NOTICE_URL =
  'https://www.shiqian.gov.cn/xzjdbsc/dsbx/zfxxgk/fdzdgknr_5672412/shsw_5909190/202611/t20261112_91098073.html'

const RANGE_START = '2026-11-01'
const RANGE_END = '2027-12-31'
const RANGE_START_MONTH = '2026-11'
const RANGE_END_MONTH = '2027-12'

const WEEK_HEADS = ['一', '二', '三', '四', '五', '六', '日']
const WEEK_CN = ['日', '一', '二', '三', '四', '五', '六']
const LEGEND: Array<{ kind: WorkDayKind; text: string }> = [
  { kind: 'work', text: '上班' },
  { kind: 'rest', text: '休息' },
  { kind: 'holiday', text: '法定节假日' },
  { kind: 'makeup', text: '调休上班' },
]
const STATE_NAME: Record<WorkDayKind, string> = {
  work: '上班',
  rest: '休息',
  holiday: '法定节假日',
  makeup: '调休上班',
}
const SHORT_STATE: Record<WorkDayKind, string> = {
  work: '上班',
  rest: '休息',
  holiday: '假期',
  makeup: '调休',
}

type ViewMode = 'tri' | 'year'

interface MonthBlock {
  key: string
  year: number
  month: number
  cells: CalendarCell[]
  work: number
  rest: number
  isAnchor: boolean
  inRange: boolean
}

const todayKey = dayjs().format('YYYY-MM-DD')
const selected = ref(todayKey)
const mode = ref<ViewMode>('tri')

const clampAnchor = (value: dayjs.Dayjs): dayjs.Dayjs => {
  const key = value.format('YYYY-MM')
  if (key < RANGE_START_MONTH) return dayjs(`${RANGE_START_MONTH}-01`)
  if (key > RANGE_END_MONTH) return dayjs(`${RANGE_END_MONTH}-01`)
  return value
}

const anchor = ref<dayjs.Dayjs>(clampAnchor(dayjs(todayKey).startOf('month')))

/** 补齐到 6 周，避免各月高度参差 */
const padToSixWeeks = (cells: CalendarCell[], key: string): CalendarCell[] => {
  const out = [...cells]
  while (out.length < 42) {
    out.push({
      date: `${key}#${out.length}`,
      kind: 'work',
      label: '',
      day: 0,
      inMonth: false,
      isToday: false,
    })
  }
  return out
}

const buildBlock = (year: number, month: number, isAnchor = false): MonthBlock => {
  const key = `${year}-${String(month).padStart(2, '0')}`
  const cells = buildMonthGrid(year, month)
  const stats = monthStats(cells)
  return {
    key,
    year,
    month,
    cells: padToSixWeeks(cells, key),
    work: stats.work,
    rest: stats.rest,
    isAnchor,
    inRange: key >= RANGE_START_MONTH && key <= RANGE_END_MONTH,
  }
}

const triMonths = computed<MonthBlock[]>(() =>
  [0, 1].map((offset) => {
    const base = anchor.value.add(offset, 'month')
    return buildBlock(base.year(), base.month() + 1, offset === 0)
  })
)

const windowLabel = computed(() => {
  const first = triMonths.value[0]
  const last = triMonths.value[triMonths.value.length - 1]
  return `${first.year} 年 ${first.month} 月 — ${last.year} 年 ${last.month} 月`
})

const shiftWindow = (offset: number) => {
  anchor.value = clampAnchor(anchor.value.add(offset, 'month'))
}

/** 整段区间（14 个月）的月份块，用于「全部月份」视图 */
const rangeMonths = computed<MonthBlock[]>(() => {
  const blocks: MonthBlock[] = []
  let cursor = dayjs(`${RANGE_START_MONTH}-01`)
  const end = dayjs(`${RANGE_END_MONTH}-01`)
  while (!cursor.isAfter(end, 'month')) {
    blocks.push(buildBlock(cursor.year(), cursor.month() + 1))
    cursor = cursor.add(1, 'month')
  }
  return blocks
})

/** 热力条用的整段日列表（统计与倒计时复用） */
const allDays = computed(() => {
  const days: Array<{ date: string; kind: WorkDayKind; label: string }> = []
  let cursor = dayjs(RANGE_START)
  const end = dayjs(RANGE_END)
  while (!cursor.isAfter(end, 'day')) {
    const date = cursor.format('YYYY-MM-DD')
    const info = resolveWorkDay(date)
    days.push({ date, kind: info.kind, label: info.label })
    cursor = cursor.add(1, 'day')
  }
  return days
})

const total = computed<Record<WorkDayKind, number>>(() => {
  const acc: Record<WorkDayKind, number> = { work: 0, rest: 0, holiday: 0, makeup: 0 }
  for (const day of allDays.value) acc[day.kind] += 1
  return acc
})

const selectedInfo = computed(() => resolveWorkDay(selected.value))
const isToday = computed(() => selected.value === todayKey)
const selectedLabel = computed(() => {
  const date = dayjs(selected.value)
  return `${date.format('YYYY-MM-DD')} 周${WEEK_CN[date.day()]}`
})

const stateName = (kind: WorkDayKind): string => STATE_NAME[kind]
const shortState = (cell: CalendarCell): string =>
  cell.kind === 'holiday' ? cell.label : SHORT_STATE[cell.kind]

const nextRest = computed(() => {
  for (const day of allDays.value) {
    if (day.date <= todayKey) continue
    if (day.kind === 'rest' || day.kind === 'holiday') return day
  }
  return null
})

const nextRestDays = computed(() =>
  nextRest.value ? dayjs(nextRest.value.date).diff(dayjs(todayKey), 'day') : 0
)
const nextRestText = computed(() => {
  if (!nextRest.value) return ''
  const date = dayjs(nextRest.value.date)
  return `${date.format('MM-DD')} 周${WEEK_CN[date.day()]}`
})

const nextHoliday = computed(() => {
  for (const period of HOLIDAYS_2027) {
    if (period.end >= todayKey) return period
  }
  return null
})

const nextHolidayDays = computed(() => {
  if (!nextHoliday.value) return 0
  return Math.max(0, dayjs(nextHoliday.value.start).diff(dayjs(todayKey), 'day'))
})
const nextHolidayText = computed(() => {
  if (!nextHoliday.value) return ''
  return `${nextHoliday.value.name} ${nextHoliday.value.start.slice(5)} 起 · ${nextHoliday.value.days} 天`
})

const resetToday = () => {
  selected.value = todayKey
  anchor.value = clampAnchor(dayjs(todayKey).startOf('month'))
}

const goToday = resetToday

/** 点右栏假期：左侧跳到该月并选中假期首日 */
const focusHoliday = (start: string) => {
  selected.value = start
  anchor.value = clampAnchor(dayjs(start).startOf('month'))
}

/** 当前选中的日期落在哪个假期里（用于给整段假期描边） */
const activePeriod = computed(
  () =>
    HOLIDAYS_2027.find(
      (period) => selected.value >= period.start && selected.value <= period.end
    ) ?? null
)

const isInActivePeriod = (cell: CalendarCell): boolean => {
  const period = activePeriod.value
  if (!period || !cell.inMonth) return false
  return cell.date >= period.start && cell.date <= period.end
}

/**
 * 计算「整段假期」的外轮廓：只给朝向区间外的边画线，内部边留空，
 * 于是无论假期跨几行、形状多不规则，看上去都是一个连通的框。
 */
const rangeStyle = (block: MonthBlock, index: number): Record<string, string> | null => {
  const cell = block.cells[index]
  if (!cell || !isInActivePeriod(cell)) return null

  const period = activePeriod.value
  if (!period) return null

  const inside = (row: number, col: number): boolean => {
    if (row < 0 || row > 5 || col < 0 || col > 6) return false
    const neighbor = block.cells[row * 7 + col]
    if (!neighbor || !neighbor.inMonth) return false
    return neighbor.date >= period.start && neighbor.date <= period.end
  }

  const row = Math.floor(index / 7)
  const col = index % 7
  const top = !inside(row - 1, col)
  const bottom = !inside(row + 1, col)
  const left = !inside(row, col - 1)
  const right = !inside(row, col + 1)

  return {
    '--bt': top ? '2px' : '0px',
    '--br': right ? '2px' : '0px',
    '--bb': bottom ? '2px' : '0px',
    '--bl': left ? '2px' : '0px',
    '--rtl': top && left ? '9px' : '0px',
    '--rtr': top && right ? '9px' : '0px',
    '--rbr': bottom && right ? '9px' : '0px',
    '--rbl': bottom && left ? '9px' : '0px',
  }
}

const holidayRows = computed(() =>
  HOLIDAYS_2027.map((period) => ({
    name: period.name,
    start: period.start,
    end: period.end,
    active: selected.value >= period.start && selected.value <= period.end,
    range:
      period.start === period.end
        ? period.start.slice(5)
        : `${period.start.slice(5)} ~ ${period.end.slice(5)}`,
    days: period.days,
    makeup: period.makeup.map((date) => date.slice(5)).join('、'),
  }))
)
</script>

<style scoped lang="scss">
.work-calendar-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 1440px;
  margin: 0 auto;
  font-variant-numeric: tabular-nums;
}

// ---------------- 标头 ----------------
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ev-border-subtle);

  .page-title {
    margin: 0 0 5px;
    font-size: 20px;
    font-weight: 700;
    letter-spacing: 0.02em;
    color: var(--ev-text-primary);
  }

  .page-sub {
    margin: 0;
    font-size: 12.5px;
    color: var(--ev-text-muted);
  }

  .head-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-shrink: 0;
  }

  .notice-link {
    padding: 5px 11px;
    font-size: 12px;
    color: var(--ev-text-secondary);
    text-decoration: none;
    border: 1px solid var(--ev-border-default);
    border-radius: 7px;
    background: var(--ev-bg-elevated);
    transition: all 0.16s var(--ev-ease-out);

    &:hover {
      color: var(--ev-primary);
      border-color: var(--ev-border-active);
    }
  }
}

.mode-switch {
  display: inline-flex;
  padding: 2px;
  border-radius: 8px;
  background: var(--ev-bg-surface);
  border: 1px solid var(--ev-border-subtle);

  button {
    padding: 4px 12px;
    border: none;
    border-radius: 6px;
    background: transparent;
    font-size: 12px;
    color: var(--ev-text-secondary);
    cursor: pointer;
    transition: all 0.16s var(--ev-ease-out);

    &.active {
      background: var(--ev-bg-elevated);
      color: var(--ev-text-primary);
      font-weight: 600;
      box-shadow: var(--ev-shadow-xs);
    }
  }
}

// ---------------- 下：左日历 / 右信息，等高 ----------------
.body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 250px;
  gap: 14px;
  align-items: stretch;
}

.cal-panel,
.rail-holidays {
  background: var(--ev-bg-elevated);
  border: 1px solid var(--ev-border-subtle);
  border-radius: var(--ev-radius-lg);
  box-shadow: var(--ev-shadow-card);
}

.cal-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 12px 16px 10px;
  max-height: calc(100vh - 210px);
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;

  &::-webkit-scrollbar {
    width: 0;
    height: 0;
    display: none;
  }
}

.side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

// ---------------- 工具条 ----------------
.sub-bar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding-bottom: 10px;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;

  li {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 11px;
    color: var(--ev-text-muted);
  }

  i {
    width: 9px;
    height: 9px;
    border-radius: 2px;

    &.kind-work {
      background: #dbe5f1;
    }

    &.kind-rest {
      background: #a9c2da;
    }

    &.kind-holiday {
      background: #e9a0ad;
    }

    &.kind-makeup {
      background: #e8c68f;
    }
  }
}

.sub-hint {
  font-size: 11px;
  color: var(--ev-text-muted);
}

.tri-nav {
  display: flex;
  align-items: center;
  gap: 6px;

  .nav-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 23px;
    height: 23px;
    padding: 0;
    color: var(--ev-text-secondary);
    background: transparent;
    border: 1px solid var(--ev-border-subtle);
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.15s var(--ev-ease-out);

    &:hover {
      color: var(--ev-primary);
      border-color: var(--ev-border-hover);
      background: var(--ev-bg-tint);
    }
  }

  .tri-range {
    font-size: 12px;
    font-weight: 600;
    color: var(--ev-text-primary);
  }

  .tri-today {
    margin-left: 2px;
    padding: 3px 8px;
    border: 1px solid var(--ev-border-default);
    border-radius: 6px;
    background: transparent;
    font-size: 11.5px;
    color: var(--ev-text-secondary);
    cursor: pointer;

    &:hover {
      color: var(--ev-primary);
      border-color: var(--ev-border-hover);
    }
  }
}

// ---------------- 近三月 ----------------
.tri {
  flex: 1;
  min-height: 0;
}

.tri-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.tri-month {
  padding: 8px 9px 10px;
  border-radius: var(--ev-radius-md);
  border: 1px solid var(--ev-border-subtle);
  background: var(--ev-bg-surface);
  transition: border-color 0.18s var(--ev-ease-out);

  &.is-anchor {
    border-color: rgba(var(--ev-primary-rgb), 0.28);
    background: var(--ev-bg-elevated);
    box-shadow: var(--ev-shadow-xs);
  }

  &.is-out {
    opacity: 0.55;
  }
}

.tm-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
  margin-bottom: 6px;

  .tm-title {
    font-size: 12px;
    font-weight: 700;
    color: var(--ev-text-primary);
  }

  .tm-meta {
    font-size: 9.5px;
    color: var(--ev-text-muted);
  }
}

.tm-week,
.ym-week {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 5px;
  margin-bottom: 5px;

  span {
    text-align: center;
    font-size: 9.5px;
    font-weight: 600;
    color: #9aabbb;
  }
}

.ym-week {
  gap: 4px;
  margin-bottom: 4px;

  span {
    font-size: 9px;
  }
}

.tm-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 6px;
}

.day {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  height: 56px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: #dbe5f1;
  cursor: pointer;
  transition: filter 0.14s var(--ev-ease-out), box-shadow 0.14s var(--ev-ease-out);

  .d-num {
    font-size: 15px;
    font-weight: 600;
    line-height: 1.1;
    color: #2b4460;
  }

  .d-tag {
    font-size: 10px;
    line-height: 1.15;
    color: #5c7793;
    max-width: 100%;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &.kind-rest {
    background: #a9c2da;

    .d-num {
      color: #1e3651;
    }

    .d-tag {
      color: #2c4966;
    }
  }

  &.kind-holiday {
    background: #e9a0ad;

    .d-num {
      color: #651b2a;
    }

    .d-tag {
      color: #7a2434;
    }
  }

  &.kind-makeup {
    background: #e8c68f;

    .d-num {
      color: #5c3f08;
    }

    .d-tag {
      color: #6d4c0d;
    }
  }

  &.is-blank {
    background: transparent;
    cursor: default;
  }

  &.is-today .d-num {
    text-decoration: underline;
    text-underline-offset: 3px;
  }

  &.is-active {
    z-index: 3;
    transform: translateY(-1px);
    box-shadow: inset 0 0 0 2px #1d4f86, 0 8px 16px -10px rgba(29, 79, 134, 0.75);
  }

  // 整段假期：只画朝向区间外的边，内部边留空 → 自动连成一个轮廓
  &.hsel {
    z-index: 2;
  }

  &.hsel::after {
    content: '';
    position: absolute;
    inset: -3px;
    border-style: solid;
    border-color: #a8243f;
    border-width: var(--bt, 0) var(--br, 0) var(--bb, 0) var(--bl, 0);
    border-radius: var(--rtl, 0) var(--rtr, 0) var(--rbr, 0) var(--rbl, 0);
    pointer-events: none;
  }

  &:hover:not(.is-blank) {
    filter: brightness(0.96);
  }
}

// ---------------- 全部月份（14 个月总览） ----------------
.year-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  align-content: start;
  gap: 10px;
}

.ym {
  padding: 9px 10px 10px;
  border-radius: var(--ev-radius-md);
  border: 1px solid var(--ev-border-subtle);
  background: var(--ev-bg-surface);
}

.ym-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
  margin-bottom: 7px;

  .ym-title {
    font-size: 12px;
    font-weight: 700;
    color: var(--ev-text-primary);
  }

  .ym-meta {
    font-size: 9.5px;
    color: var(--ev-text-muted);
  }
}

.ym-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
}

.ynum {
  position: relative;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: #dbe5f1;
  font-size: 11px;
  font-weight: 500;
  line-height: 24px;
  text-align: center;
  color: #2b4460;
  cursor: pointer;
  transition: filter 0.12s var(--ev-ease-out), box-shadow 0.12s var(--ev-ease-out);

  &.kind-rest {
    background: #a9c2da;
    color: #1e3651;
  }

  &.kind-holiday {
    background: #e9a0ad;
    color: #651b2a;
  }

  &.kind-makeup {
    background: #e8c68f;
    color: #5c3f08;
  }

  &.is-blank {
    background: transparent;
    cursor: default;
  }

  &.is-today {
    font-weight: 800;
    text-decoration: underline;
    text-underline-offset: 2px;
  }

  &.is-active {
    z-index: 1;
    box-shadow: inset 0 0 0 1.5px #1d4f86;
  }

  // 整段假期：与近三月同一套外轮廓算法，按小格子缩小线宽
  &.hsel {
    z-index: 2;
  }

  &.hsel::after {
    content: '';
    position: absolute;
    inset: -2px;
    border-style: solid;
    border-color: #a8243f;
    border-width: calc(var(--bt, 0px) * 0.75) calc(var(--br, 0px) * 0.75)
      calc(var(--bb, 0px) * 0.75) calc(var(--bl, 0px) * 0.75);
    border-radius: calc(var(--rtl, 0px) * 0.6) calc(var(--rtr, 0px) * 0.6)
      calc(var(--rbr, 0px) * 0.6) calc(var(--rbl, 0px) * 0.6);
    pointer-events: none;
  }

  &:hover:not(.is-blank) {
    filter: brightness(0.94);
  }
}

// ---------------- 底部统计 ----------------
.panel-foot {
  position: sticky;
  bottom: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: auto;
  padding: 10px 0 2px;
  background: var(--ev-bg-elevated);
  border-top: 1px solid var(--ev-border-subtle);
  font-size: 11px;
  color: var(--ev-text-muted);

  .foot-sep {
    width: 1px;
    height: 9px;
    background: var(--ev-border-default);
  }
}

// ---------------- 右栏：选中日 ----------------
.today-card {
  padding: 13px 14px;
  border-radius: var(--ev-radius-lg);
  background: var(--ev-grad-aurora);
  box-shadow: var(--ev-glow-violet);
  color: #fff;

  .tc-label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 10.5px;
    letter-spacing: 0.08em;
    color: rgba(255, 255, 255, 0.8);

    em {
      font-style: normal;
      padding: 1px 6px;
      border-radius: var(--ev-radius-pill);
      background: rgba(255, 255, 255, 0.24);
      font-size: 10px;
      letter-spacing: 0.02em;
    }
  }

  .tc-date {
    display: block;
    margin-top: 4px;
    font-size: 18px;
    font-weight: 700;
    line-height: 1.2;
  }

  .tc-state {
    display: flex;
    align-items: center;
    gap: 7px;
    margin-top: 5px;

    .chip {
      padding: 1px 8px;
      border-radius: var(--ev-radius-pill);
      background: rgba(255, 255, 255, 0.92);
      font-size: 10.5px;
      font-weight: 700;

      &.kind-work {
        color: #2b4460;
      }

      &.kind-rest {
        color: #1e3651;
      }

      &.kind-holiday {
        color: #651b2a;
      }

      &.kind-makeup {
        color: #5c3f08;
      }
    }

    .tc-reason {
      font-size: 11px;
      color: rgba(255, 255, 255, 0.82);
    }
  }

  .tc-counts {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    margin-top: 10px;
    padding-top: 9px;
    border-top: 1px solid rgba(255, 255, 255, 0.2);
  }

  .tc-count {
    display: flex;
    flex-direction: column;
    gap: 1px;
  }

  .tc-k {
    font-size: 10px;
    color: rgba(255, 255, 255, 0.78);
  }

  .tc-v {
    font-size: 20px;
    font-weight: 700;
    line-height: 1.1;

    em {
      margin-left: 2px;
      font-style: normal;
      font-size: 10.5px;
      font-weight: 500;
      color: rgba(255, 255, 255, 0.78);
    }
  }

  .tc-m {
    font-size: 10px;
    line-height: 1.4;
    color: rgba(255, 255, 255, 0.8);
  }

  .tc-btn {
    margin-top: 9px;
    width: 100%;
    padding: 5px 0;
    border: 1px solid rgba(255, 255, 255, 0.42);
    border-radius: 6px;
    background: rgba(255, 255, 255, 0.16);
    font-size: 11px;
    font-weight: 600;
    color: #fff;
    cursor: pointer;

    &:hover {
      background: rgba(255, 255, 255, 0.28);
    }
  }
}

// ---------------- 右栏：节假日 ----------------
.rail-holidays {
  flex: 1;
  min-height: 0;
  padding: 12px 10px 10px 14px;
  overflow-y: auto;

  h3 {
    display: flex;
    align-items: baseline;
    gap: 6px;
    margin: 0 0 8px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--ev-border-subtle);
    font-size: 11.5px;
    font-weight: 700;
    letter-spacing: 0.06em;
    color: var(--ev-text-secondary);

    em {
      font-style: normal;
      font-size: 10.5px;
      font-weight: 500;
      color: var(--ev-text-muted);
    }
  }

  ul {
    display: flex;
    flex-direction: column;
    gap: 2px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .h-item {
    display: grid;
    grid-template-columns: 8px minmax(0, 1fr);
    gap: 8px;
    padding: 7px 8px;
    border-radius: 8px;
    cursor: pointer;
    outline: none;
    transition: background 0.15s var(--ev-ease-out);

    &:hover {
      background: var(--ev-bg-tint);
    }

    &:focus-visible {
      box-shadow: inset 0 0 0 1px var(--ev-border-active);
    }

    .h-dot {
      width: 8px;
      height: 8px;
      margin-top: 5px;
      border-radius: 50%;
      background: #e9a0ad;
      transition: background 0.15s var(--ev-ease-out);
    }

    .h-main {
      display: flex;
      flex-direction: column;
      gap: 1px;
      min-width: 0;
    }

    .h-line {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 8px;
    }

    .r-name {
      font-size: 12.5px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }

    .r-days {
      flex-shrink: 0;
      font-size: 10.5px;
      color: var(--ev-text-muted);
    }

    .r-range {
      font-size: 10.5px;
      color: var(--ev-text-secondary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .r-makeup {
      font-size: 10px;
      color: #8a6218;
    }

    &.active {
      background: rgba(233, 160, 173, 0.22);

      .h-dot {
        background: #a8243f;
        box-shadow: 0 0 0 3px rgba(168, 36, 63, 0.14);
      }

      .r-name {
        color: #a8243f;
      }

      .r-days {
        color: #a8243f;
      }
    }
  }
}

// ---------------- 暗色主题：格子改成深底浅字 ----------------
[data-theme='dark'] {
  .day {
    background: rgba(126, 168, 220, 0.16);

    .d-num {
      color: #d7e6f6;
    }

    .d-tag {
      color: #a7c1da;
    }

    &.kind-rest {
      background: rgba(126, 168, 220, 0.34);

      .d-num {
        color: #eaf3fd;
      }

      .d-tag {
        color: #c6dbf0;
      }
    }

    &.kind-holiday {
      background: rgba(224, 112, 139, 0.32);

      .d-num {
        color: #ffd9e1;
      }

      .d-tag {
        color: #f6c1cd;
      }
    }

    &.kind-makeup {
      background: rgba(232, 198, 143, 0.26);

      .d-num {
        color: #f6e2bb;
      }

      .d-tag {
        color: #e5cd9f;
      }
    }

    &.is-active {
      box-shadow: inset 0 0 0 2px #7fb2fb, 0 8px 16px -10px rgba(0, 0, 0, 0.7);
    }

    &.hsel::after {
      border-color: #f08aa2;
    }
  }

  .ynum {
    background: rgba(126, 168, 220, 0.16);
    color: #d7e6f6;

    &.kind-rest {
      background: rgba(126, 168, 220, 0.34);
      color: #eaf3fd;
    }

    &.kind-holiday {
      background: rgba(224, 112, 139, 0.32);
      color: #ffd9e1;
    }

    &.kind-makeup {
      background: rgba(232, 198, 143, 0.26);
      color: #f6e2bb;
    }

    &.is-active {
      box-shadow: inset 0 0 0 1.5px #7fb2fb;
    }

    &.hsel::after {
      border-color: #f08aa2;
    }
  }

  .legend i {
    &.kind-work {
      background: rgba(126, 168, 220, 0.4);
    }

    &.kind-rest {
      background: rgba(126, 168, 220, 0.72);
    }

    &.kind-holiday {
      background: #e07b93;
    }

    &.kind-makeup {
      background: #d9b06a;
    }
  }

  .today-card {
    background: linear-gradient(135deg, #1c3c5c 0%, #1d4d66 55%, #1b5a62 100%);
    box-shadow: 0 10px 26px -14px rgba(0, 0, 0, 0.85);
  }

  .tm-week span,
  .ym-week span,
  .sub-hint {
    color: var(--ev-text-muted);
  }

  .today-card .chip {
    &.kind-holiday {
      color: #7a1b2c;
    }

    &.kind-makeup {
      color: #5c3f08;
    }
  }
}

// ---------------- 响应式 ----------------
@media (max-width: 1080px) {
  .body {
    grid-template-columns: minmax(0, 1fr) 224px;
    gap: 12px;
  }

  .day {
    height: 40px;
  }

  .year-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .body {
    grid-template-columns: minmax(0, 1fr);
  }

  .side {
    display: grid;
    grid-template-columns: 240px minmax(0, 1fr);
    align-items: start;
  }

  .tri-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .cal-panel {
    max-height: none;
    overflow: visible;
  }

  .year-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .page-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .side {
    grid-template-columns: minmax(0, 1fr);
  }

  .year-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
