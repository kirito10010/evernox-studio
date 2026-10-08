<template>
  <div class="arena-page">
    <div class="page-header">
      <div class="header-left">
        <h1>
          <el-icon><Trophy /></el-icon>
          <span>Code Arena 模型排行榜</span>
        </h1>
        <p class="sub">
          数据来源 arena.ai · Code Arena | WebDev · 分数为 Elo 式 Arena 分数，价格单位为美元 / 百万 token
        </p>
      </div>
      <div class="header-right">
        <div class="stat">
          <span class="stat-num">{{ status.rowTotal }}</span>
          <span class="stat-label">收录行数</span>
        </div>
        <div class="stat">
          <span class="stat-num">{{ syncText }}</span>
          <span class="stat-label">最近同步</span>
        </div>
        <el-button v-if="userStore.isAdmin" type="primary" :loading="status.running" @click="handleSync">
          {{ status.running ? '同步中…' : '立即同步' }}
        </el-button>
        <el-button
          v-if="userStore.isAdmin"
          :loading="downloadingFetcher"
          @click="handleDownloadFetcher"
        >
          下载抓取脚本
        </el-button>
        <el-tooltip v-if="userStore.isAdmin" placement="bottom-end">
          <template #content>
            <div class="import-tip">
              服务器 IP 被官网风控拦截（HTTP 403），抓取要在能打开 arena.ai 的电脑上做：<br />
              <b>①</b> 点左边「下载抓取脚本」拿到 <b>fetch-arena-leaderboard.bat</b>；<br />
              <b>②</b> 双击运行它（首次运行若被 SmartScreen 拦，点「更多信息 → 仍要运行」），<br />
              　 它会在同目录生成 <b>arena-lb-日期-时间.zip</b>；<br />
              <b>③</b> 回到这里点「导入数据包」，选那个 zip。
            </div>
          </template>
          <el-upload
            :auto-upload="false"
            accept=".zip"
            :show-file-list="false"
            :on-change="onPackageChange"
          >
            <el-button :loading="importing">导入数据包</el-button>
          </el-upload>
        </el-tooltip>
      </div>
    </div>

    <div v-if="status.running" class="progress-bar">
      <el-progress :percentage="progressPercent" :stroke-width="10" striped striped-flow />
      <span class="progress-text">{{ progressText }}</span>
    </div>

    <div class="category-bar">
      <div v-for="group in categoryGroups" :key="group.key" class="category-row">
        <span class="category-label">{{ group.label }}</span>
        <el-radio-group v-model="category" size="small" @change="onCategoryChange">
          <el-radio-button v-for="c in group.items" :key="c.slug" :value="c.slug">
            <span :title="c.labelEn">{{ c.label }}</span>
            <span class="category-count">{{ c.modelCount }}</span>
          </el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="filters.org" placeholder="全部开发方" clearable style="width: 190px" @change="applyFilters">
        <el-option v-for="o in options.orgs" :key="o.key" :label="`${o.label} (${o.count})`" :value="o.key" />
      </el-select>
      <el-select v-model="filters.priceType" style="width: 130px" @change="onPriceTypeChange">
        <el-option label="按输入价格" value="input" />
        <el-option label="按输出价格" value="output" />
      </el-select>
      <div class="price-range">
        <el-input-number
          v-model="filters.minPrice"
          :min="0"
          :precision="2"
          :controls="false"
          placeholder="最低价"
          style="width: 110px"
          @change="applyFilters"
        />
        <span class="range-sep">~</span>
        <el-input-number
          v-model="filters.maxPrice"
          :min="0"
          :precision="2"
          :controls="false"
          placeholder="最高价"
          style="width: 110px"
          @change="applyFilters"
        />
        <span class="range-unit">$ / 百万 token</span>
      </div>
      <el-button
        v-for="r in QUICK_RANGES"
        :key="r.label"
        size="small"
        :type="isQuickActive(r) ? 'primary' : 'default'"
        @click="applyQuickRange(r)"
      >
        {{ r.label }}
      </el-button>
      <el-input
        v-model="filters.keyword"
        placeholder="搜索模型名"
        clearable
        style="width: 180px"
        @keyup.enter="applyFilters"
        @clear="applyFilters"
      />
      <el-button @click="resetFilters">重置</el-button>
      <span v-if="hasPriceFilter" class="price-hint">筛选价格时，无价格数据的模型不显示</span>
    </div>

    <div class="table-wrap">
      <el-table :data="items" v-loading="loading" stripe row-key="id">
        <el-table-column label="排名" width="80" align="center">
          <template #default="{ row }">
            <span class="rank" :class="{ top3: row.rank != null && row.rank <= 3 }">{{ row._rank }}</span>
          </template>
        </el-table-column>
        <el-table-column label="排名区间" width="100" align="center">
          <template #default="{ row }">
            <span class="muted">{{ row._spread }}</span>
          </template>
        </el-table-column>
        <el-table-column label="模型" min-width="230">
          <template #default="{ row }">
            <div class="model-cell">
              <a v-if="row.modelUrl" class="model-name" :href="row.modelUrl" target="_blank" rel="noopener noreferrer">
                {{ row.modelName }}
              </a>
              <span v-else class="model-name">{{ row.modelName }}</span>
              <el-tooltip v-if="row.isPreliminary === 1" content="该模型评测仍在进行中，分数可能变化" placement="top">
                <el-tag size="small" type="info" effect="plain">Preliminary</el-tag>
              </el-tooltip>
            </div>
            <div class="model-meta">{{ row._orgLicense }}</div>
          </template>
        </el-table-column>
        <el-table-column label="分数" width="140" align="center">
          <template #default="{ row }">
            <span class="score">{{ row._score }}</span>
            <span class="ci">{{ row._ci }}</span>
          </template>
        </el-table-column>
        <el-table-column label="投票数" width="100" align="right">
          <template #default="{ row }">{{ row._votes }}</template>
        </el-table-column>
        <el-table-column label="输入价格" width="110" align="right">
          <template #default="{ row }">{{ row._inputPrice }}</template>
        </el-table-column>
        <el-table-column label="输出价格" width="110" align="right">
          <template #default="{ row }">{{ row._outputPrice }}</template>
        </el-table-column>
        <el-table-column label="上下文" width="110" align="center">
          <template #default="{ row }">{{ row.contextText || '-' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && items.length === 0" description="暂无数据" />
    </div>

    <!-- 前端分页：接口一次返回整类榜单（可能数百行），这里只渲染当前页，避免挂载时同步渲染大量 DOM -->
    <div v-if="total > PAGE_SIZE" class="table-footer">
      <el-pagination
        v-model:current-page="page"
        :page-size="PAGE_SIZE"
        :total="total"
        layout="total, prev, pager, next, jumper"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onDeactivated, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { formatDate } from '@/utils/format'
