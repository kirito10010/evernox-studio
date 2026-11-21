<template>
  <div class="flag-offering">
    <div class="toolbar">
      <!-- 期切换：一年 2–3 期，‹ 当前期 › -->
      <div class="period">
        <button class="arrow" :disabled="!hasOlder" title="上一期" @click="stepPeriod(1)">‹</button>
        <el-dropdown trigger="click" :disabled="periods.length === 0" @command="onPeriodCommand">
          <span class="period-label" :title="periodTitle">
            {{ currentPeriod?.label || '暂无数据' }}
            <span v-if="currentPeriod" class="period-meta">{{ currentPeriod.itemCount }} 件</span>
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
        <button class="arrow" :disabled="!hasNewer" title="下一期" @click="stepPeriod(-1)">›</button>
      </div>

      <span v-if="currentPeriod" class="period-window">
        活动时间 {{ formatDate(currentPeriod.startTime) }} ~ {{ formatDate(currentPeriod.endTime) }}
      </span>

      <div class="ops">
        <span v-if="importing" class="import-progress">{{ importProgress }}%</span>
        <template v-if="isAdmin">
          <el-button type="primary" size="small" :loading="importing" @click="pickFolder">
            导入数据
          </el-button>
          <el-dropdown trigger="click" :disabled="!currentPeriod || deleting" @command="onOpCommand">
            <button class="more" :disabled="deleting" title="更多操作"><el-icon><MoreFilled /></el-icon></button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="period" :disabled="!currentPeriod">删除本期</el-dropdown-item>
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
      <el-empty description="还没有彩旗献礼数据">
        <p class="empty-hint">
          {{
            isAdmin
              ? '点右上角「导入数据」，选择抓取工具导出的「彩旗献礼_日期_时间」文件夹'
              : '管理员导入数据后即可查看'
          }}
        </p>
      </el-empty>
    </div>

    <template v-else>
      <div class="category-bar">
        <button
          v-for="category in categories"
          :key="category.key"
          class="chip"
          :class="{ active: activeCategory === category.key }"
          @click="activeCategory = category.key"
        >
          {{ category.label }}
          <span class="cnt">{{ category.count }}</span>
        </button>
      </div>

      <div v-loading="loading" class="item-area">
        <el-empty v-if="!loading && visibleItems.length === 0" description="该分类下没有商品" />
        <div v-else class="item-grid">
          <div v-for="item in visibleItems" :key="item.id" class="grid-cell">
            <MallItemCard :item="item" image-base="/api/hyol/flag/image" />
            <span v-if="limitedLabel(item)" class="limited-tip">{{ limitedLabel(item) }}</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteFlagPeriod, getFlagItems, getFlagPeriods, importFlagFolder } from '@/api/hyolFlag'
import type { FlagItem, FlagPeriod } from '@/types/hyolFlag'
import { useUserStore } from '@/stores/user'
import MallItemCard from './MallItemCard.vue'

const IMAGE_PATTERN = /\.(png|jpe?g|webp|gif)$/i
const MAX_IMAGE_BYTES = 8 * 1024 * 1024
const MAX_BATCH_BYTES = 60 * 1024 * 1024
/** 分类固定顺序；数据里出现的其他分类排在后面 */
const CATEGORY_ORDER = ['忍者碎片', '通灵兽碎片', '礼包', '技能书', '材料', '勾玉/符石']

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)

const periods = ref<FlagPeriod[]>([])
const periodId = ref<number | null>(null)
const items = ref<FlagItem[]>([])
const activeCategory = ref('all')
const loading = ref(false)
const importing = ref(false)
const importProgress = ref(0)
const deleting = ref(false)
const folderInput = ref<HTMLInputElement | null>(null)

const currentPeriod = computed<FlagPeriod | null>(() => {
  if (!periodId.value) return null
  return periods.value.find((period) => period.id === periodId.value) || null
})

/** 2026-11-19T05:00:00 → 2026-11-19 */
const formatDate = (value?: string | null): string => (value ? value.slice(0, 10) : '—')
/** 2026-11-26T04:59:59 → 11-26 */
const shortDate = (value?: string | null): string => (value ? value.slice(5, 10) : '')

const categories = computed(() => {
  const counter = new Map<string, number>()
  for (const item of items.value) {
    const key = (item.category || '其他').trim() || '其他'
    counter.set(key, (counter.get(key) || 0) + 1)
  }
  const known = CATEGORY_ORDER.filter((name) => counter.has(name))
  const rest = [...counter.keys()].filter((name) => !CATEGORY_ORDER.includes(name)).sort()
  return [
    { key: 'all', label: '全部', count: items.value.length },
    ...[...known, ...rest].map((name) => ({
      key: name,
      label: name,
      count: counter.get(name) || 0,
    })),
  ]
})

const visibleItems = computed(() => {
  if (activeCategory.value === 'all') return items.value
  return items.value.filter((item) => ((item.category || '其他').trim() || '其他') === activeCategory.value)
})

/** 商品结束时间早于本期结束 → 本期内的限时商品 */
const limitedLabel = (item: FlagItem): string => {
  const period = currentPeriod.value
  if (!period || !item.endTime || !period.endTime) return ''
  if (item.endTime >= period.endTime) return ''
  return `限时至 ${shortDate(item.endTime)}`
}

