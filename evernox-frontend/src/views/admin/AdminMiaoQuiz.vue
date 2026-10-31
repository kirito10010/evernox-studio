<template>
  <div class="admin-miao">
    <div class="page-header">
      <h2>喵问答题库</h2>
      <p>
        支持「导入文件夹」：选择整个题库目录（含 questions.json 与 images/）即可连图片一起入库；
        重复题目不会重复导入，已有答案不会被覆盖，新答案/新排除项会补进空位
      </p>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索问题"
        clearable
        style="width: 220px"
        @input="onKeywordInput"
        @clear="reload"
      />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 130px" @change="reload">
        <el-option label="待审批" :value="0" />
        <el-option label="已通过" :value="1" />
        <el-option label="已驳回" :value="2" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="~"
        start-placeholder="创建起"
        end-placeholder="止"
        class="filter-date"
        @change="reload"
      />
      <el-select v-model="sortOrder" style="width: 100px" @change="reload">
        <el-option label="降序" value="desc" />
        <el-option label="升序" value="asc" />
      </el-select>
      <el-button type="primary" @click="openCreate">新增题目</el-button>
      <el-button :loading="importing" @click="pickFolder">导入文件夹</el-button>
      <span v-if="importing" class="import-progress">上传中 {{ importProgress }}%</span>
      <input
        ref="folderInput"
        type="file"
        webkitdirectory
        multiple
        class="folder-input"
        @change="onFolderChange"
      />
    </div>

    <el-table v-loading="loading" :data="list" @selection-change="onSelectionChange">
      <el-table-column type="selection" width="46" />
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expand-content">
            <el-image
              v-if="(row as MiaoQuestion).image"
              :src="imageUrl((row as MiaoQuestion).image)"
              :preview-src-list="[imageUrl((row as MiaoQuestion).image)]"
              fit="contain"
              class="expand-image"
              preview-teleported
            />
            <p><strong>问题：</strong>{{ (row as MiaoQuestion).question }}</p>
            <p
              v-for="(option, index) in optionsOf(row as MiaoQuestion)"
              :key="index"
              :class="{ removed: isRemoved(row as MiaoQuestion, option) }"
            >
              {{ optionKeys[index] }}. {{ option }}
              <span v-if="isRemoved(row as MiaoQuestion, option)" class="removed-tag">已排除</span>
            </p>
            <p><strong>答案：</strong>{{ (row as MiaoQuestion).answer || '待确认' }}</p>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="问题" min-width="240">
        <template #default="{ row }">
          <div class="question-cell">
            <el-image
              v-if="(row as MiaoQuestion).image"
              :src="imageUrl((row as MiaoQuestion).image)"
              fit="cover"
              class="thumb"
              preview-teleported
              :preview-src-list="[imageUrl((row as MiaoQuestion).image)]"
            />
            <span class="question-text">{{ (row as MiaoQuestion).question }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="答案" width="140">
        <template #default="{ row }">
          <el-tag v-if="!(row as MiaoQuestion).answer" type="warning" size="small">待确认</el-tag>
          <span v-else class="answer-text">{{ (row as MiaoQuestion).answer }}</span>
        </template>
      </el-table-column>
      <el-table-column label="排除项" width="180">
        <template #default="{ row }">
          <el-tag
            v-for="option in (row as MiaoQuestion).removedOptions"
            :key="option"
            type="info"
            size="small"
            class="removed-chip"
          >
            {{ option }}
          </el-tag>
          <span v-if="!((row as MiaoQuestion).removedOptions || []).length" class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="QuizStatusType[(row as MiaoQuestion).status] || 'info'" size="small">
            {{ QuizStatusMap[(row as MiaoQuestion).status] || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatDateTime((row as MiaoQuestion).createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <div class="op-btns">
            <el-button size="small" @click="openEdit(row as MiaoQuestion)">编辑</el-button>
            <el-button
              v-if="(row as MiaoQuestion).status === 0"
              size="small"
              type="success"
              @click="handleApprove(row as MiaoQuestion)"
            >通过</el-button>
            <el-button
              v-if="(row as MiaoQuestion).status === 0"
              size="small"
              type="warning"
              @click="handleReject(row as MiaoQuestion)"
            >驳回</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row as MiaoQuestion)">删除</el-button>
          </div>
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
        background
        @current-change="load"
        @size-change="reload"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑题目' : '新增题目'"
      width="580px"
      append-to-body
    >
      <el-form label-width="72px">
        <el-form-item label="问题">
          <el-input v-model="form.question" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="选项A">
          <el-input v-model="form.optionA" maxlength="200" />
        </el-form-item>
        <el-form-item label="选项B">
          <el-input v-model="form.optionB" maxlength="200" />
        </el-form-item>
        <el-form-item label="选项C">
          <el-input v-model="form.optionC" maxlength="200" />
        </el-form-item>
        <el-form-item label="选项D">
          <el-input v-model="form.optionD" maxlength="200" />
        </el-form-item>
        <el-form-item label="答案">
          <el-select
            v-model="form.answer"
            clearable
            placeholder="可留空，留空则等待用户确认"
            style="width: 100%"
          >
            <el-option v-for="option in optionChoices" :key="option" :label="option" :value="option" />
          </el-select>
        </el-form-item>
        <el-form-item label="排除项">
          <el-select
            v-model="form.removedOptions"
            multiple
            :multiple-limit="3"
            placeholder="游戏内已排除的选项，最多 3 个"
            style="width: 100%"
          >
            <el-option v-for="option in optionChoices" :key="option" :label="option" :value="option" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.image" label="题图">
          <div class="image-cell">
            <el-image
              :src="imageUrl(form.image)"
              :preview-src-list="[imageUrl(form.image)]"
              fit="contain"
              class="dialog-image"
              preview-teleported
            />
            <span class="muted">换图请重新导入文件夹</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">
          {{ editing ? '保存' : '添加' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime } from '@/utils/format'
import {
  approveMiao,
  batchDeleteMiao,
  createMiao,
  deleteMiao,
  getAdminMiaoList,
  importMiaoFolder,
  rejectMiao,
  updateMiao,
} from '@/api/adminMiaoQuiz'
import { QuizStatusMap, QuizStatusType } from '@/types/quiz'
import type { MiaoQuestion, MiaoQuestionRequest } from '@/types/miao'

const MAX_IMAGE_BYTES = 8 * 1024 * 1024
const MAX_BATCH_BYTES = 60 * 1024 * 1024
const IMAGE_PATTERN = /\.(png|jpe?g|webp|gif)$/i

const list = ref<MiaoQuestion[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const statusFilter = ref<number | null>(null)
const dateRange = ref<[string, string] | null>(null)
const sortOrder = ref<'asc' | 'desc'>('desc')
const loading = ref(false)
const selectedIds = ref<number[]>([])

const dialogVisible = ref(false)
const editing = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)

const folderInput = ref<HTMLInputElement | null>(null)
const importing = ref(false)
const importProgress = ref(0)

const optionKeys = ['A', 'B', 'C', 'D']

const form = reactive<MiaoQuestionRequest & { removedOptions: string[]; image: string }>({
  question: '',
  optionA: '',
  optionB: '',
  optionC: '',
  optionD: '',
  answer: '',
  removedOptions: [],
  image: '',
})

let keywordTimer: ReturnType<typeof setTimeout> | null = null

const imageUrl = (name?: string | null) =>
  name ? `${window.location.origin}/api/miao/image/${encodeURIComponent(name)}` : ''

const optionsOf = (q: MiaoQuestion) =>
  [q.optionA, q.optionB, q.optionC, q.optionD].map((option) => (option || '').trim()).filter(Boolean)

const isRemoved = (q: MiaoQuestion, option: string) => (q.removedOptions || []).includes(option)

/** 弹窗里可选的选项（排除项/答案都只能从已填写的四个选项里选） */
const optionChoices = computed(() =>
  [form.optionA, form.optionB, form.optionC, form.optionD]
    .map((option) => (option || '').trim())
    .filter(Boolean)
)

watch(optionChoices, (choices) => {
  if (form.removedOptions?.length) {
    form.removedOptions = form.removedOptions.filter((option) => choices.includes(option))
  }
  if (form.answer && !choices.includes(form.answer)) {
    form.answer = ''
  }
})

const load = async () => {
  loading.value = true
  try {
    const res = await getAdminMiaoList({
      page: page.value,
      size: size.value,
      status: statusFilter.value,
      keyword: keyword.value || undefined,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      sortField: 'createdAt',
      sortOrder: sortOrder.value,
    })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const reload = () => {
  page.value = 1
  load()
}

const onKeywordInput = () => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(reload, 300)
}

const onSelectionChange = (rows: MiaoQuestion[]) => {
  selectedIds.value = rows.map((row) => row.id)
}

const openCreate = () => {
  editing.value = false
  editingId.value = null
  form.question = ''
  form.optionA = ''
  form.optionB = ''
  form.optionC = ''
  form.optionD = ''
  form.answer = ''
  form.removedOptions = []
  form.image = ''
  dialogVisible.value = true
}

const openEdit = (row: MiaoQuestion) => {
  editing.value = true
  editingId.value = row.id
  form.question = row.question
  form.optionA = row.optionA
  form.optionB = row.optionB
  form.optionC = row.optionC
  form.optionD = row.optionD
  form.answer = row.answer
  form.removedOptions = [...(row.removedOptions || [])]
  form.image = row.image || ''
  dialogVisible.value = true
}

const submitForm = async () => {
  if (
    !form.question.trim() ||
    !form.optionA.trim() ||
    !form.optionB.trim() ||
    !form.optionC.trim() ||
    !form.optionD.trim()
  ) {
    ElMessage.warning('请填写完整的问题和四个选项')
    return
  }
  saving.value = true
  try {
    const data: MiaoQuestionRequest = {
      question: form.question.trim(),
      optionA: form.optionA.trim(),
      optionB: form.optionB.trim(),
      optionC: form.optionC.trim(),
      optionD: form.optionD.trim(),
      answer: (form.answer || '').trim(),
      removedOptions: form.removedOptions || [],
      image: form.image || '',
    }
    if (editing.value && editingId.value) {
      await updateMiao(editingId.value, data)
      ElMessage.success('保存成功')
    } else {
      await createMiao(data)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    reload()
  } catch {
    // 错误已由拦截器提示
  } finally {
    saving.value = false
  }
}

const handleApprove = async (row: MiaoQuestion) => {
  await approveMiao(row.id)
  ElMessage.success('已通过')
  reload()
}

const handleReject = async (row: MiaoQuestion) => {
  await rejectMiao(row.id)
  ElMessage.success('已驳回')
  reload()
}

const handleDelete = async (row: MiaoQuestion) => {
  await ElMessageBox.confirm(`确定删除题目「${row.question}」吗？`, '提示', { type: 'warning' })
  await deleteMiao(row.id)
  ElMessage.success('删除成功')
  reload()
}

const handleBatchDelete = async () => {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 道题吗？`, '批量删除', { type: 'warning' })
  await batchDeleteMiao(selectedIds.value)
  ElMessage.success('删除成功')
  selectedIds.value = []
  reload()
}

const pickFolder = () => {
  folderInput.value?.click()
}

const onFolderChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  // 清空 value：同一个文件夹再选一次也能触发 change
  input.value = ''
  if (!files.length) return

  const manifest = files.find((file) => file.name.toLowerCase() === 'questions.json')
  if (!manifest) {
    ElMessage.warning('所选文件夹里没有 questions.json')
    return
  }
  const images = files.filter((file) => IMAGE_PATTERN.test(file.name))
  const oversize = images.find((file) => file.size > MAX_IMAGE_BYTES)
  if (oversize) {
    ElMessage.error(`图片「${oversize.name}」超过 8MB，请压缩后重试`)
    return
  }
  const totalBytes = files.reduce((sum, file) => sum + file.size, 0)
  if (totalBytes > MAX_BATCH_BYTES) {
    ElMessage.warning('所选文件夹超过 60MB，请拆分后分批导入')
    return
  }

  const formData = new FormData()
  formData.append('manifest', manifest, manifest.name)
  images.forEach((file) => formData.append('images', file, file.name))

  importing.value = true
  importProgress.value = 0
  try {
    const res = await importMiaoFolder(formData, (percent) => {
      importProgress.value = percent
    })
    const data = res.data
    if (!data) return
    const missing = data.missingImages > 0 ? `，缺失 ${data.missingImages} 张图片` : ''
    ElMessage.success(
      `导入完成：新增 ${data.imported} 条，补全 ${data.updated} 条，跳过 ${data.skipped} 条，图片 ${data.images} 张${missing}`
    )
    reload()
  } catch {
    // 请求层已提示
  } finally {
    importing.value = false
    importProgress.value = 0
  }
}

load()
</script>

<style scoped lang="scss">
.admin-miao {
  .page-header {
    h2 {
      margin: 0 0 4px;
      font-size: 20px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }

    p {
      margin: 0 0 12px;
      font-size: 12px;
      line-height: 1.6;
      color: var(--ev-text-muted);
    }
  }

  .filter-bar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 14px;

    :deep(.filter-date) {
      flex: none !important;
      width: 250px !important;
      max-width: 250px !important;
    }
  }

  .folder-input {
    display: none;
  }

  .import-progress {
    font-size: 12px;
    color: var(--ev-primary);
  }

  .question-cell {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;

    .thumb {
      width: 32px;
      height: 32px;
      flex-shrink: 0;
      border-radius: 6px;
      border: 1px solid var(--ev-border-subtle);
      cursor: zoom-in;
    }

    .question-text {
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .answer-text {
    font-size: 13px;
    color: var(--ev-text-primary);
  }

  .removed-chip {
    margin: 0 4px 4px 0;
  }

  .muted {
    color: var(--ev-text-muted);
    font-size: 12px;
  }

  .expand-content {
    padding: 6px 16px;
    font-size: 13px;
    color: var(--ev-text-regular);
    line-height: 1.8;

    p {
      margin: 2px 0;
    }

    .removed {
      color: var(--ev-text-muted);
      text-decoration: line-through;
    }

    .removed-tag {
      margin-left: 6px;
      padding: 0 5px;
      border-radius: 4px;
      background: rgba(144, 147, 153, 0.16);
      color: var(--ev-text-muted);
      font-size: 10px;
      text-decoration: none;
    }

    .expand-image {
      display: block;
      max-width: 320px;
      max-height: 200px;
      margin-bottom: 8px;
      border: 1px solid var(--ev-border-subtle);
      border-radius: 8px;
      cursor: zoom-in;
    }
  }

  .image-cell {
    display: flex;
    align-items: center;
    gap: 10px;

    .dialog-image {
      max-width: 180px;
      max-height: 120px;
      border: 1px solid var(--ev-border-subtle);
      border-radius: 8px;
      cursor: zoom-in;
    }
  }

  .table-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 14px;
  }

  .op-btns {
    display: flex;
    flex-wrap: nowrap;
    align-items: center;

    :deep(.el-button + .el-button) {
      margin-left: 6px;
    }
  }
}
</style>