import type { UploadFile } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  downloadArenaFetcher,
  getArenaCategories,
  getArenaFilters,
  getArenaItems,
  getArenaSyncStatus,
  importArenaPackage,
  triggerArenaSync,
  type ArenaCategoryOption,
  type ArenaFilterOptions,
  type ArenaRankItem,
  type ArenaSyncStatus,
} from '@/api/arenaRank'

// 显式声明组件名：MainLayout 的 <keep-alive :include="..."> 按组件名匹配缓存，
// <script setup> 的推断名虽然通常可用，但显式声明更稳。
defineOptions({ name: 'AiArenaLeaderboard' })

const userStore = useUserStore()

const EMPTY_OPTIONS: ArenaFilterOptions = {
  orgs: [],
  minInput: null,
  maxInput: null,
  minOutput: null,
  maxOutput: null,
}

interface QuickRange {
  label: string
  min: number | null
  max: number | null
}

const QUICK_RANGES: QuickRange[] = [
  { label: '全部', min: null, max: null },
  { label: '≤ $1', min: null, max: 1 },
  { label: '$1 ~ $5', min: 1, max: 5 },
  { label: '≥ $5', min: 5, max: null },
]

const categories = ref<ArenaCategoryOption[]>([])
/** 每页行数：整类榜单可能有数百行，分页后一次只渲染 50 行 */
const PAGE_SIZE = 50

/**
 * 展示行：把每行的显示字符串在**数据加载时算一次**。
 * 原来是在模板里直接调函数（每行 8 个：rank/spread/orgLicense/score/ci/votes/价格×2），
 * 每次重渲染都要为每一行重算一遍；分页前有数百行时这笔开销很可观。
 */
interface ArenaRow extends ArenaRankItem {
  _rank: string
  _spread: string
  _orgLicense: string
  _score: string
  _ci: string
  _votes: string
  _inputPrice: string
  _outputPrice: string
}

const items = ref<ArenaRow[]>([])
const page = ref(1)
const total = ref(0)
/** 接口返回的完整列表（未分页）。翻页只切展示，不重新请求 */
let allRows: ArenaRankItem[] = []
const options = ref<ArenaFilterOptions>({ ...EMPTY_OPTIONS })
const loading = ref(false)
const importing = ref(false)
const downloadingFetcher = ref(false)

const category = ref('overall')
const filters = reactive({
  org: '',
  priceType: 'input' as 'input' | 'output',
  minPrice: null as number | null,
  maxPrice: null as number | null,
  keyword: '',
})

