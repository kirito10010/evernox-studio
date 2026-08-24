<template>
  <div class="admin-announcement">
    <div class="page-header">
      <div>
        <h2 class="page-title">公告管理</h2>
        <p class="page-subtitle">维护公告标签、发布公告；发布后全站用户可在右上角铃铛查看</p>
      </div>
      <div class="header-actions">
        <el-button @click="openTagDialog">公告标签配置</el-button>
        <el-button type="primary" @click="openCreateDialog">发布公告</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索标题"
        clearable
        style="width: 240px"
        @input="onKeywordInput"
        @clear="onKeywordClear"
      />
    </div>

    <el-table v-loading="loading" :data="list" @selection-change="onSelectionChange">
      <el-table-column type="selection" width="46" />
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column label="标签" width="150">
        <template #default="{ row }">
          <span v-if="row.tagName" class="tag-chip" :style="{ background: row.tagColor || '#409EFF' }">
            {{ row.tagName }}
          </span>
          <span v-else class="tag-none">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdByName" label="发布人" width="120" />
      <el-table-column prop="createdAt" label="发布时间" width="180" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEditDialog(row as AnnouncementResponse)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row as AnnouncementResponse)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="table-footer">
      <el-button v-if="selectedIds.length" type="danger" size="small" @click="handleBatchDelete">
        批量删除（{{ selectedIds.length }}）
      </el-button>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadList"
        @size-change="onSizeChange"
      />
    </div>

    <!-- 标签配置对话框 -->
    <el-dialog v-model="tagDialogVisible" title="公告标签配置" width="520px">
      <div class="tag-list">
        <div v-for="tag in tags" :key="tag.id" class="tag-item">
          <span class="tag-chip" :style="{ background: tag.color }">{{ tag.name }}</span>
          <div class="tag-actions">
            <el-button size="small" @click="startEditTag(tag)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDeleteTag(tag)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!tags.length" description="暂无标签" :image-size="60" />
      </div>
      <div class="tag-add">
        <el-input v-model="tagForm.name" placeholder="标签名" maxlength="30" style="width: 170px" />
        <el-color-picker v-model="tagForm.color" color-format="hex" :predefine="predefineColors" />
        <el-button type="primary" @click="submitTag">{{ editingTagId ? '保存' : '添加' }}</el-button>
        <el-button v-if="editingTagId" @click="cancelEditTag">取消</el-button>
      </div>
    </el-dialog>

    <!-- 发布/编辑对话框 -->
    <el-dialog
      v-model="publishDialogVisible"
      :title="isEditing ? '编辑公告' : '发布公告'"
      width="720px"
    >
      <el-form label-width="64px">
        <el-form-item label="标签">
          <el-select
            v-model="announcementForm.tagId"
            placeholder="选择标签（可选）"
            clearable
            style="width: 220px"
          >
            <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="announcementForm.title" placeholder="公告标题" maxlength="100" />
        </el-form-item>
        <el-form-item label="正文">
          <RichTextEditor
            ref="editorRef"
            v-model="announcementForm.content"
            :loader="decryptAnnouncement"
            :purpose="4"
            placeholder="写点公告内容…"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAnnouncement">
          {{ isEditing ? '保存' : '发布' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import RichTextEditor from '@/components/RichTextEditor.vue'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import {
  batchDeleteAnnouncements,
  createAnnouncement,
  createAnnouncementTag,
  deleteAnnouncement,
  deleteAnnouncementTag,
  getAdminAnnouncementList,
  getAdminAnnouncementTags,
  getAnnouncementDetail,
  getAnnouncementImageBlob,
  updateAnnouncement,
  updateAnnouncementTag,
} from '@/api/announcement'
import type { AnnouncementResponse, AnnouncementTag } from '@/types/announcement'

const { decryptImage: decryptAnnouncement, clearCache: clearAnnouncementCache } =
  useImageDecrypt(getAnnouncementImageBlob)

const list = ref<AnnouncementResponse[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)
const submitting = ref(false)
const selectedIds = ref<number[]>([])

// 标签
const tags = ref<AnnouncementTag[]>([])
const tagDialogVisible = ref(false)
const editingTagId = ref<number | null>(null)
const tagForm = reactive({ name: '', color: '#409EFF' })
const predefineColors = [
  '#409EFF', '#67C23A', '#E6A23C', '#F56C6C',
  '#909399', '#9C27B0', '#00BCD4', '#FF9800',
]

// 发布
const publishDialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const editorRef = ref<{ getHtml: () => string } | null>(null)
const announcementForm = reactive({ tagId: null as number | null, title: '', content: '' })

let keywordTimer: ReturnType<typeof setTimeout> | null = null

const loadList = async () => {
  loading.value = true
  try {
    const res = await getAdminAnnouncementList({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
    })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const loadTags = async () => {
  const res = await getAdminAnnouncementTags()
  tags.value = res.data || []
}

const onKeywordInput = () => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    page.value = 1
    loadList()
  }, 300)
}

const onKeywordClear = () => {
  page.value = 1
  loadList()
}

const onSelectionChange = (rows: AnnouncementResponse[]) => {
  selectedIds.value = rows.map((r) => r.id)
}

const onSizeChange = () => {
  page.value = 1
  loadList()
}

// ============ 标签 ============

const openTagDialog = () => {
  resetTagForm()
  tagDialogVisible.value = true
  loadTags()
}

const submitTag = async () => {
  if (!tagForm.name.trim()) {
    ElMessage.warning('请输入标签名')
    return
  }
  if (!tagForm.color) {
    ElMessage.warning('请选择颜色')
    return
  }
  const data = { name: tagForm.name.trim(), color: tagForm.color }
  if (editingTagId.value) {
    await updateAnnouncementTag(editingTagId.value, data)
    ElMessage.success('保存成功')
  } else {
    await createAnnouncementTag(data)
    ElMessage.success('添加成功')
  }
  resetTagForm()
  await loadTags()
}

const startEditTag = (tag: AnnouncementTag) => {
  editingTagId.value = tag.id
  tagForm.name = tag.name
  tagForm.color = tag.color
}

const cancelEditTag = () => resetTagForm()

const resetTagForm = () => {
  editingTagId.value = null
  tagForm.name = ''
  tagForm.color = '#409EFF'
}

const handleDeleteTag = async (tag: AnnouncementTag) => {
  await ElMessageBox.confirm(`确定删除标签「${tag.name}」吗？`, '提示', { type: 'warning' })
  await deleteAnnouncementTag(tag.id)
  ElMessage.success('删除成功')
  await loadTags()
}

// ============ 公告 ============

const openCreateDialog = () => {
  isEditing.value = false
  editingId.value = null
  announcementForm.tagId = null
  announcementForm.title = ''
  announcementForm.content = ''
  publishDialogVisible.value = true
  loadTags()
}

const openEditDialog = async (row: AnnouncementResponse) => {
  const res = await getAnnouncementDetail(row.id)
  const detail = res.data
  isEditing.value = true
  editingId.value = row.id
  announcementForm.tagId = detail.tagId ?? null
  announcementForm.title = detail.title
  announcementForm.content = detail.content || ''
  publishDialogVisible.value = true
  loadTags()
}

const submitAnnouncement = async () => {
  if (!announcementForm.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }
  const finalContent = editorRef.value?.getHtml() ?? announcementForm.content
  submitting.value = true
  try {
    const data = {
      tagId: announcementForm.tagId,
      title: announcementForm.title.trim(),
      content: finalContent,
    }
    if (isEditing.value && editingId.value) {
      await updateAnnouncement(editingId.value, data)
      ElMessage.success('保存成功')
    } else {
      await createAnnouncement(data)
      ElMessage.success('发布成功')
    }
    publishDialogVisible.value = false
    await loadList()
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row: AnnouncementResponse) => {
  await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？`, '提示', { type: 'warning' })
  await deleteAnnouncement(row.id)
  ElMessage.success('删除成功')
  await loadList()
}

const handleBatchDelete = async () => {
  await ElMessageBox.confirm(
    `确定删除选中的 ${selectedIds.value.length} 条公告吗？`,
    '批量删除',
    { type: 'warning' }
  )
  await batchDeleteAnnouncements(selectedIds.value)
  ElMessage.success('删除成功')
  selectedIds.value = []
  await loadList()
}

onMounted(() => {
  loadList()
  loadTags()
})

onUnmounted(() => {
  clearAnnouncementCache()
  if (keywordTimer) clearTimeout(keywordTimer)
})
</script>

<style scoped lang="scss">
.admin-announcement {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 20px;

    .page-title {
      margin: 0;
      font-size: 20px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }

    .page-subtitle {
      margin: 6px 0 0;
      font-size: 13px;
      color: var(--ev-text-muted);
    }

    .header-actions {
      display: flex;
      gap: 12px;
    }
  }

  .filter-bar {
    margin-bottom: 16px;
  }

  .table-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 16px;
  }

  .tag-chip {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 999px;
    color: #fff;
    font-size: 12px;
    line-height: 20px;
  }

  .tag-none {
    color: var(--ev-text-muted);
  }

  .tag-list {
    max-height: 260px;
    overflow-y: auto;
    margin-bottom: 16px;

    .tag-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 10px;
      border-radius: 8px;

      &:hover {
        background: var(--ev-bg-tint);
      }

      .tag-actions {
        display: flex;
        gap: 8px;
      }
    }
  }

  .tag-add {
    display: flex;
    align-items: center;
    gap: 10px;
    padding-top: 12px;
    border-top: 1px solid var(--ev-border-subtle);
  }
}
</style>
