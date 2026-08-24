<template>
  <div class="admin-quiz">
    <div class="page-header">
      <h2>忍者测验管理</h2>
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
      <el-button type="primary" @click="openCreate">新增题目</el-button>
      <el-upload
        :show-file-list="false"
        :auto-upload="false"
        accept=".xlsx,.xls"
        :on-change="onImportFile"
      >
        <el-button :loading="importing">导入 Excel</el-button>
      </el-upload>
    </div>

    <el-table v-loading="loading" :data="list" @selection-change="onSelectionChange">
      <el-table-column type="selection" width="46" />
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expand-content">
            <p><strong>问题：</strong>{{ row.question }}</p>
            <p>A. {{ row.optionA }}</p>
            <p>B. {{ row.optionB }}</p>
            <p>C. {{ row.optionC }}</p>
            <p>D. {{ row.optionD }}</p>
            <p><strong>答案：</strong>{{ row.answer }}</p>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="question" label="问题" min-width="240" show-overflow-tooltip />
      <el-table-column prop="answer" label="答案" width="130" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="QuizStatusType[row.status] || 'info'" size="small">
            {{ QuizStatusMap[row.status] || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row as QuizQuestion)">编辑</el-button>
          <el-button
            v-if="(row as QuizQuestion).status === 0"
            size="small"
            type="success"
            @click="handleApprove(row as QuizQuestion)"
          >通过</el-button>
          <el-button
            v-if="(row as QuizQuestion).status === 0"
            size="small"
            type="warning"
            @click="handleReject(row as QuizQuestion)"
          >驳回</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row as QuizQuestion)">删除</el-button>
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
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="reload"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑题目' : '新增题目'"
      width="560px"
      append-to-body
    >
      <el-form label-width="64px">
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
          <el-input v-model="form.answer" maxlength="200" placeholder="正确答案（选项文本）" />
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
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import {
  approveQuiz,
  batchDeleteQuiz,
  createQuiz,
  deleteQuiz,
  getAdminQuizList,
  importQuizExcel,
  rejectQuiz,
  updateQuiz,
} from '@/api/adminQuiz'
import { QuizStatusMap, QuizStatusType } from '@/types/quiz'
import type { QuizQuestion, QuizQuestionRequest } from '@/types/quiz'

const list = ref<QuizQuestion[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const statusFilter = ref<number | null>(null)
const loading = ref(false)
const selectedIds = ref<number[]>([])

const dialogVisible = ref(false)
const editing = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const importing = ref(false)
const form = reactive<QuizQuestionRequest>({
  question: '',
  optionA: '',
  optionB: '',
  optionC: '',
  optionD: '',
  answer: '',
})

let keywordTimer: ReturnType<typeof setTimeout> | null = null

const load = async () => {
  loading.value = true
  try {
    const res = await getAdminQuizList({
      page: page.value,
      size: size.value,
      status: statusFilter.value,
      keyword: keyword.value || undefined,
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

const onSelectionChange = (rows: QuizQuestion[]) => {
  selectedIds.value = rows.map((r) => r.id)
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
  dialogVisible.value = true
}

const openEdit = (row: QuizQuestion) => {
  editing.value = true
  editingId.value = row.id
  form.question = row.question
  form.optionA = row.optionA
  form.optionB = row.optionB
  form.optionC = row.optionC
  form.optionD = row.optionD
  form.answer = row.answer
  dialogVisible.value = true
}

const submitForm = async () => {
  if (
    !form.question.trim() ||
    !form.optionA.trim() ||
    !form.optionB.trim() ||
    !form.optionC.trim() ||
    !form.optionD.trim() ||
    !form.answer.trim()
  ) {
    ElMessage.warning('请填写完整的题目信息')
    return
  }
  saving.value = true
  try {
    const data = {
      question: form.question.trim(),
      optionA: form.optionA.trim(),
      optionB: form.optionB.trim(),
      optionC: form.optionC.trim(),
      optionD: form.optionD.trim(),
      answer: form.answer.trim(),
    }
    if (editing.value && editingId.value) {
      await updateQuiz(editingId.value, data)
      ElMessage.success('保存成功')
    } else {
      await createQuiz(data)
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

const handleApprove = async (row: QuizQuestion) => {
  await approveQuiz(row.id)
  ElMessage.success('已通过')
  reload()
}

const handleReject = async (row: QuizQuestion) => {
  await rejectQuiz(row.id)
  ElMessage.success('已驳回')
  reload()
}

const handleDelete = async (row: QuizQuestion) => {
  await ElMessageBox.confirm(`确定删除题目「${row.question}」吗？`, '提示', { type: 'warning' })
  await deleteQuiz(row.id)
  ElMessage.success('删除成功')
  reload()
}

const handleBatchDelete = async () => {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 道题吗？`, '批量删除', { type: 'warning' })
  await batchDeleteQuiz(selectedIds.value)
  ElMessage.success('删除成功')
  selectedIds.value = []
  reload()
}

const onImportFile = (file: UploadFile) => {
  const raw = file.raw
  if (!raw) return
  if (!/\.(xlsx|xls)$/i.test(raw.name)) {
    ElMessage.warning('请选择 .xlsx 或 .xls 文件')
    return
  }
  importing.value = true
  importQuizExcel(raw)
    .then((res) => {
      const imported = res.data?.imported ?? 0
      const skipped = res.data?.skipped ?? 0
      ElMessage.success(`导入完成：成功 ${imported} 条，跳过 ${skipped} 条`)
      reload()
    })
    .catch(() => {
      // 错误已由拦截器提示
    })
    .finally(() => {
      importing.value = false
    })
}

load()
</script>

<style scoped lang="scss">
.admin-quiz {
  .page-header {
    h2 {
      margin: 0 0 12px;
      font-size: 20px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }
  }

  .filter-bar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;
  }

  .expand-content {
    padding: 6px 16px;
    font-size: 13px;
    color: var(--ev-text-regular);
    line-height: 1.8;

    p {
      margin: 2px 0;
    }
  }

  .table-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 14px;
  }
}
</style>