const status = ref<ArenaSyncStatus>({
  running: false,
  phase: null,
  currentCategory: null,
  categoryTotal: 0,
  categoryDone: 0,
  rowTotal: 0,
  added: 0,
  updated: 0,
  removed: 0,
  lastRunAt: null,
  lastSuccess: false,
  durationMs: 0,
  message: null,
})

let pollTimer: number | null = null

const categoryGroups = computed(() => [
  { key: 'category', label: '分类', items: categories.value.filter((c) => c.group === 'category') },
  { key: 'domain', label: '领域', items: categories.value.filter((c) => c.group === 'domain') },
])

const syncText = computed(() => (status.value.lastRunAt ? formatDate(status.value.lastRunAt, '-') : '未同步'))

const progressPercent = computed(() => {
  if (!status.value.categoryTotal) return 0
  return Math.min(100, Math.round((status.value.categoryDone * 100) / status.value.categoryTotal))
})

const progressText = computed(() => {
  const s = status.value
  const phase = s.phase ?? '同步中'
  const at = s.currentCategory ? ` · ${s.currentCategory}` : ''
  return `${phase}${at}（${s.categoryDone}/${s.categoryTotal} 分类）`
})

const hasPriceFilter = computed(() => filters.minPrice != null || filters.maxPrice != null)

const rankText = (r: number | null) => (r == null ? '-' : String(r))

/** el-table 插槽的 row 类型是 DefaultRow，统一在函数内收窄，省去模板里逐处断言 */
const asRank = (raw: unknown) => raw as ArenaRankItem

const spreadText = (raw: unknown) => {
  const row = asRank(raw)
  if (row.rankSpreadMin == null || row.rankSpreadMax == null) return '-'
  return row.rankSpreadMin === row.rankSpreadMax
    ? String(row.rankSpreadMin)
    : `${row.rankSpreadMin} ~ ${row.rankSpreadMax}`
}

const scoreText = (raw: unknown) => {
  const score = asRank(raw).score
  return score == null ? '-' : String(score)
}

const ciText = (raw: unknown) => {
  const row = asRank(raw)
  return row.scoreCiPlus == null || row.scoreCiMinus == null ? '' : ` +${row.scoreCiPlus}/-${row.scoreCiMinus}`
}

const votesText = (raw: unknown) => {
  const votes = asRank(raw).votes
  return votes == null ? '-' : votes.toLocaleString()
}

const priceText = (v: number | null) => (v == null ? '-' : `$${v}`)

const orgLicense = (raw: unknown) => {
  const row = asRank(raw)
  if (row.org && row.license) return `${row.org} · ${row.license}`
  return row.org || row.license || '-'
}

/** 把接口行转成展示行（显示字符串只算一次） */
const toRow = (r: ArenaRankItem): ArenaRow => ({
  ...r,
  _rank: rankText(r.rank),
  _spread: spreadText(r),
  _orgLicense: orgLicense(r),
  _score: scoreText(r),
  _ci: ciText(r),
  _votes: votesText(r),
  _inputPrice: priceText(r.inputPrice),
  _outputPrice: priceText(r.outputPrice),
})

/** 按当前页码切出要渲染的那一页 */
const slicePage = () => {
  const start = (page.value - 1) * PAGE_SIZE
  items.value = allRows.slice(start, start + PAGE_SIZE).map(toRow)
}

const onPageChange = () => {
  slicePage()
}

const isQuickActive = (r: QuickRange) => filters.minPrice === r.min && filters.maxPrice === r.max

const loadCategories = async () => {
  const res = await getArenaCategories()
  categories.value = res.data ?? []
  if (!categories.value.some((c) => c.slug === category.value)) {
    category.value = categories.value[0]?.slug ?? 'overall'
  }
}

const loadItems = async () => {
  loading.value = true
  try {
    const res = await getArenaItems({
      category: category.value,
      org: filters.org || undefined,
      priceType: filters.priceType,
      minPrice: filters.minPrice ?? undefined,
      maxPrice: filters.maxPrice ?? undefined,
      keyword: filters.keyword || undefined,
    })
    allRows = res.data ?? []
    total.value = allRows.length
    // 换了筛选条件后结果变少，当前页可能已越界 → 回到第 1 页
    if ((page.value - 1) * PAGE_SIZE >= total.value) {
      page.value = 1
    }
    slicePage()
  } finally {
    loading.value = false
  }
}

const loadOptions = async () => {
  const res = await getArenaFilters(category.value)
  options.value = res.data ?? { ...EMPTY_OPTIONS }
}

const loadStatus = async () => {
  const res = await getArenaSyncStatus()
  if (res.data) status.value = res.data
  return status.value
}

