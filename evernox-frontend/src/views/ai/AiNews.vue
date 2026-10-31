<template>
  <div class="ai-news-page">
    <div class="toolbar">
      <div class="filters">
        <el-select v-model="filterSource" placeholder="全部来源" clearable style="width: 120px" @change="reload">
          <el-option v-for="s in sourceOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-select v-model="filterTag" placeholder="全部标签" clearable style="width: 130px" @change="reload">
          <el-option v-for="t in tagOptions" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-select v-model="sort" style="width: 100px" @change="reload">
          <el-option label="最新" value="time" />
          <el-option label="热度" value="score" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="~"
          start-placeholder="发布起"
          end-placeholder="止"
          :disabled="onlyFav"
          :clearable="true"
          style="width: 250px"
          @change="reload"
        />
        <el-input
          v-model="keyword"
          placeholder="搜索标题关键词"
          clearable
          style="width: 240px"
          @keyup.enter="reload"
          @clear="reload"
        >
          <template #append>
            <el-button @click="reload">搜索</el-button>
          </template>
        </el-input>
      </div>
      <el-switch v-model="onlyFav" inline-prompt active-text="我的收藏" @change="onOnlyFavChange" />
    </div>

    <div v-loading="loading" class="news-list">
      <el-empty v-if="!loading && items.length === 0" description="暂无资讯，稍后再来看看" />
      <div v-for="it in items" :key="it.id" class="news-item">
        <div class="item-head">
          <span class="source-badge" :class="`src-${it.source}`">{{ sourceLabel(it.source) }}</span>
          <span class="item-time">{{ formatTime(it.publishedAt) }}</span>
          <el-button class="fav-btn" size="small" circle text @click="toggleFav(it)">
            <el-icon :class="{ 'is-fav': it.favorited }">
              <StarFilled v-if="it.favorited" />
              <Star v-else />
            </el-icon>
          </el-button>
        </div>

        <a :href="it.url" target="_blank" rel="noopener noreferrer" class="item-title" :title="it.title">{{ displayTitle(it) }}</a>

        <div v-if="it.titleZh && it.titleZh !== it.title" class="item-original">{{ it.title }}</div>

        <div v-if="displaySummary(it)" class="item-summary">{{ displaySummary(it) }}</div>

        <div class="item-foot">
          <el-tag size="small" effect="plain" :type="tagType(it.tag)">{{ tagLabel(it.tag) }}</el-tag>
          <span v-if="it.score > 0" class="item-score">
            <el-icon><TrendCharts /></el-icon>
            <span>{{ it.score }}</span>
          </span>
        </div>
      </div>
    </div>

    <div v-if="total > 0" class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="load"
        @size-change="reload"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAuthDialogStore } from '@/stores/authDialog'
import {
  getAiNewsList,
  getAiNewsFavorites,
  favoriteAiNews,
  unfavoriteAiNews,
  type AiNewsItem,
} from '@/api/aiNews'

