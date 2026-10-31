<template>
  <el-dialog
    v-model="visible"
    title="获取新模型"
    width="760px"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
  >
    <!-- 1. 配置 -->
    <div v-if="mode === 'config'" class="section">
      <p class="tip">
        只抓取本地还没有的模型，已收录的模型不会重复导入，也不会检查它们的变体。
        不填关键词则扫描官网「官方库 + abliterated」。
      </p>
      <div class="keyword-row">
        <el-tag
          v-for="(kw, index) in keywords"
          :key="kw"
          closable
          class="keyword-tag"
          @close="removeKeyword(index)"
        >
          {{ kw }}
        </el-tag>
        <el-input
          v-model="input"
          size="small"
          class="keyword-input"
          placeholder="输入关键词后回车添加（可留空）"
          maxlength="100"
          @keyup.enter="addKeyword"
        />
        <el-button size="small" @click="addKeyword">添加</el-button>
      </div>
      <p class="hint">最多 {{ MAX_KEYWORDS }} 个关键词，单个不超过 100 个字符。</p>
    </div>

    <!-- 2. 进度 -->
    <div v-else-if="mode === 'running'" class="section">
      <div class="progress-head">
        <span class="phase">{{ task?.phase || '正在获取…' }}</span>
        <span v-if="task && task.total > 0" class="count">{{ task.done }} / {{ task.total }}</span>
      </div>
      <el-progress
        :percentage="percent"
        :indeterminate="!hasTotal"
        :duration="2"
        :stroke-width="10"
        :show-text="hasTotal"
      />
      <p class="hint">作业在服务端执行，可以切换到其他菜单；回到本页后进度会继续刷新。</p>
    </div>

    <!-- 3. 结果 -->
    <div v-else class="section">
      <div class="summary">
        <span class="summary-item">新增 <strong>{{ result?.added ?? 0 }}</strong> 个模型</span>
        <span class="summary-item">共 <strong>{{ result?.variants ?? 0 }}</strong> 个变体</span>
        <span class="summary-item muted">扫描 {{ result?.scanned ?? 0 }} 个，已存在的不重复导入</span>
      </div>
      <el-alert
        v-if="result && !result.complete"
        type="warning"
        :closable="false"
        show-icon
        title="官网列表未抓完，可能还有未收录的模型，可以稍后再获取一次"
        class="alert"
      />
      <el-table :data="result?.models || []" size="small" max-height="360" border>
        <el-table-column prop="name" label="模型" min-width="220" show-overflow-tooltip />
        <el-table-column label="下载量" width="110" align="right">
          <template #default="{ row }">{{ row.pullsText || '-' }}</template>
        </el-table-column>
        <el-table-column prop="tagCount" label="变体" width="90" align="right" />
      </el-table>
      <el-empty v-if="!result?.models?.length" description="本次没有发现新模型" :image-size="60" />
    </div>

    <template #footer>
      <template v-if="mode === 'config'">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" :loading="busy" @click="start">开始获取</el-button>
      </template>
      <template v-else-if="mode === 'running'">
        <span class="footer-hint">获取进行中，完成后需要手动关闭</span>
      </template>
      <template v-else>
        <el-button type="primary" @click="close">关闭</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { OllamaTaskStatus } from '@/api/ollamaModel'

const props = defineProps<{
  modelValue: boolean
  task: OllamaTaskStatus | null
  busy?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'start', keywords: string[]): void
  (e: 'close'): void
}>()

const MAX_KEYWORDS = 10

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const keywords = ref<string[]>([])
const input = ref('')
const submitted = ref(false)

const mode = computed<'config' | 'running' | 'result'>(() => {
  const task = props.task
  if (!submitted.value && (!task || task.type !== 'DISCOVER')) return 'config'
  if (task && task.type === 'DISCOVER') {
    return task.running ? 'running' : 'result'
  }
  return 'config'
})

const result = computed(() =>
  props.task && props.task.type === 'DISCOVER' ? props.task.discover : null
)

const hasTotal = computed(() => (props.task?.total ?? 0) > 0)
const percent = computed(() => {
  const task = props.task
  if (!task || task.total <= 0) return 0
  return Math.min(100, Math.round((task.done / task.total) * 100))
})

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    // 打开时如果后端已有 DISCOVER 作业（切菜单回来），直接进入运行/结果视图
    if (props.task?.type === 'DISCOVER') {
      submitted.value = true
    }
  },
  { immediate: true }
)

const addKeyword = () => {
  const kw = input.value.trim()
  if (!kw) return
  if (keywords.value.length >= MAX_KEYWORDS) return
  if (keywords.value.includes(kw)) {
    input.value = ''
    return
  }
  keywords.value = [...keywords.value, kw]
  input.value = ''
}

const removeKeyword = (index: number) => {
  keywords.value = keywords.value.filter((_, i) => i !== index)
}

const start = () => {
  submitted.value = true
  emit('start', [...keywords.value])
}

const close = () => {
  emit('close')
  visible.value = false
}
</script>

<style scoped lang="scss">
.section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tip {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ev-text-secondary);
}

.keyword-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;

  .keyword-tag {
    margin: 0;
  }

  .keyword-input {
    width: 240px;
  }
}

.hint {
  margin: 0;
  font-size: 12px;
  color: var(--ev-text-muted);
}

.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--ev-text-primary);

  .count {
    font-weight: 600;
    color: var(--ev-primary);
  }
}

.summary {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  font-size: 13px;
  color: var(--ev-text-regular);

  strong {
    color: var(--ev-primary);
    font-size: 15px;
  }

  .muted {
    color: var(--ev-text-muted);
  }
}

.alert {
  margin: 0;
}

.footer-hint {
  font-size: 12px;
  color: var(--ev-text-muted);
}
</style>
