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

    <div v-if="tags.length" class="tag-bar">
      <el-check-tag :checked="selectedTagIds.length === 0" @change="clearTags">全部</el-check-tag>
      <el-check-tag
        v-for="tag in tags"
        :key="tag.id"
        :checked="selectedTagIds.includes(tag.id)"
        @change="toggleTag(tag.id)"
      >
        {{ tag.name }}
      </el-check-tag>
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
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next, jumper"
        @current-change="loadSites"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
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
const pageSize = 12
const total = ref(0)

const loadSites = async () => {
  loading.value = true
  try {
    const res = await getPublicSites({
      page: currentPage.value,
      size: pageSize,
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
  void loadTags()
  void loadSites()
})

// ObjectURL 不回收会一直占内存
onUnmounted(clearCache)
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
  flex-wrap: wrap;
  gap: 8px;
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

