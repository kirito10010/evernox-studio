<template>
  <div class="battle-summon">
    <div class="toolbar">
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

      <span v-if="currentPeriod" class="period-window">{{ currentPeriod.name }}</span>

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
      <el-empty description="还没有战场的召唤数据">
        <p class="empty-hint">
          {{
            isAdmin
              ? '点右上角「导入数据」，选择抓取工具导出的「战场的召唤_日期_时间」文件夹'
              : '管理员导入数据后即可查看'
          }}
        </p>
      </el-empty>
    </div>

    <div v-else v-loading="loading" class="camp-board">
      <section
        v-for="(camp, index) in camps"
        :key="camp.name"
        class="camp-panel"
        :class="index === 0 ? 'wave' : 'uchiha'"
      >
        <header class="camp-head">
          <div class="camp-title">
            <span class="camp-name">{{ camp.name }}</span>
            <span v-if="camp.representative" class="camp-rep">代表忍者：{{ camp.representative }}</span>
          </div>
          <span class="camp-count">{{ itemsOf(camp.name).length }} 件</span>
        </header>
        <div class="camp-grid">
          <MallItemCard
            v-for="item in itemsOf(camp.name)"
            :key="item.id"
            :item="toCard(item)"
            image-base="/api/hyol/battle/image"
            limit-label="限购"
            :show-slot="false"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteBattlePeriod, getBattleItems, getBattlePeriods, importBattleFolder } from '@/api/hyolBattle'
import type { BattleItem, BattlePeriod } from '@/types/hyolBattle'
import type { MallItem } from '@/types/hyolMall'
import { useUserStore } from '@/stores/user'
import MallItemCard from './MallItemCard.vue'

const IMAGE_PATTERN = /\.(png|jpe?g|webp|gif)$/i
const MAX_IMAGE_BYTES = 8 * 1024 * 1024
const MAX_BATCH_BYTES = 60 * 1024 * 1024

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)

const periods = ref<BattlePeriod[]>([])
const periodId = ref<number | null>(null)
const items = ref<BattleItem[]>([])
const loading = ref(false)
const importing = ref(false)
const importProgress = ref(0)
const deleting = ref(false)
const folderInput = ref<HTMLInputElement | null>(null)

const currentPeriod = computed<BattlePeriod | null>(() => {
  if (!periodId.value) return null
  return periods.value.find((period) => period.id === periodId.value) || null
})

const camps = computed(() => currentPeriod.value?.camps || [])

const itemsOf = (camp: string): BattleItem[] =>
  items.value.filter((item) => item.camp === camp)

/** 战场商品 → 通用卡片数据（数量/限购映射到卡片字段） */
const toCard = (item: BattleItem): MallItem => ({
  id: item.id,
  periodId: item.periodId,
  slot: item.slot,
  itemId: item.itemId,
  name: item.name,
  description: item.description,
  iconId: item.iconId,
  price: item.price,
  currency: item.currency,
  quantity: item.quantity,
  dailyLimit: item.limitCount,
  image: item.image,
  detail: item.detail,
})

const loadItems = async () => {
  if (!periodId.value) {
    items.value = []
    return
  }
  loading.value = true
  try {
    const res = await getBattleItems(periodId.value)
    items.value = res.data || []
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

const loadPeriods = async (preferId?: number) => {
  loading.value = true
  try {
    const res = await getBattlePeriods()
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

/** 当前期在列表里的位置：列表按结束时间倒序，下标越大越早 */
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
  await loadItems()
}

const onPeriodCommand = async (command: number) => {
  if (!command || command === periodId.value) return
  periodId.value = command
  await loadItems()
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
  const manifest = manifests.find((file) => file.name.includes('战场的召唤')) || manifests[0]
  if (!manifest) {
    ElMessage.warning('所选文件夹里没有「战场的召唤.json」')
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
    const res = await importBattleFolder(formData, (percent) => {
      importProgress.value = percent
    })
    const data = res.data
    if (!data) return
    const missing = data.missingImages > 0 ? `，缺 ${data.missingImages} 张图` : ''
    ElMessage.success(
      `导入完成：${data.periodLabel}，${data.campCount} 个阵营 ${data.itemCount} 件商品，图片 ${data.imageCount} 张${missing}`
    )
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
    await deleteBattlePeriod(period.id)
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
.battle-summon {
  display: flex;
  flex-direction: column;
  gap: 14px;
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

/* ===== 双阵营面板 ===== */

.camp-board {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  align-items: start;
}

.camp-panel {
  padding: 12px;
  border-radius: 12px;
  border: 1px solid transparent;
}

.camp-panel.wave {
  background: linear-gradient(180deg, rgba(47, 124, 246, 0.09), rgba(47, 124, 246, 0.02));
  border-color: rgba(47, 124, 246, 0.22);
}

.camp-panel.uchiha {
  background: linear-gradient(180deg, rgba(214, 69, 69, 0.09), rgba(214, 69, 69, 0.02));
  border-color: rgba(214, 69, 69, 0.22);
}

.camp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.camp-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.camp-name {
  font-size: 16px;
  font-weight: 700;
}

.camp-panel.wave .camp-name {
  color: #1f5fbf;
}

.camp-panel.uchiha .camp-name {
  color: #b23b3b;
}

.camp-rep {
  font-size: 12px;
  color: #5b7fa3;
}

.camp-count {
  flex: 0 0 auto;
  padding: 1px 8px;
  font-size: 11px;
  color: #5b7fa3;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 10px;
}

.camp-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 10px;
}

.empty-wrap {
  padding: 24px 0;
}

.empty-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #8aa3bb;
}

@media (max-width: 1100px) {
  .camp-board {
    grid-template-columns: 1fr;
  }

  .period-window {
    display: none;
  }
}
</style>
