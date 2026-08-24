<template>
  <div class="notes-page">
    <div class="page-header">
      <div class="header-text">
        <h2>记事本</h2>
        <p>支持富文本与插图；申请公开需管理员审批</p>
      </div>
      <div class="header-right">
        <div class="stat-row">
          <div class="stat"><span class="num">{{ stats.mine ?? '—' }}</span><span class="label">全部</span></div>
          <div class="stat"><span class="num">{{ stats.pending ?? '—' }}</span><span class="label">待审批</span></div>
          <div class="stat"><span class="num">{{ stats.published ?? '—' }}</span><span class="label">已公开</span></div>
        </div>
        <el-button type="primary" @click="openCreate">写笔记</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        class="search"
        placeholder="搜索标题或摘要"
        clearable
        @input="onKeywordInput"
        @clear="resetAndLoad"
      />
      <el-select v-model="statusFilter" class="filter-item" placeholder="全部状态" clearable @change="resetAndLoad">
        <el-option v-for="(label, value) in NoteStatusMap" :key="value" :label="label" :value="Number(value)" />
      </el-select>
    </div>

    <div v-loading="loading" class="note-list">
      <div v-for="note in notes" :key="note.id" class="note-card" @click="openDetail(note)">
        <div class="card-head">
          <span class="title">
            <el-icon v-if="note.pinned === 1" class="pin"><Star /></el-icon>
            {{ note.title }}
          </span>
          <span class="chip" :class="`s-${note.status}`">{{ NoteStatusMap[note.status] }}</span>
        </div>
        <p class="summary">{{ note.summary || '（空白笔记）' }}</p>
        <p v-if="note.status === NoteStatus.REJECTED && note.rejectReason" class="reject">
          驳回：{{ note.rejectReason }}
        </p>
        <div class="card-foot" @click.stop>
          <span class="time">{{ note.updatedAt }}</span>
          <div class="actions">
            <el-button size="small" text @click="togglePin(note)">
              {{ note.pinned === 1 ? '取消置顶' : '置顶' }}
            </el-button>
            <el-button v-if="canEdit(note)" size="small" text @click="openEdit(note)">编辑</el-button>
            <el-button v-if="canEdit(note)" size="small" text type="primary" @click="handleSubmit(note)">申请公开</el-button>
            <el-button v-if="canWithdraw(note)" size="small" text @click="handleWithdraw(note)">
              {{ note.status === NoteStatus.PENDING ? '撤回申请' : '撤下' }}
            </el-button>
            <el-button size="small" text type="danger" @click="handleDelete(note)">删除</el-button>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && !notes.length" class="empty" description="还没有笔记，写一篇吧" />

      <div class="list-footer">
        <div :ref="(el) => (sentinelRef = el as HTMLElement | null)" class="load-sentinel"></div>
        <span v-if="loadingMore" class="footer-tip">加载中…</span>
        <el-button v-else-if="hasMore && !supportsObserver" text @click="loadMore">加载更多</el-button>
      </div>
    </div>
    <el-drawer v-model="editorVisible" :title="editingId ? '编辑笔记' : '写笔记'" size="720px">
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="给这篇笔记起个名字" />
        </el-form-item>
        <el-form-item label="正文">
          <RichTextEditor
            v-if="editorVisible"
            ref="editorRef"
            v-model="form.content"
            :loader="decryptImage"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="detailVisible" :title="detail?.title || '笔记'" size="720px">
      <div v-if="detail" class="detail-meta">
        <span class="chip" :class="`s-${detail.status}`">{{ NoteStatusMap[detail.status] }}</span>
        <span class="time">更新于 {{ detail.updatedAt }}</span>
      </div>
      <RichTextViewer v-if="detail && detailVisible" :html="detail.content || ''" :loader="decryptImage" />
      <el-empty v-else-if="!detailLoading" description="空白笔记" />
    </el-drawer>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import RichTextEditor from '@/components/RichTextEditor.vue'
import RichTextViewer from '@/components/RichTextViewer.vue'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import {
  createNote,
  deleteNote,
  getMyNotes,
  getNoteById,
  getNoteStats,
  pinNote,
  submitNote,
  updateNote,
  withdrawNote,
} from '@/api/note'
import { NoteStatus, NoteStatusMap } from '@/types/note'
import type { Note, NoteStats } from '@/types/note'

const BATCH_SIZE = 20

/** 插图与图床共用同一套 ObjectURL 缓存，页面卸载时统一释放 */
const { decryptImage, clearCache } = useImageDecrypt()

const notes = ref<Note[]>([])
const total = ref(0)
const batchIndex = ref(1)
const loading = ref(false)
const loadingMore = ref(false)
const saving = ref(false)
const detailLoading = ref(false)
const stats = ref<NoteStats>({ mine: null, pending: null, published: null, rejected: null })

const keyword = ref('')
const statusFilter = ref<number | null>(null)

const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const editorRef = ref<InstanceType<typeof RichTextEditor> | null>(null)
const form = reactive({ title: '', content: '' })

const detailVisible = ref(false)
const detail = ref<Note | null>(null)

const supportsObserver = typeof IntersectionObserver !== 'undefined'
const hasMore = computed(() => notes.value.length < total.value)

const canEdit = (note: Note) =>
  note.status === NoteStatus.PRIVATE || note.status === NoteStatus.REJECTED