const applyFilters = () => {
  const { minPrice, maxPrice } = filters
  if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
    ElMessage.warning('价格下限不能大于上限')
    return
  }
  page.value = 1
  void loadItems()
}

const onCategoryChange = () => {
  filters.org = ''
  filters.minPrice = null
  filters.maxPrice = null
  page.value = 1
  void Promise.all([loadItems(), loadOptions()])
}

const onPriceTypeChange = () => {
  filters.minPrice = null
  filters.maxPrice = null
  page.value = 1
  void loadItems()
}

const applyQuickRange = (r: QuickRange) => {
  filters.minPrice = r.min
  filters.maxPrice = r.max
  page.value = 1
  void loadItems()
}

const resetFilters = () => {
  filters.org = ''
  filters.priceType = 'input'
  filters.minPrice = null
  filters.maxPrice = null
  filters.keyword = ''
  page.value = 1
  void loadItems()
}

const stopPolling = () => {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

const startPolling = () => {
  stopPolling()
  // 12 个分类页，通常 1 分钟内抓完，这里给 3 分钟轮询窗口
  const maxTicks = 60
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
      await Promise.all([loadCategories(), loadItems(), loadOptions()])
    }
  }, 3000)
}

const handleSync = async () => {
  await triggerArenaSync()
  ElMessage.info('已开始同步，正在后台抓取…')
  await loadStatus()
  startPolling()
}

/**
 * 下载本地抓取脚本。
 *
 * 不能直接用 <a href>：接口要 JWT，而 <a> 不会带 Authorization 头（会 401），
 * 所以取回 blob 后自己造一个链接触发保存。
 */
const handleDownloadFetcher = async () => {
  downloadingFetcher.value = true
  try {
    const blob = await downloadArenaFetcher()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'fetch-arena-leaderboard.bat'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('脚本已下载，双击运行即可抓取')
  } finally {
    downloadingFetcher.value = false
  }
}

const onPackageChange = async (file: UploadFile) => {
  const raw = file.raw
  if (!raw) return
  importing.value = true
  try {
    const res = await importArenaPackage(raw)
    const s = res.data
    if (s?.lastSuccess) {
      ElMessage.success(s.message ?? '导入完成')
    } else {
      ElMessage.warning(s?.message ?? '导入未成功')
    }
    await Promise.all([loadCategories(), loadItems(), loadOptions(), loadStatus()])
  } finally {
    importing.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadStatus()])
  await Promise.all([loadItems(), loadOptions()])
  if (status.value.running) {
    startPolling()
  }
})

// 本页被 keep-alive 缓存后，切走只会触发 onDeactivated（不会 onUnmounted），
// 轮询必须在这里一起停掉，否则会在后台空转到结束。
onDeactivated(stopPolling)
onUnmounted(stopPolling)
</script>

<style scoped lang="scss">
.arena-page {
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

  .progress-bar {
    display: flex;
    align-items: center;
    gap: 12px;

    :deep(.el-progress) {
      flex: 1;
    }

    .progress-text {
      flex-shrink: 0;
      font-size: 12px;
      color: var(--ev-text-secondary);
    }
  }

  .category-bar {
    display: flex;
    flex-direction: column;
    gap: 8px;

    .category-row {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }

    .category-label {
      flex-shrink: 0;
      font-size: 13px;
      color: var(--ev-text-secondary);
    }

    .category-count {
      margin-left: 4px;
      font-size: 11px;
      color: var(--ev-text-muted);
    }
  }

  .filter-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;

    .price-range {
      display: flex;
      align-items: center;
      gap: 6px;

      .range-sep {
        color: var(--ev-text-muted);
      }

      .range-unit {
        font-size: 12px;
        color: var(--ev-text-muted);
        white-space: nowrap;
      }
    }

    .price-hint {
      font-size: 12px;
      color: var(--ev-text-muted);
    }
  }

  .table-wrap {
    flex: 1;
    overflow: auto;

    .rank {
      font-weight: 700;
      color: var(--ev-text-secondary);

      &.top3 {
        color: var(--ev-primary);
      }
    }

    .model-cell {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .model-name {
      font-weight: 600;
      color: var(--ev-text-primary);
      text-decoration: none;

      &:hover {
        text-decoration: underline;
      }
    }

    .model-meta {
      margin-top: 2px;
      font-size: 12px;
      color: var(--ev-text-muted);
    }

    .score {
      font-weight: 700;
      color: var(--ev-primary);
    }

    .ci {
      font-size: 12px;
      color: var(--ev-text-muted);
    }

    .muted {
      color: var(--ev-text-muted);
    }
  }

  .table-footer {
    display: flex;
    justify-content: flex-end;
    padding: 12px 4px 0;
  }
}
</style>
