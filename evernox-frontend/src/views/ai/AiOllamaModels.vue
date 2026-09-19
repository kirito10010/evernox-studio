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
        <el-button v-if="userStore.isAdmin" type="primary" :loading="status.running" @click="handleSync">
          {{ status.running ? '同步中…' : '立即同步' }}
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
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <div class="table-wrap">
      <el-table :data="models" v-loading="loading" row-key="id" @row-click="onRowClick">
        <el-table-column label="模型" min-width="260">
          <template #default="{ row }">
            <div class="model-cell">
              <span class="model-name">{{ row.name }}</span>
              <el-tag v-if="row.isAbliterated === 1" size="small" type="warning" effect="plain">abliterated</el-tag>
              <el-tag v-if="row.vendor === 'community'" size="small" type="info" effect="plain">社区</el-tag>
            </div>
            <div v-if="row.description" class="model-desc">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="厂商" width="140">
          <template #default="{ row }">{{ row.vendorLabel || '-' }}</template>
        </el-table-column>
        <el-table-column label="参数量" width="130">
          <template #default="{ row }">
            <span v-if="row.sizes">{{ row.sizes.split(',').join(' / ') }}</span>
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
              v-for="c in splitCapabilities(row.capabilities)"
              :key="c"
              size="small"
              effect="plain"
              class="cap-tag"
            >
              {{ capabilityLabel(c) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="官网更新" width="120" align="center">
          <template #default="{ row }">{{ formatDate(row.sourceUpdatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click.stop="onRowClick(row)">查看变体</el-button>
            <el-button size="small" text @click.stop="copyCommand(row.defaultCommand)">复制命令</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && models.length === 0" description="暂无模型数据" />
    </div>

    <div class="table-footer">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="reload"
      />
    </div>

    <el-drawer v-model="drawerVisible" :title="detail?.name || '模型详情'" size="62%">
      <div v-if="detail" v-loading="detailLoading" class="detail">
        <div class="detail-tags">
          <el-tag v-for="c in splitCapabilities(detail.capabilities)" :key="c" size="small" effect="plain">
            {{ capabilityLabel(c) }}
          </el-tag>
          <el-tag v-if="detail.vendorLabel" size="small" type="info" effect="plain">{{ detail.vendorLabel }}</el-tag>
          <el-tag v-if="detail.isAbliterated === 1" size="small" type="warning" effect="plain">abliterated</el-tag>
        </div>

        <p v-if="detail.description" class="detail-desc">{{ detail.description }}</p>

        <div class="detail-meta">
          <span>下载量：{{ detail.pullsText || detail.pulls }}</span>
          <span>参数量：{{ detail.sizes ? detail.sizes.split(',').join(' / ') : '-' }}</span>
          <span>官方更新：{{ formatDate(detail.sourceUpdatedAt) }}</span>
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  getOllamaFilters,
  getOllamaModelDetail,
  getOllamaModels,
  getOllamaSyncStatus,
  triggerOllamaSync,
  type OllamaFilterOptions,
  type OllamaModel,
  type OllamaSyncStatus,
} from '@/api/ollamaModel'

const userStore = useUserStore()

const CAPABILITY_LABELS: Record<string, string> = {
  vision: '视觉',
  tools: '工具调用',
  thinking: '思考',
  embedding: '向量',
  cloud: '云端',
}

const models = ref<OllamaModel[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const filters = reactive({
  keyword: '',
  vendor: '',
  size: '',
  capability: '',
  version: 'all' as 'all' | 'standard' | 'abliterated',
  sort: 'popular' as 'popular' | 'new' | 'size',
})

const options = ref<OllamaFilterOptions>({ vendors: [], sizes: [], capabilities: [] })
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

let pollTimer: number | null = null

const capabilityLabel = (c: string) => CAPABILITY_LABELS[c] ?? c
const splitCapabilities = (caps: string | null) => (caps ? caps.split(',').filter(Boolean) : [])

const syncText = computed(() => (status.value.lastRunAt ? formatDate(status.value.lastRunAt) : '未同步'))

const formatDate = (s: string | null) => {
  if (!s) return '-'
  const d = new Date(s.replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return s
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const load = async () => {
  loading.value = true
  try {
    const res = await getOllamaModels({
      keyword: filters.keyword || undefined,
      vendor: filters.vendor || undefined,
      size: filters.size || undefined,
      capability: filters.capability || undefined,
      abliterated: filters.version === 'abliterated' ? true : undefined,
      sort: filters.sort,
      page: page.value,
      pageSize: pageSize.value,
    })
    models.value = res.data?.records ?? []
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
  filters.vendor = ''
  filters.size = ''
  filters.capability = ''
  filters.version = 'all'
  filters.sort = 'popular'
  reload()
}

const loadOptions = async () => {
  const res = await getOllamaFilters()
  options.value = res.data ?? { vendors: [], sizes: [], capabilities: [] }
}

const loadStatus = async () => {
  const res = await getOllamaSyncStatus()
  if (res.data) {
    status.value = res.data
  }
  return status.value
}

/** 打开详情抽屉；行类型来自 el-table 插槽，收窄成 OllamaModel */
const onRowClick = async (row: unknown) => {
  const model = row as OllamaModel
  if (!model || typeof model.id !== 'number') {
    return
  }
  drawerVisible.value = true
  detailLoading.value = true
  try {
    const res = await getOllamaModelDetail(model.id)
    detail.value = res.data ?? null
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

const copyCommand = async (text: string | null | undefined) => {
  if (!text) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    ElMessage.success(`已复制：${text}`)
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

const stopPolling = () => {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

const startPolling = () => {
  stopPolling()
  // 首跑要逐个抓变体页（200+ 个模型），耗时可能十几分钟，这里给足轮询窗口
  const maxTicks = 240
  let ticks = 0
  pollTimer = window.setInterval(async () => {
    ticks++
    const s = await loadStatus().catch(() => null)
    if (!s) return
    if (!s.running || ticks >= maxTicks) {
      stopPolling()
      if (s.message) {
        if (s.lastSuccess) {
          ElMessage.success(s.message)
        } else {
          ElMessage.warning(s.message)
        }
      }
      await Promise.all([load(), loadOptions()])
    }
  }, 5000)
}

const handleSync = async () => {
  try {
    await triggerOllamaSync()
    ElMessage.success('已开始同步，请稍候查看结果')
    await loadStatus()
    startPolling()
  } catch {
    /* 提示已在请求层 */
  }
}

onMounted(async () => {
  await Promise.all([loadOptions(), load()])
  await loadStatus()
  if (status.value.running) {
    startPolling()
  }
})

onUnmounted(stopPolling)
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
