<template>
  <div class="public-sites">
    <div class="page-header">
      <div class="header-text">
        <h2>网站导航</h2>
        <p>由社区成员分享、管理员审核通过的站点，点击卡片在新标签页打开</p>
      </div>
      <el-input
        v-model="keyword"
        class="search"
        placeholder="搜索站点名称、链接或介绍"
        clearable
        @input="onKeywordInput"
        @clear="applyFilters"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <div v-if="tags.length" class="tag-bar" :class="{ 'is-expanded': tagsExpanded }">
      <div ref="tagListRef" class="tag-list">
        <el-check-tag :checked="selectedTagIds.length === 0" @change="clearTags">全部</el-check-tag>
        <el-check-tag
          v-for="tag in sortedTags"
          :key="tag.id"
          :checked="selectedTagIds.includes(tag.id)"
          @change="toggleTag(tag.id)"
        >
          {{ tag.name }}
        </el-check-tag>
      </div>
      <button
        v-if="showTagToggle"
        class="tag-toggle"
        type="button"
        @click="toggleTagBar"
      >
        <span>{{ tagsExpanded ? '收起' : '展开' }}</span>
        <el-icon :size="12">
          <ArrowUp v-if="tagsExpanded" />
          <ArrowDown v-else />
        </el-icon>
      </button>
    </div>

    <div v-loading="loading" class="card-grid">
      <SiteCard
        v-for="site in sites"
        :key="site.id"
        :site="site"
        :loader="decryptImage"
      />
      <el-empty v-if="!loading && !sites.length" class="empty" description="还没有公开的站点分享" />
    </div>

    <div v-if="total > pageSize" class="pagination-wrap">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[12, 24, 48]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="loadSites"
        @size-change="applyFilters"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ArrowDown, ArrowUp, Search } from '@element-plus/icons-vue'
import SiteCard from '@/components/SiteCard.vue'
import { getPublicSites, getSiteTags } from '@/api/site'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import type { SiteLink, SiteTag } from '@/types/site'

const { decryptImage, clearCache } = useImageDecrypt()

const sites = ref<SiteLink[]>([])
const tags = ref<SiteTag[]>([])
const selectedTagIds = ref<number[]>([])
const keyword = ref('')
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(12)
const total = ref(0)

// ---------- 标签栏：按站点数排序 + 默认只占一行 ----------
const tagsExpanded = ref(false)
const tagOverflow = ref(false)
const tagListRef = ref<HTMLElement | null>(null)
let tagResizeObserver: ResizeObserver | null = null

/** 关联站点数从多到少；数量相同则保持后台 sort 顺序 */
const sortedTags = computed(() =>
  [...tags.value].sort((a, b) => (b.siteCount ?? 0) - (a.siteCount ?? 0))
)

/** 一行放不下时才出现「展开」；展开后始终保留「收起」 */
const showTagToggle = computed(() => tagOverflow.value || tagsExpanded.value)

const measureTagOverflow = () => {
  const el = tagListRef.value
  if (!el) return
  tagOverflow.value = el.scrollWidth > el.clientWidth + 1
}

const setupTagObserver = () => {
  tagResizeObserver?.disconnect()
  const el = tagListRef.value
  if (!el) return
  tagResizeObserver = new ResizeObserver(() => {
    if (!tagsExpanded.value) measureTagOverflow()
  })
  tagResizeObserver.observe(el)
  measureTagOverflow()
}

const toggleTagBar = async () => {
  tagsExpanded.value = !tagsExpanded.value
  await nextTick()
  measureTagOverflow()
}

const loadSites = async () => {
  loading.value = true
  try {
    const res = await getPublicSites({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      tagIds: selectedTagIds.value.length ? selectedTagIds.value.join(',') : undefined,
    })
    sites.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    /* 请求层已提示 */
  } finally {
    loading.value = false
  }
}

const loadTags = async () => {
  try {
    const res = await getSiteTags()
    tags.value = res.data || []
  } catch {
    /* 请求层已提示 */
  }
}

const applyFilters = () => {
  currentPage.value = 1
  void loadSites()
}

let keywordTimer: ReturnType<typeof setTimeout> | null = null
const onKeywordInput = () => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(applyFilters, 300)
}

const toggleTag = (id: number) => {
  const index = selectedTagIds.value.indexOf(id)
  if (index >= 0) selectedTagIds.value.splice(index, 1)
  else selectedTagIds.value.push(id)
  applyFilters()
}

const clearTags = () => {
  if (!selectedTagIds.value.length) return
  selectedTagIds.value = []
  applyFilters()
}

onMounted(() => {
  void loadTags().then(async () => {
    await nextTick()
    setupTagObserver()
  })
  void loadSites()
})

// ObjectURL 不回收会一直占内存
onUnmounted(() => {
  tagResizeObserver?.disconnect()
  clearCache()
})
</script>

<style scoped lang="scss">
.public-sites {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  h2 {
    margin: 0;
    font-size: 22px;
  }

  p {
    margin: 4px 0 0;
    font-size: 13px;
    color: var(--ev-text-muted, #90a4bb);
  }
}

.search {
  width: 280px;
}

.tag-bar {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.tag-list {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
  overflow: hidden;
  /* 折叠时最后一个标签可能被切断，右侧做一点渐隐提示「还有更多」 */
  mask-image: linear-gradient(to right, #000 calc(100% - 16px), transparent);
  -webkit-mask-image: linear-gradient(to right, #000 calc(100% - 16px), transparent);

  /* 不压缩标签，超出的直接裁掉，保证始终只有一行 */
  > * {
    flex: 0 0 auto;
  }
}

.tag-bar.is-expanded .tag-list {
  flex-wrap: wrap;
  overflow: visible;
  mask-image: none;
  -webkit-mask-image: none;
}

.tag-toggle {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 28px;
  padding: 0 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--ev-primary, #2f7cf6);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: var(--ev-bg-tint, rgba(47, 124, 246, 0.08));
  }
}

.card-grid {
  display: grid;
  /* 整卡即图的 4:3 图块，列宽小一点排布更像导航站 */
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 14px;
  min-height: 200px;
}

.empty {
  grid-column: 1 / -1;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
}
</style>

