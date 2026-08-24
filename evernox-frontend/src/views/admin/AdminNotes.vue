<template>
  <div class="admin-notes">
    <div class="page-header">
      <div class="header-text">
        <h2>笔记审批</h2>
        <p>通过后笔记正文与插图对所有登录用户可见</p>
      </div>
      <div class="stat-row">
        <div class="stat"><span class="num">{{ stats.pending ?? '—' }}</span><span class="label">待审批</span></div>
        <div class="stat"><span class="num">{{ stats.published ?? '—' }}</span><span class="label">已公开</span></div>
        <div class="stat"><span class="num">{{ stats.rejected ?? '—' }}</span><span class="label">已驳回</span></div>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="applyFilters">
      <el-tab-pane label="待审批" name="pending" />
      <el-tab-pane label="全部笔记" name="all" />
    </el-tabs>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        class="search"
        placeholder="搜索标题或摘要"
        clearable
        @input="onKeywordInput"
        @clear="applyFilters"
      />
      <el-select
        v-if="activeTab === 'all'"
        v-model="statusFilter"
        class="filter-item"
        placeholder="全部状态"
        clearable
        @change="applyFilters"
      >
        <el-option v-for="(label, value) in NoteStatusMap" :key="value" :label="label" :value="Number(value)" />
      </el-select>
    </div>

    <el-table :data="notes" v-loading="loading" row-key="id">
      <el-table-column label="笔记" min-width="280">
        <template #default="{ row }">
          <div class="note-cell">
            <span class="note-title">{{ row.title }}</span>
            <span class="note-summary" :title="row.summary || ''">{{ row.summary || '（空白笔记）' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="ownerName" label="作者" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ NoteStatusMap[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="提交时间" width="170" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openPreview(row as Note)">查看</el-button>
          <el-button v-if="row.status === NoteStatus.PENDING" size="small" type="primary" @click="handleApprove(row as Note)">通过</el-button>
          <el-button v-if="row.status === NoteStatus.PENDING" size="small" type="danger" @click="handleReject(row as Note)">驳回</el-button>
          <el-button v-if="row.status === NoteStatus.PUBLIC" size="small" type="warning" @click="handleOffline(row as Note)">撤下</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadNotes"
        @size-change="applyFilters"
      />
    </div>
    <el-drawer v-model="previewVisible" :title="preview?.title || '笔记预览'" size="720px" @closed="preview = null">
      <div v-if="preview" class="preview-meta">
        <span>作者 {{ preview.ownerName || '—' }}</span>
        <span>提交于 {{ preview.submittedAt || '—' }}</span>
      </div>
      <RichTextViewer v-if="preview && previewVisible" :html="preview.content || ''" :loader="decryptImage" />
      <el-empty v-else-if="!previewLoading" description="空白笔记" />
    </el-drawer>
  </div>
</template>
<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import RichTextViewer from '@/components/RichTextViewer.vue'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { getAdminImageBlob } from '@/api/adminAsset'
import {
  approveNote,
  getAdminNoteById,
  getAdminNoteStats,
  getAdminNotes,
  offlineNote,
  rejectNote,
} from '@/api/adminNote'
import { NoteStatus, NoteStatusMap } from '@/types/note'
import type { Note, NoteStats } from '@/types/note'

/**
 * 插图走管理员取流通道
 *
 * 待审批笔记的插图此刻还是作者的私密图，普通 /image/{id}/file 会 403。
 */
const { decryptImage, clearCache } = useImageDecrypt(getAdminImageBlob)

const activeTab = ref('pending')
const notes = ref<Note[]>([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref<number | null>(null)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const stats = ref<NoteStats>({ mine: null, pending: null, published: null, rejected: null })

const previewVisible = ref(false)
const previewLoading = ref(false)
const preview = ref<Note | null>(null)

const statusTagType = (status: number) => {
  switch (status) {
    case NoteStatus.PENDING:
      return 'warning'
    case NoteStatus.PUBLIC:
      return 'success'
    case NoteStatus.REJECTED:
      return 'danger'
    default:
      return 'info'
  }
}
const loadNotes = async () => {
  loading.value = true
  try {
    const res = await getAdminNotes({
      page: currentPage.value,
      size: pageSize.value,
      status: activeTab.value === 'pending' ? NoteStatus.PENDING : statusFilter.value,
      keyword: keyword.value.trim() || undefined,
    })
    notes.value = res.data?.records || []
    total.value = res.data?.total || 0
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

const loadStats = async () => {
  try {
    const res = await getAdminNoteStats()
    if (res.data) stats.value = res.data
  } catch {
    /* 请求层已提示 */
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

const openPreview = async (note: Note) => {
  previewLoading.value = true
  previewVisible.value = true
  try {
    const res = await getAdminNoteById(note.id)
    preview.value = res.data ?? null
  } catch {
    preview.value = null
  } finally {
    previewLoading.value = false
  }
}
const handleApprove = async (note: Note) => {
  try {
    await ElMessageBox.confirm(
      `通过后「${note.title}」及正文插图将对所有登录用户可见，确定通过？`,
      '通过审批',
      { confirmButtonText: '通过', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await approveNote(note.id)
    ElMessage.success('已通过审批')
    await Promise.all([loadNotes(), loadStats()])
  } catch {
    /* 请求层已提示 */
  }
}

const handleReject = async (note: Note) => {
  let reason: string
  try {
    const result = await ElMessageBox.prompt('请填写驳回原因，作者可以看到：', '驳回笔记', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (value: string) =>
        (value && value.trim().length > 0) || '驳回原因不能为空',
    })
    reason = result.value
  } catch {
    return
  }
  try {
    await rejectNote(note.id, reason.trim())
    ElMessage.success('已驳回')
    await Promise.all([loadNotes(), loadStats()])
  } catch {
    /* 请求层已提示 */
  }
}

const handleOffline = async (note: Note) => {
  try {
    await ElMessageBox.confirm(`确定撤下「${note.title}」？笔记将回到作者的私有状态。`, '撤下笔记', {
      confirmButtonText: '撤下',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await offlineNote(note.id)
    ElMessage.success('已撤下')
    await Promise.all([loadNotes(), loadStats()])
  } catch {
    /* 请求层已提示 */
  }
}

onMounted(() => {
  void loadNotes()
  void loadStats()
})

onUnmounted(clearCache)
</script>
<style scoped lang="scss">
.admin-notes {
  display: flex;
  flex-direction: column;
  gap: 12px;
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
  width: 240px;
}

.filter-item {
  width: 150px;
}

.note-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.note-title {
  font-weight: 600;
}

.note-summary {
  font-size: 12px;
  color: var(--ev-text-muted, #90a4bb);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
}

.preview-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  font-size: 12px;
  color: var(--ev-text-muted, #90a4bb);
}
</style>

