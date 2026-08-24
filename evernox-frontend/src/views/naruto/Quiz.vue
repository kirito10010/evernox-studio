<template>
  <div class="quiz-page">
    <div class="page-header">
      <div>
        <h2>忍者测验</h2>
        <p>输入问题关键字，自动模糊匹配题库（容忍错别字、标点差异）</p>
      </div>
      <el-button type="primary" @click="openSubmit">提交题目</el-button>
    </div>

    <div class="quiz-layout">
      <div class="quiz-main">
        <div class="search-bar">
          <el-input
            v-model="keyword"
            placeholder="输入问题内容自动搜索…"
            clearable
            size="large"
            @input="onInput"
            @clear="clearSearch"
          />
        </div>

        <div v-loading="loading" class="result-list">
          <div v-for="q in results" :key="q.id" class="quiz-card">
            <div class="quiz-question">
              <span v-if="q.score != null" class="match">相似度 {{ (q.score * 100).toFixed(0) }}%</span>
              {{ q.question }}
            </div>
            <div class="quiz-options">
              <div class="option">A. {{ q.optionA }}</div>
              <div class="option">B. {{ q.optionB }}</div>
              <div class="option">C. {{ q.optionC }}</div>
              <div class="option">D. {{ q.optionD }}</div>
            </div>
            <div class="quiz-answer">答案：<span class="answer">{{ q.answer }}</span></div>
          </div>

          <el-empty v-if="!loading && keyword && !results.length" description="没有找到相似题目" />
          <el-empty v-else-if="!keyword" description="输入问题开始搜索" />
        </div>
      </div>

      <aside class="quiz-aside">
        <div class="aside-title">我的提交</div>
        <div v-if="mySubmissions.length" class="submission-list">
          <div v-for="s in mySubmissions" :key="s.id" class="submission-item">
            <div class="submission-question">{{ s.question }}</div>
            <div class="submission-meta">
              <el-tag :type="QuizStatusType[s.status] || 'info'" size="small">
                {{ QuizStatusMap[s.status] || '未知' }}
              </el-tag>
              <span class="submission-time">{{ formatTime(s.createdAt) }}</span>
            </div>
            <div v-if="s.status !== 1" class="submission-actions">
              <el-button size="small" text type="primary" @click="openEdit(s)">修改</el-button>
              <el-button v-if="s.status === 2" size="small" text @click="onResubmit(s)">重新提交</el-button>
              <el-button size="small" text type="danger" @click="onDelete(s)">删除</el-button>
            </div>
          </div>
        </div>
        <el-empty v-else description="还没有提交过题目" :image-size="50" />
      </aside>
    </div>

    <el-dialog
      v-model="submitVisible"
      :title="editingId ? '修改题目' : '提交题目'"
      width="560px"
      append-to-body
    >
      <el-form label-width="64px">
        <el-form-item label="问题">
          <el-input v-model="form.question" maxlength="500" show-word-limit placeholder="题目问题" />
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
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">
          {{ editingId ? '保存并重新提交' : '提交' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteMySubmission,
  getMySubmissions,
  resubmitQuiz,
  searchQuiz,
  submitQuiz,
  updateMySubmission,
} from '@/api/quiz'
import { QuizStatusMap, QuizStatusType } from '@/types/quiz'
import type { QuizQuestion, QuizQuestionRequest } from '@/types/quiz'

const keyword = ref('')
const results = ref<QuizQuestion[]>([])
const loading = ref(false)

const mySubmissions = ref<QuizQuestion[]>([])
const submitVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const form = reactive<QuizQuestionRequest>({
  question: '',
  optionA: '',
  optionB: '',
  optionC: '',
  optionD: '',
  answer: '',
})

let timer: ReturnType<typeof setTimeout> | null = null

const doSearch = async () => {
  const kw = keyword.value.trim()
  if (!kw) {
    results.value = []
    return
  }
  loading.value = true
  try {
    const res = await searchQuiz(kw)
    results.value = res.data || []
  } finally {
    loading.value = false
  }
}

const onInput = () => {
  if (timer) clearTimeout(timer)
  timer = setTimeout(doSearch, 1000)
}

const clearSearch = () => {
  results.value = []
}

const loadSubmissions = async () => {
  const res = await getMySubmissions()
  mySubmissions.value = res.data || []
}

const resetForm = () => {
  form.question = ''
  form.optionA = ''
  form.optionB = ''
  form.optionC = ''
  form.optionD = ''
  form.answer = ''
}

const openSubmit = () => {
  editingId.value = null
  resetForm()
  submitVisible.value = true
}

const openEdit = (s: QuizQuestion) => {
  editingId.value = s.id
  form.question = s.question
  form.optionA = s.optionA
  form.optionB = s.optionB
  form.optionC = s.optionC
  form.optionD = s.optionD
  form.answer = s.answer
  submitVisible.value = true
}

const onSubmit = async () => {
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
  submitting.value = true
  try {
    const data = {
      question: form.question.trim(),
      optionA: form.optionA.trim(),
      optionB: form.optionB.trim(),
      optionC: form.optionC.trim(),
      optionD: form.optionD.trim(),
      answer: form.answer.trim(),
    }
    if (editingId.value) {
      await updateMySubmission(editingId.value, data)
      ElMessage.success('修改并重新提交成功')
    } else {
      await submitQuiz(data)
      ElMessage.success('提交成功，等待管理员审核')
    }
    submitVisible.value = false
    await loadSubmissions()
  } catch {
    // 错误（如重复题目）已由拦截器提示
  } finally {
    submitting.value = false
  }
}

const onResubmit = async (s: QuizQuestion) => {
  await resubmitQuiz(s.id)
  ElMessage.success('已重新提交')
  await loadSubmissions()
}

const onDelete = async (s: QuizQuestion) => {
  await ElMessageBox.confirm(`确定删除题目「${s.question}」吗？`, '提示', { type: 'warning' })
  await deleteMySubmission(s.id)
  ElMessage.success('删除成功')
  await loadSubmissions()
}

const formatTime = (value?: string) => {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(loadSubmissions)
</script>

<style scoped lang="scss">
.quiz-page {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;

    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }

    p {
      margin: 6px 0 0;
      font-size: 13px;
      color: var(--ev-text-muted);
    }
  }

  .quiz-layout {
    display: flex;
    gap: 20px;
    align-items: flex-start;
    margin-top: 18px;
  }

  .quiz-main {
    flex: 1;
    min-width: 0;
  }

  .search-bar {
    margin-bottom: 14px;
  }

  .result-list {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .quiz-card {
    padding: 14px 16px;
    background: var(--el-bg-color);
    border: 1px solid var(--ev-border-subtle);
    border-radius: 12px;

    .quiz-question {
      font-size: 15px;
      font-weight: 600;
      color: var(--ev-text-primary);
      line-height: 1.6;

      .match {
        margin-right: 8px;
        padding: 1px 8px;
        border-radius: 999px;
        background: rgba(47, 124, 246, 0.1);
        color: var(--ev-primary);
        font-size: 12px;
        font-weight: 500;
      }
    }

    .quiz-options {
      margin-top: 10px;
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 4px 16px;

      .option {
        font-size: 13px;
        color: var(--ev-text-regular);
      }
    }

    .quiz-answer {
      margin-top: 10px;
      padding-top: 10px;
      border-top: 1px solid var(--ev-border-subtle);
      font-size: 13px;
      color: var(--ev-text-regular);

      .answer {
        font-weight: 600;
        color: var(--ev-success);
      }
    }
  }

  .quiz-aside {
    width: 320px;
    flex-shrink: 0;
    position: sticky;
    top: 20px;

    .aside-title {
      font-size: 14px;
      font-weight: 600;
      color: var(--ev-text-primary);
      margin-bottom: 12px;
    }

    .submission-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .submission-item {
      padding: 12px 14px;
      background: var(--el-bg-color);
      border: 1px solid var(--ev-border-subtle);
      border-radius: 10px;

      .submission-question {
        font-size: 13px;
        color: var(--ev-text-primary);
        line-height: 1.5;
        display: -webkit-box;
        -webkit-line-clamp: 3;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }

      .submission-meta {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-top: 8px;

        .submission-time {
          margin-left: auto;
          font-size: 12px;
          color: var(--ev-text-muted);
        }
      }

      .submission-actions {
        display: flex;
        gap: 4px;
        margin-top: 8px;
        padding-top: 8px;
        border-top: 1px solid var(--ev-border-subtle);
      }
    }
  }
}
</style>
