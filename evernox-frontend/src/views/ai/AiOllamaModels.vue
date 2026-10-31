<template>
  <div class="ollama-page">
    <div class="page-header">
      <div class="header-left">
        <h1>
          <el-icon><Box /></el-icon>
          <span>Ollama 模型库</span>
        </h1>
        <p class="sub">仅收录官网可下载的本地模型，包含各尺寸 / 量化变体与下载命令</p>
      </div>
      <div class="header-right">
        <div class="stat">
          <span class="stat-num">{{ status.modelTotal }}</span>
          <span class="stat-label">模型</span>
        </div>
        <div class="stat">
          <span class="stat-num">{{ status.tagTotal }}</span>
          <span class="stat-label">变体</span>
        </div>
        <div class="stat">
          <span class="stat-num">{{ syncText }}</span>
          <span class="stat-label">最近同步</span>
        </div>
        <el-button v-if="userStore.isAdmin" :disabled="!!task?.running" @click="openDiscover">
          获取新模型
        </el-button>
        <el-button
          v-if="userStore.isAdmin"
          type="primary"
          :disabled="!!task?.running"
          @click="openUpdate"
        >
          更新模型
        </el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="filters.keyword"
        placeholder="搜索模型名 / 介绍"
        clearable
        style="width: 220px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-select v-model="filters.category" placeholder="全部类型" clearable style="width: 130px" @change="reload">
        <el-option
          v-for="c in options.categories"
          :key="c.key"
          :label="`${c.label} (${c.count})`"
          :value="c.key"
        />
      </el-select>
      <el-select v-model="filters.vendor" placeholder="全部厂商" clearable style="width: 180px" @change="reload">
        <el-option v-for="v in options.vendors" :key="v.key" :label="`${v.label} (${v.count})`" :value="v.key" />
      </el-select>
      <el-select v-model="filters.size" placeholder="全部大小" clearable style="width: 130px" @change="reload">
        <el-option v-for="s in options.sizes" :key="s" :label="s" :value="s" />
      </el-select>
      <el-select v-model="filters.capability" placeholder="全部能力" clearable style="width: 130px" @change="reload">
        <el-option v-for="c in options.capabilities" :key="c" :label="capabilityLabel(c)" :value="c" />
      </el-select>
      <el-select v-model="filters.version" style="width: 150px" @change="reload">
        <el-option label="全部版本" value="all" />
        <el-option label="标准版本" value="standard" />
        <el-option label="Abliterated" value="abliterated" />
      </el-select>
      <el-select v-model="filters.sort" style="width: 130px" @change="reload">
        <el-option label="按下载量" value="popular" />
        <el-option label="按更新时间" value="new" />
        <el-option label="按参数量" value="size" />
      </el-select>
      <!--
        获取新模型 / 更新模型改由页面右上角两个按钮触发：
        获取时在弹窗里单独填关键词（不用占用这里的列表筛选），更新时在弹窗里选范围。
      -->
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <div class="table-wrap">
      <el-table :data="models" v-loading="loading" row-key="id" @row-click="onRowClick">
        <el-table-column label="模型" min-width="260">
          <template #default="{ row }">
            <div class="model-cell">
              <span class="model-name">{{ row.name }}</span>
              <el-tag
                v-if="row.category"
                size="small"
                :type="categoryMeta(row.category).type"
                effect="plain"
              >
                {{ categoryMeta(row.category).label }}
              </el-tag>
              <el-tag v-if="row.isAbliterated === 1" size="small" type="warning" effect="plain">abliterated</el-tag>
            </div>
            <div v-if="row.description" class="model-desc">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="厂商" width="140">
          <template #default="{ row }">{{ row.vendorLabel || '-' }}</template>
        </el-table-column>
        <el-table-column label="参数量" width="130">
          <template #default="{ row }">
            <span v-if="row._sizesText">{{ row._sizesText }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="下载量" width="100" align="right">
          <template #default="{ row }">{{ row.pullsText || row.pulls }}</template>
        </el-table-column>
        <el-table-column label="变体" width="80" align="right">
          <template #default="{ row }">{{ row.tagCount }}</template>
        </el-table-column>
        <el-table-column label="能力" width="200">
          <template #default="{ row }">
            <el-tag
              v-for="c in row._capabilities"
              :key="c.key"
              size="small"
              effect="plain"
              class="cap-tag"
            >
              {{ c.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="官网更新" width="120" align="center">
          <template #default="{ row }">{{ formatDate(row.sourceUpdatedAt, '-') }}</template>
        </el-table-column>
        <el-table-column label="数据更新" width="120" align="center">
          <template #default="{ row }">{{ formatDate(row.lastUpdatedAt, '-') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click.stop="onRowClick(row)">查看变体</el-button>
            <el-button size="small" text @click.stop="copyCommand(row.defaultCommand)">复制命令</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && models.length === 0">
        <template #description>
          <span v-if="filters.keyword.trim()">
            本地没有匹配「{{ filters.keyword.trim() }}」的模型，可点上方「从官网补全」
          </span>
          <span v-else>暂无模型数据</span>
        </template>
      </el-empty>
    </div>

    <div class="table-footer">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="load"
        @size-change="reload"
      />
    </div>

    <el-drawer v-model="drawerVisible" :title="detail?.name || '模型详情'" size="62%">
      <div v-if="detail" v-loading="detailLoading" class="detail">
        <div class="detail-tags">
          <el-tag
            v-if="detail.category"
            size="small"
            :type="categoryMeta(detail.category).type"
            effect="plain"
          >
            {{ categoryMeta(detail.category).label }}
          </el-tag>
          <el-tag v-for="c in detailCapabilities" :key="c.key" size="small" effect="plain">
            {{ c.label }}
          </el-tag>
          <el-tag v-if="detail.vendorLabel" size="small" type="info" effect="plain">{{ detail.vendorLabel }}</el-tag>
          <el-tag v-if="detail.isAbliterated === 1" size="small" type="warning" effect="plain">abliterated</el-tag>
        </div>

        <p v-if="detail.description" class="detail-desc">{{ detail.description }}</p>

        <div class="detail-meta">
          <span>下载量：{{ detail.pullsText || detail.pulls }}</span>
          <span>参数量：{{ detailSizesText }}</span>
          <span>官方更新：{{ formatDate(detail.sourceUpdatedAt, '-') }}</span>
          <a :href="detail.url" target="_blank" rel="noopener noreferrer">官网页面</a>
        </div>

        <div class="detail-command">
          <span class="cmd-text">{{ detail.defaultCommand }}</span>
          <el-button size="small" text type="primary" @click="copyCommand(detail.defaultCommand)">复制</el-button>
        </div>

        <h3 class="section-title">变体（{{ detail.tags?.length ?? 0 }}）</h3>
        <el-table :data="detail.tags || []" size="small" row-key="id">
          <el-table-column label="标签" min-width="240">
            <template #default="{ row }">
              <div class="tag-cell">
                <span class="tag-name">{{ row.name }}</span>
                <el-tag v-if="row.isLatest === 1" size="small" type="primary" effect="plain">latest</el-tag>
                <el-tag v-if="row.isMlx === 1" size="small" type="info" effect="plain">MLX</el-tag>
              </div>
              <div v-if="row.digest" class="tag-digest">{{ row.digest }}</div>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="90">
            <template #default="{ row }">{{ row.sizeText || '-' }}</template>
          </el-table-column>
          <el-table-column label="上下文" width="90">
            <template #default="{ row }">{{ row.contextText || '-' }}</template>
          </el-table-column>
          <el-table-column label="输入" width="120">
            <template #default="{ row }">{{ row.inputs || '-' }}</template>
          </el-table-column>
          <el-table-column label="更新" width="110">
            <template #default="{ row }">{{ row.sourceUpdatedText || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="copyCommand(row.command)">复制</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <OllamaDiscoverDialog
      :key="discoverSession"
      v-model="discoverVisible"
      :task="task"
      :busy="taskStarting"
      @start="onDiscoverStart"
      @close="onTaskDialogClose"
    />

    <OllamaUpdateDialog
      :key="updateSession"
      v-model="updateVisible"
      :task="task"
      :busy="taskStarting"
      :options="options"
      @start="onUpdateStart"
      @retry="onUpdateRetry"
      @close="onTaskDialogClose"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onDeactivated, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useClipboard } from '@/composables/useClipboard'
import { formatDate } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import {
  getOllamaFilters,
  getOllamaModelDetail,
  getOllamaModels,
  getOllamaSyncStatus,
  getOllamaTaskStatus,
  startOllamaDiscover,
  startOllamaUpdate,
  type OllamaFilterOptions,
  type OllamaModel,
  type OllamaSyncStatus,
  type OllamaTaskStatus,
  type OllamaUpdateRequest,
} from '@/api/ollamaModel'
import OllamaDiscoverDialog from '@/components/ollama/OllamaDiscoverDialog.vue'
import OllamaUpdateDialog from '@/components/ollama/OllamaUpdateDialog.vue'

// 显式声明组件名：MainLayout 的 <keep-alive :include="..."> 按组件名匹配缓存。
defineOptions({ name: 'AiOllamaModels' })

const userStore = useUserStore()

const CAPABILITY_LABELS: Record<string, string> = {
  vision: '视觉',
  tools: '工具调用',
  thinking: '思考',
  embedding: '向量',
  cloud: '云端',
}

const CATEGORY_META: Record<string, { label: string; type: 'success' | 'warning' | 'info' }> = {
  official: { label: '官方', type: 'success' },
  third_party: { label: '第三方', type: 'warning' },
  community: { label: '社区', type: 'info' },
}

const categoryMeta = (category: string) =>
  CATEGORY_META[category] ?? { label: category, type: 'info' as const }

/**
 * 展示行：把每行的显示文本/标签在**数据加载时算一次**。
 * 原来模板里直接 `row.sizes.split(',')`、`splitCapabilities(row.capabilities)`、
 * `capabilityLabel(c)` —— 每行每次重渲染都要重新 split + filter + 查表。
 * 表格页大小可到 100，这笔开销不划算。
 */
interface OllamaRow extends OllamaModel {
  /** 参数量显示文本，如 "7b / 27b"；无参数量时为空串 */
  _sizesText: string
  /** 能力标签（已翻译），直接 v-for */
  _capabilities: { key: string; label: string }[]
}

const models = ref<OllamaRow[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const filters = reactive({
  keyword: '',
  category: '',
  vendor: '',
  size: '',
  capability: '',
  version: 'all' as 'all' | 'standard' | 'abliterated',
  sort: 'popular' as 'popular' | 'new' | 'size',
})

const options = ref<OllamaFilterOptions>({ categories: [], vendors: [], sizes: [], capabilities: [] })
const status = ref<OllamaSyncStatus>({
  running: false,
  lastRunAt: null,
  lastSuccess: false,
  durationMs: 0,
  modelTotal: 0,
  tagTotal: 0,
  modelAdded: 0,
  modelUpdated: 0,
  modelRemoved: 0,
  tagAdded: 0,
  tagRemoved: 0,
  message: null,
})

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<OllamaModel | null>(null)

// ---------- 获取新模型 / 更新模型（后台作业） ----------
const task = ref<OllamaTaskStatus | null>(null)
const discoverVisible = ref(false)
const updateVisible = ref(false)
const taskStarting = ref(false)
/** 上次手动关闭过的 finishedAt：相同则不重复弹出结果弹窗 */
const closedFinishedAt = ref<string | null>(null)
/** 每次打开弹窗换 key，重置弹窗内部状态 */
const discoverSession = ref(0)
const updateSession = ref(0)
let taskTimer: number | null = null

const capabilityLabel = (c: string) => CAPABILITY_LABELS[c] ?? c

/** 把能力字符串拆成 {key,label} 列表（供预计算与抽屉 computed 复用） */
const toCapabilities = (caps: string | null | undefined) =>
  caps
    ? caps
        .split(',')
        .filter(Boolean)
        .map((k) => ({ key: k, label: capabilityLabel(k) }))
    : []

/** 接口行 → 展示行（显示文本只算一次） */
const toRow = (m: OllamaModel): OllamaRow => ({
  ...m,
  _sizesText: m.sizes ? m.sizes.split(',').join(' / ') : '',
  _capabilities: toCapabilities(m.capabilities),
})

/** 抽屉内容来自单独的详情请求；用 computed 缓存，模板里就不必每次重渲染都拆一遍 */
const detailCapabilities = computed(() => toCapabilities(detail.value?.capabilities))
const detailSizesText = computed(() =>
  detail.value?.sizes ? detail.value.sizes.split(',').join(' / ') : '-'
)

const syncText = computed(() => (status.value.lastRunAt ? formatDate(status.value.lastRunAt, '-') : '未同步'))

const load = async () => {
  loading.value = true
  try {
    const res = await getOllamaModels({
      keyword: filters.keyword || undefined,
      category: filters.category || undefined,
      vendor: filters.vendor || undefined,
      size: filters.size || undefined,
      capability: filters.capability || undefined,
      abliterated:
        filters.version === 'abliterated' ? true : filters.version === 'standard' ? false : undefined,
      sort: filters.sort,
      page: page.value,
      pageSize: pageSize.value,
    })
    models.value = (res.data?.records ?? []).map(toRow)
    total.value = res.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

const reload = () => {
  page.value = 1
  void load()
}

const resetFilters = () => {
  filters.keyword = ''
  filters.category = ''
  filters.vendor = ''
  filters.size = ''
  filters.capability = ''
  filters.version = 'all'
  filters.sort = 'popular'
  reload()
}

const loadOptions = async () => {
  const res = await getOllamaFilters()
  options.value = res.data ?? { categories: [], vendors: [], sizes: [], capabilities: [] }
}

const loadStatus = async () => {
  const res = await getOllamaSyncStatus()
  if (res.data) {
    status.value = res.data
  }
  return status.value
}

/** 拉取单个模型的详情（含变体列表） */
const loadDetail = async (id: number) => {
  detailLoading.value = true
  try {
    const res = await getOllamaModelDetail(id)
    detail.value = res.data ?? null
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

/** 打开详情抽屉；行类型来自 el-table 插槽，收窄成 OllamaModel */
const onRowClick = async (row: unknown) => {
  const model = row as OllamaModel
  if (!model || typeof model.id !== 'number') {
    return
  }
  drawerVisible.value = true
  await loadDetail(model.id)
}

const { copy } = useClipboard()

const copyCommand = async (text: string | null | undefined) => {
  if (!text) return
  await copy(text, `已复制：${text}`)
}

const stopTaskPolling = () => {
  if (taskTimer !== null) {
    window.clearInterval(taskTimer)
    taskTimer = null
  }
}

/** 拉一次作业状态，并按结果打开对应弹窗（运行中 / 新的结果） */
const loadTask = async () => {
  const res = await getOllamaTaskStatus().catch(() => null)
  const st = res?.data
  if (!st) return
  task.value = st
  if (!st.type) return
  if (st.running) {
    if (st.type === 'DISCOVER') discoverVisible.value = true
    else updateVisible.value = true
    startTaskPolling()
    return
  }
  stopTaskPolling()
  if (st.finishedAt && st.finishedAt !== closedFinishedAt.value) {
    if (st.type === 'DISCOVER') discoverVisible.value = true
    else updateVisible.value = true
  }
}

const refreshAfterTask = async () => {
  await Promise.all([load(), loadOptions(), loadStatus()])
  if (drawerVisible.value && detail.value?.id) {
    await loadDetail(detail.value.id)
  }
}

const startTaskPolling = () => {
  stopTaskPolling()
  taskTimer = window.setInterval(async () => {
    const res = await getOllamaTaskStatus().catch(() => null)
    const st = res?.data
    if (!st) return
    task.value = st
    if (st.running) return
    stopTaskPolling()
    if (st.message) {
      ElMessage.success(st.message)
    }
    await refreshAfterTask()
  }, 1500)
}

const openDiscover = () => {
  discoverSession.value += 1
  // 手动打开时先清掉旧状态，弹窗展示「配置」而不是上次的结果
  task.value = null
  discoverVisible.value = true
}

const openUpdate = () => {
  updateSession.value += 1
  task.value = null
  updateVisible.value = true
}

const onDiscoverStart = async (keywords: string[]) => {
  taskStarting.value = true
  try {
    const res = await startOllamaDiscover(keywords)
    if (res.data) task.value = res.data
    startTaskPolling()
  } catch {
    /* 提示已在请求层 */
  } finally {
    taskStarting.value = false
  }
}

const runUpdate = async (request: OllamaUpdateRequest) => {
  taskStarting.value = true
  try {
    const res = await startOllamaUpdate(request)
    if (res.data) task.value = res.data
    if (res.data?.running) {
      startTaskPolling()
    } else {
      await refreshAfterTask()
    }
  } catch {
    /* 提示已在请求层 */
  } finally {
    taskStarting.value = false
  }
}

const onUpdateStart = (request: OllamaUpdateRequest) => void runUpdate(request)

/** 只重试失败的模型：父组件把 id 列表交给后端，进度/结果弹窗会自动切回运行态 */
const onUpdateRetry = (ids: number[]) => void runUpdate({ ids })

/** 结果弹窗关闭：记下 finishedAt，切菜单回来不再重复弹出 */
const onTaskDialogClose = () => {
  closedFinishedAt.value = task.value?.finishedAt ?? null
}

onMounted(async () => {
  await Promise.all([loadOptions(), load()])
  await loadStatus()
  await loadTask()
})

onActivated(async () => {
  // 切菜单回来：立即同步一次作业状态（运行中的进度弹窗会继续保持打开）
  await loadTask()
})

// 页面被 keep-alive 缓存后切走只会触发 onDeactivated：
// 停止前端轮询（服务端作业继续跑），回来时 onActivated 立即拉最新进度。
onDeactivated(stopTaskPolling)
onUnmounted(stopTaskPolling)
</script>

<style scoped lang="scss">
.ollama-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px 20px;
  box-sizing: border-box;
  gap: 14px;

  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;

    .header-left {
      h1 {
        display: flex;
        align-items: center;
        gap: 8px;
        margin: 0;
        font-size: 20px;
        font-weight: 600;
        color: var(--ev-text-primary);
      }

      .sub {
        margin: 4px 0 0;
        font-size: 12px;
        color: var(--ev-text-muted);
      }
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 20px;

      .stat {
        display: flex;
        flex-direction: column;
        align-items: center;

        .stat-num {
          font-size: 16px;
          font-weight: 700;
          color: var(--ev-primary);
        }

        .stat-label {
          font-size: 12px;
          color: var(--ev-text-muted);
        }
      }
    }
  }

  .filter-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .table-wrap {
    flex: 1;
    overflow: auto;

    .model-cell {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .model-name {
      font-weight: 600;
      color: var(--ev-text-primary);
      cursor: pointer;
    }

    .model-desc {
      margin-top: 2px;
      font-size: 12px;
      color: var(--ev-text-muted);
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .cap-tag {
      margin-right: 4px;
    }

    .muted {
      color: var(--ev-text-muted);
    }
  }

  .table-footer {
    display: flex;
    justify-content: flex-end;
  }
}

.detail {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .detail-tags {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }

  .detail-desc {
    margin: 0;
    font-size: 13px;
    line-height: 1.6;
    color: var(--ev-text-secondary);
    word-break: break-word;
  }

  .detail-meta {
    display: flex;
    align-items: center;
    gap: 18px;
    flex-wrap: wrap;
    font-size: 12px;
    color: var(--ev-text-muted);

    a {
      color: var(--ev-primary);
      text-decoration: none;

      &:hover {
        text-decoration: underline;
      }
    }
  }

  .detail-command {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    border-radius: 8px;
    background: var(--el-fill-color-light);

    .cmd-text {
      flex: 1;
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      font-size: 13px;
      word-break: break-all;
    }
  }

  .section-title {
    margin: 6px 0 0;
    font-size: 14px;
    font-weight: 600;
    color: var(--ev-text-primary);
  }

  .tag-cell {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .tag-name {
    font-weight: 600;
  }

  .tag-digest {
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 11px;
    color: var(--ev-text-muted);
  }
}
</style>
