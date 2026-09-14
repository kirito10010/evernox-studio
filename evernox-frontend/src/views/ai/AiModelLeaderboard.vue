<template>
  <div class="lb-page">
    <div class="lb-toolbar">
      <div class="lb-source">
        <el-button :type="source === 'aihot' ? 'primary' : 'default'" size="small" @click="setSource('aihot')">AI HOT</el-button>
        <el-button :type="source === 'zhizhi' ? 'primary' : 'default'" size="small" @click="setSource('zhizhi')">致知</el-button>
      </div>
      <el-tabs v-model="category" class="lb-tabs" @tab-change="load">
        <el-tab-pane v-for="c in currentCategories" :key="c.value" :label="c.label" :name="c.value" />
      </el-tabs>
    </div>

    <div v-if="source === 'zhizhi'" class="lb-filters">
      <div class="f-item">
        <span class="f-label">数据集</span>
        <el-select v-model="zhizhiMonth" size="small" class="f-month" @change="onMonthChange">
          <el-option v-for="m in zhizhiMonths" :key="m" :label="m" :value="m" />
        </el-select>
      </div>
      <div class="f-item">
        <span class="f-label">模型模式</span>
        <el-select v-model="inferenceFilter" size="small" class="f-select">
          <el-option label="全部" value="all" />
          <el-option label="思考" value="think" />
          <el-option label="非思考" value="non-think" />
        </el-select>
      </div>
      <div class="f-item">
        <span class="f-label">模型国家</span>
        <el-select v-model="countryFilter" size="small" class="f-select">
          <el-option label="全部" value="all" />
          <el-option label="中国" value="china" />
          <el-option label="美国" value="usa" />
          <el-option label="其他" value="other" />
        </el-select>
      </div>
      <div class="f-item">
        <span class="f-label">搜索</span>
        <el-input v-model="search" size="small" placeholder="模型名" clearable class="f-search" />
      </div>
    </div>

    <div v-loading="loading" class="lb-body">
      <!-- AI HOT 排行榜 -->
      <el-table v-if="source === 'aihot'" :data="aihotItems" stripe>
        <el-table-column label="排名" width="70" align="center">
          <template #default="{ row }">
            <span class="rank" :class="{ top3: row.rank && row.rank <= 3 }">{{ rankText(row.rank) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="模型" min-width="190">
          <template #default="{ row }">
            <div class="model-cell">
              <div class="model-name">{{ row.modelName }}</div>
              <div class="provider">{{ row.provider }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="releaseDate" label="上线日期" width="110" align="center" />
        <el-table-column label="评测证据" width="140" align="center">
          <template #default="{ row }">
            <div>{{ row.evidence }}</div>
            <div v-if="row.confidence" class="sub">{{ row.confidence }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="inputPrice" label="输入价格" width="120" align="center" />
        <el-table-column prop="outputPrice" label="输出价格" width="120" align="center" />
        <el-table-column label="共识指数" width="110" align="center">
          <template #default="{ row }">
            <span class="score">{{ row.score ?? '-' }}</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 致知排行榜 -->
      <el-table v-else :data="filteredZhizhiItems" stripe>
        <el-table-column label="排名" width="70" align="center">
          <template #default="{ row }">
            <span class="rank" :class="{ top3: row.rank && row.rank <= 3 }">{{ rankText(row.rank) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="模型" min-width="210">
          <template #default="{ row }">
            <div class="model-cell">
              <span class="model-name">{{ row.modelName }}</span>
              <el-tag v-if="row.think === 1" size="small" type="success" class="think-tag">推理</el-tag>
              <el-tag v-if="row.country" size="small" :type="countryType(row.country)" class="country-tag">
                {{ countryLabel(row.country) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="极限分数" width="90" align="center">
          <template #default="{ row }">{{ row.extremeScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="中位分数" width="90" align="center">
          <template #default="{ row }"><span class="score">{{ row.medianScore ?? '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="medianGap" label="中位差距" width="90" align="center" />
        <el-table-column prop="change" label="变更" width="80" align="center" />
        <el-table-column prop="avgTime" label="耗时(秒)" width="90" align="center" />
        <el-table-column prop="token" label="Token" width="90" align="center" />
        <el-table-column prop="testCost" label="测试成本" width="100" align="center" />
        <el-table-column prop="price" label="价格" width="100" align="center" />
        <el-table-column prop="releaseDate" label="发布时间" width="100" align="center" />
      </el-table>

      <el-empty v-if="!loading && currentItems.length === 0" description="暂无数据" />
    </div>

    <p class="lb-note">
      {{ source === 'aihot' ? '数据来源：AIHOT 模型排行榜 · 共识指数不是正确率 · 价格按每百万 Token 折算人民币' : '数据来源：致知 · 大模型智力长期追踪榜单 · 按中位分数排序' }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  getAiLeaderboard,
  getZhizhiRank,
  getZhizhiMonths,
  type AiModelRankItem,
  type AiZhizhiRankItem,
} from '@/api/aiNews'

type Source = 'aihot' | 'zhizhi'

const aiCategories = [
  { label: '综合', value: 'overall' },
  { label: '编程', value: 'coding' },
  { label: '推理', value: 'reasoning' },
  { label: '知识', value: 'knowledge' },
  { label: '办公', value: 'professional' },
]

const zhizhiCategories = [
  { label: '推理', value: 'logic' },
  { label: 'Agentic', value: 'code_v3' },
  { label: '视觉', value: 'vision' },
]

const source = ref<Source>('aihot')
const category = ref('overall')
const aihotItems = ref<AiModelRankItem[]>([])
const zhizhiItems = ref<AiZhizhiRankItem[]>([])
const loading = ref(false)

const zhizhiMonths = ref<string[]>([])
const zhizhiMonth = ref('')
const inferenceFilter = ref<'all' | 'think' | 'non-think'>('all')
const countryFilter = ref<'all' | 'china' | 'usa' | 'other'>('all')
const search = ref('')

const currentCategories = computed(() => (source.value === 'aihot' ? aiCategories : zhizhiCategories))
const currentItems = computed(() => (source.value === 'aihot' ? aihotItems.value : zhizhiItems.value))

const filteredZhizhiItems = computed(() => {
  let rows = zhizhiItems.value
  if (inferenceFilter.value === 'think') {
    rows = rows.filter((r) => r.think === 1)
  } else if (inferenceFilter.value === 'non-think') {
    rows = rows.filter((r) => r.think !== 1)
  }
  if (countryFilter.value !== 'all') {
    rows = rows.filter((r) => r.country === countryFilter.value)
  }
  const kw = search.value.trim().toLowerCase()
  if (kw) {
    rows = rows.filter((r) => r.modelName.toLowerCase().includes(kw))
  }
  return rows
})

const rankText = (r: number | null) => (r == null ? '-' : String(r).padStart(2, '0'))

const countryLabel = (c: string) => (c === 'china' ? '中国' : c === 'usa' ? '美国' : c === 'other' ? '其他' : '')
const countryType = (c: string): 'primary' | 'danger' | 'info' =>
  c === 'china' ? 'danger' : c === 'usa' ? 'primary' : 'info'

const setSource = (s: Source) => {
  if (source.value === s) return
  source.value = s
  category.value = s === 'aihot' ? 'overall' : 'logic'
  if (s === 'zhizhi') {
    inferenceFilter.value = 'all'
    countryFilter.value = 'all'
    search.value = ''
    zhizhiMonth.value = ''
    zhizhiMonths.value = []
  }
  void load()
}

const load = async () => {
  loading.value = true
  try {
    if (source.value === 'aihot') {
      const res = await getAiLeaderboard(category.value)
      aihotItems.value = res.data ?? []
    } else {
      const monthsRes = await getZhizhiMonths(category.value)
      zhizhiMonths.value = monthsRes.data ?? []
      if (!zhizhiMonths.value.includes(zhizhiMonth.value)) {
        zhizhiMonth.value = zhizhiMonths.value[0] ?? ''
      }
      const res = await getZhizhiRank(category.value, zhizhiMonth.value || undefined)
      zhizhiItems.value = res.data ?? []
    }
  } finally {
    loading.value = false
  }
}

const onMonthChange = () => {
  void load()
}

onMounted(() => {
  void load()
})
</script>

<style scoped lang="scss">
.lb-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px 20px;
  box-sizing: border-box;
  gap: 14px;

  .lb-toolbar {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;

    .lb-source {
      display: flex;
      gap: 8px;
    }

    .lb-tabs {
      flex: 1;

      :deep(.el-tabs__header) {
        margin-bottom: 0;
      }
    }
  }

  .lb-filters {
    display: flex;
    align-items: center;
    gap: 18px;
    flex-wrap: wrap;

    .f-item {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .f-label {
      font-size: 13px;
      color: var(--ev-text-secondary);
      white-space: nowrap;
    }

    .f-month {
      width: 120px;
    }

    .f-select {
      width: 110px;
    }

    .f-search {
      width: 160px;
    }
  }

  .lb-body {
    flex: 1;
    overflow: auto;
  }

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

    .model-name {
      font-weight: 600;
      color: var(--ev-text-primary);
    }

    .provider {
      font-size: 12px;
      color: var(--ev-text-muted);
    }
  }

  .think-tag {
    flex-shrink: 0;
  }

  .country-tag {
    flex-shrink: 0;
  }

  .sub {
    font-size: 12px;
    color: var(--ev-text-muted);
  }

  .score {
    font-weight: 700;
    color: var(--ev-primary);
  }

  .lb-note {
    margin: 0;
    font-size: 12px;
    color: var(--ev-text-muted);
  }
}
</style>
