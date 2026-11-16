<template>
  <div class="mall-discount">
    <div class="toolbar">
      <!-- 周切换：‹ 当前周 ›，点周文字可用下拉直接跳 -->
      <div class="period">
        <button class="arrow" :disabled="!hasOlder" title="上一周" @click="stepPeriod(1)">‹</button>
        <el-dropdown trigger="click" :disabled="periods.length === 0" @command="onPeriodCommand">
          <span class="period-label" :title="periodTitle">
            {{ currentPeriod?.label || '暂无数据' }}
            <span v-if="currentPeriod" class="period-meta">
              {{ currentPeriod.filledDayCount }}/{{ currentPeriod.dayCount }} 天
            </span>
            <span class="caret">▾</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="period in periods" :key="period.id" :command="period.id">
                {{ period.label }}（{{ period.itemCount }} 件）
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <button class="arrow" :disabled="!hasNewer" title="下一周" @click="stepPeriod(-1)">›</button>
      </div>

      <!-- 七天胶囊：件数为 0 的不可点，悬停看当日窗口 -->
      <div class="days">
        <button
          v-for="day in currentPeriod?.days || []"
          :key="day.date"
          class="day-pill"
          :class="{ active: day.date === activeDay, empty: day.itemCount === 0 }"
          :disabled="day.itemCount === 0"
          :title="windowTitle(day)"
          @click="selectDay(day)"
        >
          <span class="wd">{{ day.weekday }}</span>
          <span class="dt">{{ shortDate(day.date) }}</span>
          <span v-if="day.itemCount > 0" class="cnt">{{ day.itemCount }}</span>
        </button>
      </div>

      <!-- 操作：导入 + ⋯ 菜单（删除收进菜单，不再占按钮位） -->
      <div class="ops">
        <span v-if="importing" class="import-progress">{{ importProgress }}%</span>
        <template v-if="isAdmin">
          <el-button type="primary" size="small" :loading="importing" @click="pickFolder">
            导入数据
          </el-button>
          <el-dropdown trigger="click" :disabled="periods.length === 0 || deleting" @command="onOpCommand">
            <button class="more" :disabled="deleting" title="更多操作"><el-icon><MoreFilled /></el-icon></button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  command="day"
                  :disabled="!activeDayInfo || activeDayInfo.itemCount === 0"
                >
                  删除当天数据
                </el-dropdown-item>
                <el-dropdown-item command="period" :disabled="!currentPeriod">
                  删除本期
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </div>

      <input
        ref="folderInput"
        type="file"
        webkitdirectory
        multiple
        class="folder-input"
        @change="onFolderChange"
      />
    </div>

    <div v-if="periods.length === 0" v-loading="loading" class="empty-wrap">
      <el-empty description="还没有商城特惠数据">
        <p class="empty-hint">
          {{
            isAdmin
              ? '点右上角「导入数据」，选择抓取工具导出的「商城特惠_日期_时间」文件夹'
              : '管理员导入数据后即可查看'
          }}
        </p>
      </el-empty>
    </div>

    <div v-else v-loading="loading" class="item-area">
      <el-empty
        v-if="!loading && items.length === 0"
        :description="activeDay ? '该日期还没有导入数据' : '选择上方日期查看当日商品'"
      />
      <div v-else class="item-grid">
        <MallItemCard v-for="item in items" :key="item.id" :item="item" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteMallDay, deleteMallPeriod, getMallItems, getMallPeriods, importMallFolder } from '@/api/hyolMall'
import type { MallDay, MallItem, MallPeriod } from '@/types/hyolMall'
import { useUserStore } from '@/stores/user'
import MallItemCard from './MallItemCard.vue'

const IMAGE_PATTERN = /\.(png|jpe?g|webp|gif)$/i
const MAX_IMAGE_BYTES = 8 * 1024 * 1024
const MAX_BATCH_BYTES = 60 * 1024 * 1024

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)

const periods = ref<MallPeriod[]>([])
const periodId = ref<number | null>(null)
const activeDay = ref('')
const items = ref<MallItem[]>([])
const loading = ref(false)
const importing = ref(false)
const importProgress = ref(0)
const deleting = ref(false)
const folderInput = ref<HTMLInputElement | null>(null)

const currentPeriod = computed<MallPeriod | null>(() => {
  if (!periodId.value) return null
  return periods.value.find((period) => period.id === periodId.value) || null
})

const activeDayInfo = computed<MallDay | null>(() => {
  const period = currentPeriod.value
  if (!period || !activeDay.value) return null
  return period.days.find((day) => day.date === activeDay.value) || null
})

/** 11-06 */
const shortDate = (date: string): string => date.slice(5)

/** 2026-11-06T05:00:00 → 11-06 05:00 */
const formatTime = (value?: string | null): string =>
  value ? value.replace('T', ' ').slice(5, 16) : '—'