const canWithdraw = (note: Note) =>
  note.status === NoteStatus.PENDING || note.status === NoteStatus.PUBLIC
const fetchBatch = async (page: number) => {
  const res = await getMyNotes({
    page,
    size: BATCH_SIZE,
    keyword: keyword.value.trim() || undefined,
    status: statusFilter.value,
  })
  total.value = res.data?.total || 0
  return res.data?.records || []
}

const resetAndLoad = async () => {
  loading.value = true
  batchIndex.value = 1
  try {
    notes.value = await fetchBatch(1)
    await recheck()
  } catch {
    /* 请求层已提示 */
  } finally {
    loading.value = false
  }
}

const loadMore = async () => {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const next = batchIndex.value + 1
    notes.value.push(...(await fetchBatch(next)))
    batchIndex.value = next
    await recheck()
  } catch {
    /* 请求层已提示 */
  } finally {
    loadingMore.value = false
  }
}

const { sentinelRef, recheck } = useInfiniteScroll(() => {
  if (hasMore.value && !loading.value) void loadMore()
})

const loadStats = async () => {
  try {
    const res = await getNoteStats()
    if (res.data) stats.value = res.data
  } catch {
    /* 请求层已提示 */
  }
}

let keywordTimer: ReturnType<typeof setTimeout> | null = null
const onKeywordInput = () => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(resetAndLoad, 300)
}
const openCreate = () => {
  editingId.value = null
  form.title = ''
  form.content = ''
  editorVisible.value = true
}

const openEdit = async (note: Note) => {
  editingId.value = note.id
  form.title = note.title
  // 列表不返回正文，编辑前单独取一次详情
  try {
    const res = await getNoteById(note.id)
    form.content = res.data?.content || ''
  } catch {
    form.content = ''
  }
  editorVisible.value = true
}

const handleSave = async () => {
  const title = form.title.trim()
  if (!title) {
    ElMessage.warning('请填写标题')
    return
  }
  // 直接问编辑器要最终 HTML：v-model 是防抖前的中间态，保存要拿当下的
  const content = editorRef.value?.getHtml() ?? form.content
  saving.value = true
  try {
    if (editingId.value) await updateNote(editingId.value, { title, content })
    else await createNote({ title, content })
    editorVisible.value = false
    await Promise.all([resetAndLoad(), loadStats()])
  } catch {
    /* 请求层已提示 */
  } finally {
    saving.value = false
  }
}

const openDetail = async (note: Note) => {
  detailLoading.value = true
  detailVisible.value = true
  try {
    const res = await getNoteById(note.id)
    detail.value = res.data ?? null
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

const togglePin = async (note: Note) => {
  try {
    await pinNote(note.id, note.pinned !== 1)
    await resetAndLoad()
  } catch {
    /* 请求层已提示 */
  }
}
const handleSubmit = async (note: Note) => {
  try {
    await submitNote(note.id)
    ElMessage.success('已提交审批')
    await Promise.all([resetAndLoad(), loadStats()])
  } catch {
    /* 请求层已提示 */
  }
}

const handleWithdraw = async (note: Note) => {
  const label = note.status === NoteStatus.PENDING ? '撤回申请' : '撤下'
  try {
    await ElMessageBox.confirm(`确定${label}「${note.title}」？笔记将回到私有状态。`, label, {
      confirmButtonText: label,
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await withdrawNote(note.id)
    ElMessage.success(`已${label}`)
    await Promise.all([resetAndLoad(), loadStats()])
  } catch {
    /* 请求层已提示 */
  }
}

const handleDelete = async (note: Note) => {
  try {
    await ElMessageBox.confirm(
      `确定删除「${note.title}」？正文里的插图会一并删除，不可恢复。`,
      '删除笔记',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteNote(note.id)
    ElMessage.success('已删除')
    await Promise.all([resetAndLoad(), loadStats()])
  } catch {
    /* 请求层已提示 */
  }
}

onMounted(() => {
  void resetAndLoad()
  void loadStats()
})

onUnmounted(clearCache)
</script>
<style scoped lang="scss">
.notes-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
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

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.stat-row {
  display: flex;
  gap: 20px;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;

  .num {
    font-size: 20px;
    font-weight: 600;
  }

  .label {
    font-size: 12px;
    color: var(--ev-text-muted, #90a4bb);
  }
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search {
  width: 260px;
}

.filter-item {
  width: 140px;
}

.note-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
  min-height: 200px;
}

.empty,
.list-footer {
  grid-column: 1 / -1;
}

.note-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  background: var(--el-bg-color);
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;

  &:hover {
    border-color: var(--el-color-primary-light-5);
    box-shadow: 0 8px 20px rgba(15, 23, 42, 0.1);
    transform: translateY(-2px);
  }
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.title {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pin {
  color: var(--el-color-warning);
}

.chip {
  flex: 0 0 auto;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);

  &.s-1 { color: var(--el-color-warning); }
  &.s-2 { color: var(--el-color-success); }
  &.s-3 { color: var(--el-color-danger); }
}

.summary {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.reject {
  margin: 0;
  font-size: 12px;
  color: var(--el-color-danger);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: auto;
  padding-top: 4px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.time {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.actions {
  display: flex;
  flex-wrap: wrap;

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}

.list-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.load-sentinel {
  width: 100%;
  height: 1px;
}

.footer-tip {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
</style>