const loading = ref(false)
const authDialog = useAuthDialogStore()
const items = ref<AiNewsItem[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filterSource = ref('')
const filterTag = ref('')
const keyword = ref('')
const sort = ref('time')
const dateRange = ref<[string, string] | null>(null)
const onlyFav = ref(false)

const sourceOptions = [{ label: 'HN', value: 'hackernews' }]

const tagOptions = [
  { label: '模型', value: 'model' },
  { label: 'Agent', value: 'agent' },
  { label: '工具', value: 'tool' },
  { label: 'IDE', value: 'ide' },
  { label: '桌面应用', value: 'desktop' },
  { label: '评测', value: 'benchmark' },
  { label: '性价比', value: 'cost' },
  { label: '其他', value: 'other' },
]

const sourceLabel = (s: string) => sourceOptions.find((o) => o.value === s)?.label ?? s
const tagLabel = (t: string) => tagOptions.find((o) => o.value === t)?.label ?? t

const displayTitle = (it: AiNewsItem) => it.titleZh || it.title
const displaySummary = (it: AiNewsItem) => it.summaryZh || it.summary

const tagType = (t: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' => {
  switch (t) {
    case 'model': return 'primary'
    case 'agent': return 'success'
    case 'tool': return 'warning'
    case 'ide': return 'danger'
    case 'desktop': return 'info'
    case 'benchmark': return 'info'
    case 'cost': return 'warning'
    default: return 'info'
  }
}

const formatTime = (s: string) => {
  if (!s) return ''
  const d = new Date(s.replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return s
  const diff = Date.now() - d.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const load = async () => {
  loading.value = true
  try {
    const res = onlyFav.value
      ? await getAiNewsFavorites({ page: page.value, size: size.value })
      : await getAiNewsList({
          source: filterSource.value || undefined,
          tag: filterTag.value || undefined,
          keyword: keyword.value || undefined,
          startDate: dateRange.value?.[0],
          endDate: dateRange.value?.[1],
          sort: sort.value,
          page: page.value,
          size: size.value,
        })
    items.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

const reload = () => {
  page.value = 1
  void load()
}

const toggleFav = (it: AiNewsItem) => authDialog.requireAuth(() => void doToggleFav(it))

const doToggleFav = async (it: AiNewsItem) => {
  try {
    if (it.favorited) {
      await unfavoriteAiNews(it.id)
      it.favorited = false
      if (onlyFav.value) {
        items.value = items.value.filter((x) => x.id !== it.id)
        total.value = Math.max(0, total.value - 1)
      }
    } else {
      await favoriteAiNews(it.id)
      it.favorited = true
    }
  } catch {
    /* 提示已在请求层 */
  }
}

const onOnlyFavChange = (value: string | number | boolean) => {
  if (!value) {
    reload()
    return
  }
  const ok = authDialog.requireAuth(() => {
    onlyFav.value = true
    // 收藏列表按收藏时间排序，不支持发布日期区间；切换时清掉避免误解
    dateRange.value = null
    reload()
  })
  if (!ok) onlyFav.value = false
}

onMounted(() => {
  void load()
})
</script>

<style scoped lang="scss">
.ai-news-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px 20px;
  box-sizing: border-box;
  gap: 16px;

  .toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
  }

  .filters {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;

    /* 输入框 + 搜索按钮的组合：全局 .el-input__wrapper 的圆角带 !important，
       会把分组右内角也做成圆的，和 append 的直角拼在一起就错位；
       这里显式拆成「左圆右平 + 左平右圆」，并让按钮融进 append 底色 */
    :deep(.el-input-group) {
      .el-input__wrapper {
        border-radius: var(--ev-radius-md) 0 0 var(--ev-radius-md) !important;
      }

      .el-input-group__append {
        padding: 0 !important;
        border: none !important;
        border-radius: 0 var(--ev-radius-md) var(--ev-radius-md) 0 !important;
        background: var(--ev-bg-tint) !important;
        box-shadow: 0 0 0 1px var(--ev-border-default) inset !important;
        overflow: hidden;
      }

      .el-input-group__append .el-button {
        height: 100%;
        margin: 0 !important;
        padding: 0 16px !important;
        border: none !important;
        border-radius: 0 !important;
        background: transparent !important;
        box-shadow: none !important;
        color: var(--ev-primary) !important;
        font-weight: 600 !important;

        &:hover {
          background: var(--ev-bg-tint-strong) !important;
        }
      }
    }
  }

  .news-list {
    flex: 1;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding-right: 4px;
  }

  .news-item {
    border: 1px solid var(--ev-border-subtle);
    border-radius: 12px;
    padding: 14px 16px;
    background: var(--el-bg-color);
    transition: box-shadow 0.2s, border-color 0.2s, transform 0.2s;

    &:hover {
      border-color: var(--ev-primary);
      box-shadow: 0 6px 18px rgba(0, 0, 0, 0.06);
      transform: translateY(-1px);
    }
  }

  .item-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }

  .source-badge {
    font-size: 12px;
    font-weight: 600;
    line-height: 1;
    padding: 3px 8px;
    border-radius: 4px;

    &.src-hackernews { color: #ff6600; background: rgba(255, 102, 0, 0.12); }
  }

  .item-time {
    margin-left: auto;
    font-size: 12px;
    color: var(--ev-text-muted);
    white-space: nowrap;
  }

  .fav-btn {
    flex-shrink: 0;
    color: var(--ev-text-muted);

    .is-fav {
      color: #f7ba2a;
    }
  }

  .item-title {
    display: block;
    font-size: 15px;
    font-weight: 600;
    line-height: 1.5;
    color: var(--ev-text-primary);
    text-decoration: none;
    word-break: break-word;

    &:hover {
      color: var(--ev-primary);
    }
  }

  .item-original {
    margin-top: 4px;
    font-size: 12px;
    color: var(--ev-text-muted);
    line-height: 1.5;
    word-break: break-word;
  }

  .item-summary {
    margin-top: 6px;
    font-size: 13px;
    color: var(--ev-text-secondary);
    line-height: 1.6;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .item-foot {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 10px;
  }

  .item-score {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: var(--ev-text-muted);
  }

  .pager {
    display: flex;
    justify-content: flex-end;
  }
}
</style>
