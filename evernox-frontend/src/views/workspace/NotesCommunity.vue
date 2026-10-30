<template>
  <div class="notes-community">
    <div class="page-header">
      <div class="header-text">
        <h2>公开笔记</h2>
        <p>社区成员经管理员审核后公开的笔记，所有访客均可阅读</p>
      </div>
      <div class="header-actions">
        <el-input
          v-model="keyword"
          class="search"
          placeholder="搜索标题或内容摘要"
          clearable
          @input="onKeywordInput"
          @clear="applyFilters"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button v-if="userStore.isLoggedIn" @click="router.push('/workspace/notes')">我的记事本</el-button>
      </div>
    </div>

    <div class="feed-meta">
      <span>共 {{ total }} 篇公开笔记</span>
      <span class="feed-tip">按最近公开时间排序</span>
    </div>

    <div v-loading="loading" class="note-grid">
      <div
        v-for="note in notes"
        :key="note.id"
        class="note-card"
        role="button"
        tabindex="0"
        @click="openDetail(note)"
        @keydown.enter="openDetail(note)"
      >
        <div class="author-row">
          <span class="avatar" :style="{ background: avatarColor(note.userId) }">{{ initial(note.ownerName) }}</span>
          <div class="author-info">
            <span class="author-name">{{ note.ownerName || '匿名用户' }}</span>
            <span class="publish-time">{{ formatDate(note.reviewedAt || note.createdAt) }} 公开</span>
          </div>
        </div>
        <h3 class="title">{{ note.title }}</h3>
        <p class="summary">{{ note.summary || '（空白笔记）' }}</p>
        <div class="card-foot">
          <span class="read-more">
            阅读全文
            <el-icon><ArrowRight /></el-icon>
          </span>
        </div>
      </div>

      <el-empty
        v-if="!loading && !notes.length"
        class="empty"
        :description="keyword.trim() ? '没有找到相关笔记' : '还没有公开的笔记'"
      />
    </div>

    <div v-if="total > pageSize" class="pagination-wrap">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[12, 24, 48]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="loadNotes"
        @size-change="applyFilters"
      />
    </div>

    <el-drawer
      v-model="detailVisible"
      :title="detail?.title || '笔记'"
      size="760px"
      @closed="onDetailClosed"
    >
      <div v-loading="detailLoading" class="detail-body">
        <div v-if="detail" class="detail-head">
          <span class="avatar" :style="{ background: avatarColor(detail.userId) }">{{ initial(detail.ownerName) }}</span>
          <div class="detail-meta">
            <span class="author-name">{{ detail.ownerName || '匿名用户' }}</span>
            <span class="publish-time">{{ formatDateTime(detail.reviewedAt || detail.createdAt) }} 公开</span>
          </div>
          <el-button text type="primary" @click="copyLink">复制链接</el-button>
        </div>
        <RichTextViewer v-if="detail && detailVisible" :html="detail.content || ''" :loader="decryptImage" />
        <el-empty v-else-if="!detailLoading" description="空白笔记" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import RichTextViewer from '@/components/RichTextViewer.vue'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { getNoteById, getPublicNotes } from '@/api/note'
import { useUserStore } from '@/stores/user'
import { formatDate, formatDateTime } from '@/utils/format'
import type { Note } from '@/types/note'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const { decryptImage, clearCache } = useImageDecrypt()

const notes = ref<Note[]>([])
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(12)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<Note | null>(null)

/** 用户名首字做头像，作者没有昵称时退回「匿」 */
const initial = (name: string | null) => (name || '匿').trim().charAt(0).toUpperCase()

/** 同一作者固定配色：按 userId 散列到色相环 */
const avatarColor = (userId: number) => `hsl(${(userId * 47) % 360}, 55%, 52%)`

const loadNotes = async () => {
  loading.value = true
  try {
    const res = await getPublicNotes({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
    })
    notes.value = res.data?.records || []
    total.value = res.data?.total || 0
    // 翻页后当前页为空（例如作者刚好撤下）→ 回退一页
    if (!notes.value.length && currentPage.value > 1) {
      currentPage.value -= 1
      await loadNotes()
    }
  } catch {
    /* 请求层已提示 */
  } finally {
    loading.value = false
  }
}

const applyFilters = () => {
  currentPage.value = 1
  void loadNotes()
}

let keywordTimer: ReturnType<typeof setTimeout> | null = null
const onKeywordInput = () => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(applyFilters, 300)
}

/** 深链：?note=123 */
const syncQuery = (id: number | null) => {
  const query: Record<string, string> = {}
  Object.entries(route.query).forEach(([key, value]) => {
    if (key !== 'note' && typeof value === 'string') query[key] = value
  })
  if (id) query.note = String(id)
  void router.replace({ path: route.path, query })
}

const openDetail = async (note: { id: number }) => {
  detailLoading.value = true
  detailVisible.value = true
  try {
    const res = await getNoteById(note.id)
    detail.value = res.data ?? null
    syncQuery(note.id)
  } catch {
    // 笔记可能已被撤下 / 删除，请求层已提示，这里只负责收起抽屉
    detail.value = null
    detailVisible.value = false
    syncQuery(null)
  } finally {
    detailLoading.value = false
  }
}

/** 抽屉关闭后清空正文并移除深链参数，避免刷新又自动弹开 */
const onDetailClosed = () => {
  detail.value = null
  syncQuery(null)
}

const copyLink = async () => {
  if (!detail.value) return
  const href = router.resolve({
    name: 'NotesCommunity',
    query: { note: String(detail.value.id) },
  }).href
  try {
    if (!navigator.clipboard) throw new Error('clipboard unavailable')
    await navigator.clipboard.writeText(`${location.origin}${href}`)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制地址栏链接')
  }
}

onMounted(async () => {
  await loadNotes()
  const id = Number(route.query.note)
  // 深链优先直接查详情，不依赖笔记是否落在当前页
  if (id) await openDetail({ id })
})

onUnmounted(() => {
  if (keywordTimer) clearTimeout(keywordTimer)
  clearCache()
})
</script>

<style scoped lang="scss">
.notes-community {
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search {
  width: 280px;
}

.feed-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.feed-tip {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.note-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
  min-height: 220px;
}

.empty {
  grid-column: 1 / -1;
}

.note-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  background: var(--el-bg-color);
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
  outline: none;

  &:hover,
  &:focus-visible {
    border-color: var(--el-color-primary-light-5);
    box-shadow: 0 8px 20px rgba(15, 23, 42, 0.1);
    transform: translateY(-2px);
  }
}

.author-row,
.detail-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  user-select: none;
}

.author-info,
.detail-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.35;
}

.author-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.publish-time {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  color: var(--el-text-color-regular);
  white-space: pre-line;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-top: auto;
  padding-top: 6px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.read-more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  color: var(--el-color-primary);
}

.pagination-wrap {
  display: flex;
  justify-content: center;
}

.detail-body {
  min-height: 160px;
}

.detail-head {
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);

  .el-button {
    margin-left: auto;
  }
}
</style>