const loadItems = async () => {
  if (!periodId.value) {
    items.value = []
    return
  }
  loading.value = true
  try {
    const res = await getFlagItems(periodId.value)
    items.value = res.data || []
    // 当前分类在新一期里不存在时回到「全部」
    if (activeCategory.value !== 'all' && !items.value.some((item) => item.category === activeCategory.value)) {
      activeCategory.value = 'all'
    }
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

const loadPeriods = async (preferId?: number) => {
  loading.value = true
  try {
    const res = await getFlagPeriods()
    periods.value = res.data || []
    if (periods.value.length === 0) {
      periodId.value = null
      items.value = []
      return
    }
    const target = (preferId ? periods.value.find((period) => period.id === preferId) : undefined)
      || (periodId.value ? periods.value.find((period) => period.id === periodId.value) : undefined)
      || periods.value[0]
    periodId.value = target.id
  } finally {
    loading.value = false
  }
  await loadItems()
}

const onPeriodChange = async () => {
  activeCategory.value = 'all'
  await loadItems()
}

/** 当前期在列表里的位置：列表按开始时间倒序，下标越大越早 */
const periodIndex = computed(() => periods.value.findIndex((period) => period.id === periodId.value))
const hasOlder = computed(() => periodIndex.value >= 0 && periodIndex.value < periods.value.length - 1)
const hasNewer = computed(() => periodIndex.value > 0)

const periodTitle = computed(() =>
  currentPeriod.value ? `${currentPeriod.value.name} ${currentPeriod.value.label}` : ''
)

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
  if (command === 'period') {
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
  const manifest = manifests.find((file) => file.name.includes('彩旗献礼')) || manifests[0]
  if (!manifest) {
    ElMessage.warning('所选文件夹里没有「彩旗献礼.json」')
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
    const res = await importFlagFolder(formData, (percent) => {
      importProgress.value = percent
    })
    const data = res.data
    if (!data) return
    const missing = data.missingImages > 0 ? `，缺 ${data.missingImages} 张图` : ''
    ElMessage.success(`导入完成：${data.periodLabel}，${data.itemCount} 件商品，图片 ${data.imageCount} 张${missing}`)
    activeCategory.value = 'all'
    await loadPeriods(data.periodId)
  } catch {
    // 请求层已提示
  } finally {
    importing.value = false
    importProgress.value = 0
  }
}

const onDeletePeriod = async () => {
  const period = currentPeriod.value
  if (!period) return
  try {
    await ElMessageBox.confirm(
      `确定删除 ${period.label} 这一期的全部数据（${period.itemCount} 件商品）吗？删除后需要重新导入才能恢复。`,
      '删除本期',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    await deleteFlagPeriod(period.id)
    ElMessage.success('已删除本期数据')
    periodId.value = null
    items.value = []
    await loadPeriods()
  } catch {
    // 请求层已提示
  } finally {
    deleting.value = false
  }
}

onMounted(loadPeriods)
</script>

<style scoped>
.flag-offering {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: #fff;
  border: 1px solid rgba(47, 124, 246, 0.12);
  border-radius: 10px;
}

/* ===== 期切换 ===== */

.period {
  display: flex;
  align-items: center;
  gap: 6px;
}

.arrow {
  width: 24px;
  height: 24px;
  padding: 0;
  font-size: 16px;
  line-height: 1;
  color: #2f7cf6;
  background: rgba(47, 124, 246, 0.08);
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}

.arrow:hover:not(:disabled) {
  background: rgba(47, 124, 246, 0.18);
}

.arrow:disabled {
  color: #b9cbdc;
  cursor: not-allowed;
}

.period-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 700;
  color: #12304f;
  cursor: pointer;
  white-space: nowrap;
}

.period-meta {
  font-size: 12px;
  font-weight: 400;
  color: #8aa3bb;
}

.caret {
  font-size: 11px;
  color: #8aa3bb;
}

.period-window {
  font-size: 12px;
  color: #8aa3bb;
}

.ops {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.import-progress {
  font-size: 12px;
  color: #2f7cf6;
  font-variant-numeric: tabular-nums;
}

.more {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #5b7fa3;
  background: rgba(47, 124, 246, 0.06);
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.more:disabled {
  color: #b9cbdc;
  cursor: not-allowed;
}

.folder-input {
  display: none;
}

/* ===== 分类筛选 ===== */

.category-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  padding: 4px 12px;
  font-size: 12px;
  color: #4a6a88;
  background: #fff;
  border: 1px solid rgba(47, 124, 246, 0.16);
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.chip:hover {
  border-color: rgba(47, 124, 246, 0.4);
}

.chip.active {
  color: #fff;
  background: #2f7cf6;
  border-color: #2f7cf6;
}

.chip .cnt {
  margin-left: 4px;
  font-variant-numeric: tabular-nums;
  opacity: 0.72;
}

/* ===== 商品网格 ===== */

.item-area {
  min-height: 160px;
}

.item-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}

.grid-cell {
  position: relative;
}

.limited-tip {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 1px 8px;
  font-size: 11px;
  color: #e0662a;
  background: rgba(224, 102, 42, 0.1);
  border: 1px solid rgba(224, 102, 42, 0.28);
  border-radius: 10px;
  pointer-events: none;
}

.empty-wrap {
  padding: 24px 0;
}

.empty-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #8aa3bb;
}

@media (max-width: 900px) {
  .item-grid {
    grid-template-columns: 1fr;
  }

  .period-window {
    display: none;
  }
}
</style>
