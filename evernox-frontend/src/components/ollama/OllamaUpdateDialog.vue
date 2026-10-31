<template>
  <el-dialog
    v-model="visible"
    title="更新模型"
    width="980px"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
  >
    <!-- 1. 选择范围 -->
    <div v-if="mode === 'config'" class="section">
      <p class="tip">
        全部留空时，按「数据更新日期」最久的 <strong>100</strong> 个模型更新；选择任意范围则更新该范围内的全部模型。
      </p>
      <div class="filter-row">
        <el-input
          v-model="search"
          placeholder="搜索模型名 / 介绍"
          clearable
          style="width: 220px"
          @input="onSearchInput"
          @clear="loadCandidates"
        />
        <el-select v-model="filters.category" placeholder="全部类型" clearable style="width: 150px" @change="loadCandidates">
          <el-option
            v-for="c in options?.categories || []"
            :key="c.key"
            :label="`${c.label} (${c.count})`"
            :value="c.key"
          />
        </el-select>
        <el-select v-model="filters.vendor" placeholder="全部厂商" clearable style="width: 190px" @change="loadCandidates">
          <el-option
            v-for="v in options?.vendors || []"
            :key="v.key"
            :label="`${v.label} (${v.count})`"
            :value="v.key"
          />
        </el-select>
        <el-select v-model="filters.size" placeholder="全部大小" clearable style="width: 140px" @change="loadCandidates">
          <el-option v-for="s in options?.sizes || []" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="filters.capability" placeholder="全部能力" clearable style="width: 140px" @change="loadCandidates">
          <el-option
            v-for="c in options?.capabilities || []"
            :key="c"
            :label="capabilityLabel(c)"
            :value="c"
          />
        </el-select>
        <el-select v-model="filters.version" style="width: 150px" @change="loadCandidates">
          <el-option label="全部版本" value="all" />
          <el-option label="标准版本" value="standard" />
          <el-option label="Abliterated" value="abliterated" />
        </el-select>
        <el-button text @click="resetFilters">重置</el-button>
      </div>

      <!-- 至少有一个筛选/搜索时，列出匹配的模型供勾选 -->
      <div v-if="listMode" v-loading="candidateLoading" class="candidate-block">
        <div class="candidate-head">
          <span>
            匹配 <strong>{{ candidates.length }}</strong> 个模型，已选
            <strong>{{ selectedIds.length }}</strong> 个
          </span>
          <div class="candidate-actions">
            <el-button text size="small" @click="selectAll">全选</el-button>
            <el-button text size="small" @click="clearSelection">清空</el-button>
          </div>
        </div>
        <el-table
          ref="tableRef"
          :data="candidates"
          size="small"
          row-key="id"
          max-height="300"
          border
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="46" reserve-selection />
          <el-table-column prop="name" label="模型" min-width="220" show-overflow-tooltip />
          <el-table-column label="厂商" width="120">
            <template #default="{ row }">{{ row.vendorLabel || '-' }}</template>
          </el-table-column>
          <el-table-column label="下载量" width="100" align="right">
            <template #default="{ row }">{{ row.pullsText || '-' }}</template>
          </el-table-column>
          <el-table-column prop="tagCount" label="变体" width="80" align="right" />
          <el-table-column label="数据更新" width="120" align="center">
            <template #default="{ row }">{{ formatDate(row.lastUpdatedAt, '-') }}</template>
          </el-table-column>
        </el-table>
        <p class="hint">勾选要更新的模型后再点「开始更新」；超过 2000 个请先用筛选或搜索缩小范围。</p>
      </div>
      <p v-else class="hint">
        未选择筛选条件：将按「数据更新」最久的 <strong>100</strong> 个模型更新。
      </p>

      <p class="hint">模型下架（官网 404）会被删除；抓取失败的模型保留旧数据，下次仍会优先更新。</p>
    </div>

    <!-- 2. 进度 -->
    <div v-else-if="mode === 'running'" class="section">
      <div class="progress-head">
        <span class="phase">{{ task?.phase || '正在更新…' }}</span>
        <span v-if="task && task.total > 0" class="count">{{ task.done }} / {{ task.total }}</span>
      </div>
      <el-progress :percentage="percent" :stroke-width="10" />
      <p class="hint">作业在服务端执行，可以切换到其他菜单；回到本页后进度会继续刷新。</p>
    </div>

    <!-- 3. 结果 -->
    <div v-else class="section">
      <div class="summary">
        <span>共处理 <strong>{{ result?.requested ?? 0 }}</strong> 个</span>
        <span>已更新 <strong>{{ result?.updated ?? 0 }}</strong></span>
        <span>无变化 {{ result?.unchanged ?? 0 }}</span>
        <span class="danger">下架删除 {{ result?.deleted ?? 0 }}</span>
        <span class="warning">失败 {{ result?.failed ?? 0 }}</span>
      </div>
      <el-table :data="sortedItems" size="small" max-height="420" border>
        <el-table-column label="模型" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" size="small" effect="plain">
              {{ statusMeta(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下载量" width="170" align="right">
          <template #default="{ row }">
            <span v-if="row.status === 'FAILED' || row.status === 'DELETED'" class="muted">
              {{ row.pullsBeforeText || row.pullsBefore || '-' }}
            </span>
            <template v-else>
              <span class="muted">{{ row.pullsBeforeText || row.pullsBefore || '-' }}</span>
              <span class="arrow"> → </span>
              <span>{{ row.pullsAfterText || row.pullsAfter || '-' }}</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="变体" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.status === 'FAILED' || row.status === 'DELETED'">{{ row.tagsBefore }}</span>
            <template v-else>
              <span class="muted">{{ row.tagsBefore }}</span>
              <span class="arrow"> → </span>
              <span>{{ row.tagsAfter }}</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="变体变化" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="row.addedTags?.length || row.removedTags?.length">
              <div v-if="row.addedTags?.length" class="tag-line add">
                <span class="tag-label">新增</span>
                <el-tag v-for="t in row.addedTags" :key="t" size="small" type="success" effect="plain">
                  {{ t }}
                </el-tag>
              </div>
              <div v-if="row.removedTags?.length" class="tag-line remove">
                <span class="tag-label">删除</span>
                <el-tag v-for="t in row.removedTags" :key="t" size="small" type="danger" effect="plain">
                  {{ t }}
                </el-tag>
              </div>
            </template>
            <span v-else class="muted">{{ row.message || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!result?.items?.length" description="本次没有需要更新的模型" :image-size="60" />
    </div>

    <template #footer>
      <template v-if="mode === 'config'">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" :loading="busy" @click="start">开始更新</el-button>
      </template>
      <template v-else-if="mode === 'running'">
        <span class="footer-hint">更新进行中，完成后需要手动关闭</span>
      </template>
      <template v-else>
        <el-button
          v-if="(result?.failed ?? 0) > 0"
          type="warning"
          :loading="busy"
          @click="retryFailed"
        >
          重试失败的模型（{{ result?.failed }}）
        </el-button>
        <el-button type="primary" @click="close">关闭</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { formatDate } from '@/utils/format'
import {
  getOllamaUpdateCandidates,
  type OllamaFilterOptions,
  type OllamaTaskStatus,
  type OllamaUpdateCandidate,
  type OllamaUpdateRequest,
} from '@/api/ollamaModel'

const props = defineProps<{
  modelValue: boolean
  task: OllamaTaskStatus | null
  busy?: boolean
  options?: OllamaFilterOptions | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'start', request: OllamaUpdateRequest): void
  (e: 'retry', ids: number[]): void
  (e: 'close'): void
}>()

const CAPABILITY_LABELS: Record<string, string> = {
  vision: '视觉',
  tools: '工具调用',
  thinking: '思考',
  embedding: '向量',
  cloud: '云端',
}

const capabilityLabel = (key: string) => CAPABILITY_LABELS[key] ?? key

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const filters = reactive({
  category: '',
  vendor: '',
  size: '',
  capability: '',
  version: 'all' as 'all' | 'standard' | 'abliterated',
})

const search = ref('')
const candidates = ref<OllamaUpdateCandidate[]>([])
const candidateLoading = ref(false)
const selectedIds = ref<number[]>([])
const tableRef = ref<TableInstance>()

const submitted = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | null = null

/** 只要有任意筛选或搜索词，就进入「列出候选、手动勾选」模式 */
const listMode = computed(
  () =>
    Boolean(
      search.value.trim() ||
        filters.category ||
        filters.vendor ||
        filters.size ||
        filters.capability ||
        filters.version !== 'all'
    )
)

const mode = computed<'config' | 'running' | 'result'>(() => {
  const task = props.task
  if (!submitted.value && (!task || task.type !== 'UPDATE')) return 'config'
  if (task && task.type === 'UPDATE') {
    return task.running ? 'running' : 'result'
  }
  return 'config'
})

const result = computed(() =>
  props.task && props.task.type === 'UPDATE' ? props.task.update : null
)

/** 与后端一致的展示顺序：失败 → 下架删除 → 有变化 → 无变化 */
const STATUS_RANK: Record<string, number> = {
  FAILED: 0,
  DELETED: 1,
  UPDATED: 2,
  UNCHANGED: 3,
}

const sortedItems = computed(() => {
  const items = result.value?.items ? [...result.value.items] : []
  return items.sort((a, b) => (STATUS_RANK[a.status] ?? 9) - (STATUS_RANK[b.status] ?? 9))
})

const percent = computed(() => {
  const task = props.task
  if (!task || task.total <= 0) return 0
  return Math.min(100, Math.round((task.done / task.total) * 100))
})

watch(
  () => props.modelValue,
  (open) => {
    if (open && props.task?.type === 'UPDATE') {
      submitted.value = true
    }
  },
  { immediate: true }
)

const STATUS_META: Record<string, { label: string; type: 'success' | 'info' | 'warning' | 'danger' }> = {
  UPDATED: { label: '已更新', type: 'success' },
  UNCHANGED: { label: '无变化', type: 'info' },
  DELETED: { label: '已下架删除', type: 'danger' },
  FAILED: { label: '失败', type: 'warning' },
}

const statusMeta = (status: string) => STATUS_META[status] ?? { label: status, type: 'info' as const }

const onSelectionChange = (rows: OllamaUpdateCandidate[]) => {
  selectedIds.value = rows.map((row) => row.id)
}

const selectAll = () => {
  tableRef.value?.toggleAllSelection()
}

const clearSelection = () => {
  tableRef.value?.clearSelection()
  selectedIds.value = []
}

const loadCandidates = async () => {
  if (!listMode.value) {
    candidates.value = []
    selectedIds.value = []
    return
  }
  candidateLoading.value = true
  try {
    const res = await getOllamaUpdateCandidates({
      keyword: search.value.trim() || undefined,
      category: filters.category || undefined,
      vendor: filters.vendor || undefined,
      size: filters.size || undefined,
      capability: filters.capability || undefined,
      abliterated:
        filters.version === 'abliterated' ? true : filters.version === 'standard' ? false : undefined,
      limit: 2000,
    })
    candidates.value = res.data || []
    selectedIds.value = []
    tableRef.value?.clearSelection()
  } catch {
    candidates.value = []
  } finally {
    candidateLoading.value = false
  }
}

const onSearchInput = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(loadCandidates, 300)
}

const resetFilters = () => {
  filters.category = ''
  filters.vendor = ''
  filters.size = ''
  filters.capability = ''
  filters.version = 'all'
  search.value = ''
  candidates.value = []
  selectedIds.value = []
}

const start = () => {
  const request: OllamaUpdateRequest = {
    category: filters.category || undefined,
    vendor: filters.vendor || undefined,
    size: filters.size || undefined,
    capability: filters.capability || undefined,
    abliterated:
      filters.version === 'abliterated' ? true : filters.version === 'standard' ? false : undefined,
  }
  if (listMode.value) {
    if (!selectedIds.value.length) {
      ElMessage.warning('请先勾选要更新的模型')
      return
    }
    request.ids = [...selectedIds.value]
  }
  submitted.value = true
  emit('start', request)
}

const close = () => {
  emit('close')
  visible.value = false
}

/** 只重试本次失败的模型 */
const retryFailed = () => {
  const ids = (result.value?.items || [])
    .filter((item) => item.status === 'FAILED')
    .map((item) => item.id)
  if (!ids.length) return
  emit('retry', ids)
}
</script>

<style scoped lang="scss">
.section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tip {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ev-text-secondary);
}

.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.candidate-block {
  display: flex;
  flex-direction: column;
  gap: 8px;

  .candidate-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 12.5px;
    color: var(--ev-text-regular);

    strong {
      color: var(--ev-primary);
    }
  }

  .candidate-actions {
    display: flex;
    gap: 4px;
  }
}

.hint {
  margin: 0;
  font-size: 12px;
  color: var(--ev-text-muted);
}

.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--ev-text-primary);

  .count {
    font-weight: 600;
    color: var(--ev-primary);
  }
}

.summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: var(--ev-text-regular);

  strong {
    color: var(--ev-primary);
  }

  .danger {
    color: var(--el-color-danger);
  }

  .warning {
    color: var(--el-color-warning);
  }
}

.muted {
  color: var(--ev-text-muted);
}

.arrow {
  color: var(--ev-text-muted);
}

.tag-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 2px;

  .tag-label {
    font-size: 11px;
    color: var(--ev-text-muted);
    margin-right: 2px;
  }
}

.footer-hint {
  font-size: 12px;
  color: var(--ev-text-muted);
}
</style>