const dayGap = (date: string, today: string): number => {
  const a = new Date(`${date}T00:00:00`).getTime()
  const b = new Date(`${today}T00:00:00`).getTime()
  return Number.isNaN(a) || Number.isNaN(b) ? Number.MAX_SAFE_INTEGER : Math.abs(a - b)
}

const todayText = (): string => {
  const now = new Date()
  const month = `${now.getMonth() + 1}`.padStart(2, '0')
  const day = `${now.getDate()}`.padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

/** 默认选中「有数据且最接近今天」的一天 */
const pickDefaultDay = (period: MallPeriod | null): string => {
  if (!period) return ''
  const filled = period.days.filter((day) => day.itemCount > 0)
  if (filled.length === 0) return ''
  const today = todayText()
  let best = filled[0]
  let bestGap = dayGap(best.date, today)
  for (const day of filled) {
    const gap = dayGap(day.date, today)
    if (gap < bestGap) {
      best = day
      bestGap = gap
    }
  }
  return best.date
}

const loadItems = async () => {
  if (!periodId.value || !activeDay.value) {
    items.value = []
    return
  }
  loading.value = true
  try {
    const res = await getMallItems(periodId.value, activeDay.value)
    items.value = res.data || []
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

const loadPeriods = async (preferId?: number, preferDay?: string) => {
  loading.value = true
  try {
    const res = await getMallPeriods()
    periods.value = res.data || []
    if (periods.value.length === 0) {
      periodId.value = null
      activeDay.value = ''
      items.value = []
      return
    }
    const preferred = preferId
      ? periods.value.find((period) => period.id === preferId)
      : undefined
    const current = periodId.value
      ? periods.value.find((period) => period.id === periodId.value)
      : undefined
    const target = preferred || current || periods.value[0]
    periodId.value = target.id
    const hasPreferDay = preferDay
      ? target.days.some((day) => day.date === preferDay)
      : false
    activeDay.value = hasPreferDay ? (preferDay as string) : pickDefaultDay(target)
  } finally {
    loading.value = false
  }
  await loadItems()
}

const onPeriodChange = async () => {
  activeDay.value = pickDefaultDay(currentPeriod.value)
  await loadItems()
}

/** 当前期在列表里的位置：列表按开始时间倒序，下标越大越早 */
const periodIndex = computed(() =>
  periods.value.findIndex((period) => period.id === periodId.value)
)
const hasOlder = computed(() => periodIndex.value >= 0 && periodIndex.value < periods.value.length - 1)
const hasNewer = computed(() => periodIndex.value > 0)

const periodTitle = computed(() =>
  currentPeriod.value
    ? `${currentPeriod.value.name} ${currentPeriod.value.label} · 共 ${currentPeriod.value.itemCount} 件`
    : ''
)

/** 当日窗口放到胶囊的 title 里，省掉一整行 */
const windowTitle = (day: MallDay): string =>
  `${day.weekday} ${day.date}　${formatTime(day.windowStart)} ~ ${formatTime(day.windowEnd)}　共 ${day.itemCount} 件`

const selectDay = async (day: MallDay) => {
  if (day.itemCount === 0 || day.date === activeDay.value) return
  activeDay.value = day.date
  await loadItems()
}

/** delta > 0 走向更早的一期，< 0 走向更新的一期 */
const stepPeriod = async (delta: number) => {
  const next = periodIndex.value + delta
  if (next < 0 || next >= periods.value.length) return
  periodId.value = periods.value[next].id
  await onPeriodChange()
}

const onPeriodCommand = async (command: number) => {
  if (!command || command === periodId.value) return
  periodId.value = command
  await onPeriodChange()
}

const onOpCommand = async (command: string) => {
  if (command === 'day') {
    await onDeleteDay()
  } else if (command === 'period') {
    await onDeletePeriod()
  }
}

const pickFolder = () => {
  folderInput.value?.click()
}

const onFolderChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  // 清空 value：同一个文件夹再选一次也能触发 change
  input.value = ''
  if (files.length === 0) return

  const manifests = files.filter((file) => file.name.toLowerCase().endsWith('.json'))
  const manifest = manifests.find((file) => file.name.includes('商城特惠')) || manifests[0]
  if (!manifest) {
    ElMessage.warning('所选文件夹里没有「商城特惠.json」')
    return
  }
  const images = files.filter((file) => IMAGE_PATTERN.test(file.name))
  const oversize = images.find((file) => file.size > MAX_IMAGE_BYTES)
  if (oversize) {
    ElMessage.error(`图片「${oversize.name}」超过 8MB，请压缩后重试`)
    return
  }
  const totalBytes = files.reduce((sum, file) => sum + file.size, 0)
  if (totalBytes > MAX_BATCH_BYTES) {
    ElMessage.warning('所选文件夹超过 60MB，请分批导入')
    return
  }

  const formData = new FormData()
  formData.append('manifest', manifest, manifest.name)
  images.forEach((file) => formData.append('images', file, file.name))

  importing.value = true
  importProgress.value = 0
  try {
    const res = await importMallFolder(formData, (percent) => {
      importProgress.value = percent
    })
    const data = res.data
    if (!data) return
    const missing = data.missingImages > 0 ? `，缺 ${data.missingImages} 张图` : ''
    ElMessage.success(
      `导入完成：${data.day}（${data.weekday}）${data.itemCount} 件商品，图片 ${data.imageCount} 张${missing}`
    )
    await loadPeriods(data.periodId, data.day)
  } catch {
    // 请求层已提示
  } finally {
    importing.value = false
    importProgress.value = 0
  }
}

onMounted(loadPeriods)

/** 删除某一天：只删这一天的商品；删完后整期空了后端会把期记录一并删除 */
const onDeleteDay = async () => {
  const period = currentPeriod.value
  const day = activeDayInfo.value
  if (!period || !day || !periodId.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除 ${day.weekday}（${day.date}）的 ${day.itemCount} 件商品吗？该日期的导入数据会被清空，可重新导入。`,
      '删除当天数据',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    await deleteMallDay(periodId.value, day.date)
    ElMessage.success('已删除当天数据')
    await loadPeriods()
  } catch {
    // 请求层已提示
  } finally {
    deleting.value = false
  }
}

/** 删除整期：该周所有天一起删掉 */
const onDeletePeriod = async () => {
  const period = currentPeriod.value
  if (!period) return
  try {
    await ElMessageBox.confirm(
      `确定删除整期 ${period.label} 的全部数据（${period.itemCount} 件商品）吗？删除后需要重新导入才能恢复。`,
      '删除本期',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    await deleteMallPeriod(period.id)
    ElMessage.success('已删除本期数据')
    periodId.value = null
    activeDay.value = ''
    items.value = []
    await loadPeriods()
  } catch {
    // 请求层已提示
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  margin-bottom: 14px;
  background: #fff;
  border: 1px solid rgba(47, 124, 246, 0.12);
  border-radius: 10px;
}

/* ===== 周切换：‹ 当前周 › ===== */

.period {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 0 0 auto;
}

.arrow {
  width: 22px;
  height: 22px;
  padding: 0;
  font-family: inherit;
  font-size: 15px;
  line-height: 1;
  color: #2f7cf6;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.arrow:hover:not(:disabled) {
  background: rgba(47, 124, 246, 0.1);
}

.arrow:disabled {
  color: #c8d6e5;
  cursor: not-allowed;
}

.period-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 6px;
  font-size: 13px;
  color: #12304f;
  border-radius: 6px;
  cursor: pointer;
  outline: none;
  font-variant-numeric: tabular-nums;
}

.period-label:hover {
  background: rgba(47, 124, 246, 0.08);
}

.period-meta {
  font-size: 12px;
  color: #8aa3bb;
}

.caret {
  font-size: 10px;
  color: #8aa3bb;
}

/* ===== 七天胶囊 ===== */

.days {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.day-pill {
  display: inline-flex;
  align-items: baseline;
  gap: 5px;
  padding: 4px 9px;
  font-family: inherit;
  font-size: 12px;
  color: #4a6a88;
  background: rgba(47, 124, 246, 0.06);
  border: 1px solid transparent;
  border-radius: 14px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.day-pill:hover:not(:disabled):not(.active) {
  background: rgba(47, 124, 246, 0.14);
}

.day-pill.active {
  color: #fff;
  background: #2f7cf6;
}

.day-pill.empty {
  color: #b7c7d6;
  background: rgba(138, 163, 187, 0.1);
  cursor: not-allowed;
}

.day-pill .wd {
  font-weight: 600;
}

.day-pill .dt {
  font-size: 11px;
  opacity: 0.9;
  font-variant-numeric: tabular-nums;
}

.day-pill .cnt {
  min-width: 15px;
  padding: 0 4px;
  font-size: 11px;
  line-height: 15px;
  text-align: center;
  border-radius: 8px;
  background: rgba(47, 124, 246, 0.14);
  color: #2f7cf6;
  font-variant-numeric: tabular-nums;
}

.day-pill.active .cnt {
  background: rgba(255, 255, 255, 0.26);
  color: #fff;
}

/* ===== 操作区 ===== */

.ops {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  color: #8aa3bb;
  background: transparent;
  border: 1px solid rgba(47, 124, 246, 0.16);
  border-radius: 6px;
  cursor: pointer;
}

.more:hover {
  color: #2f7cf6;
  background: rgba(47, 124, 246, 0.08);
}

.import-progress {
  font-size: 12px;
  color: #2f7cf6;
  font-variant-numeric: tabular-nums;
}

.folder-input {
  display: none;
}

.empty-wrap {
  min-height: 220px;
  padding: 30px 0;
}

.empty-hint {
  margin: 0;
  font-size: 12px;
  color: #8aa3bb;
}

.item-area {
  min-height: 180px;
}

.item-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

@media (max-width: 900px) {
  .ops {
    margin-left: 0;
  }

  .item-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  }
}
</style>
