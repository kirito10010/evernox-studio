<template>
  <div class="todos-page">
    <div class="page-header">
      <div class="header-text">
        <h2>待办</h2>
        <p>只有你自己能看到这些条目</p>
      </div>
    </div>

    <div class="todos-layout">
      <div class="todos-main">
        <div class="quick-add">
      <el-input
        v-model="draft.content"
        placeholder="要做什么？回车即添加"
        maxlength="500"
        clearable
        @keyup.enter="handleCreate"
      />
      <el-select v-model="draft.priority" class="priority-select">
        <el-option v-for="(label, value) in TodoPriorityMap" :key="value" :label="`${label}优先`" :value="Number(value)" />
      </el-select>
      <el-date-picker
        v-model="draft.dueDate"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="截止日期"
        class="date-picker"
      />
      <el-button type="primary" :loading="creating" @click="handleCreate">添加</el-button>
    </div>

    <div class="filter-bar">
      <el-radio-group v-model="doneFilter" @change="reload">
        <el-radio-button :value="0">未完成</el-radio-button>
        <el-radio-button :value="1">已完成</el-radio-button>
        <el-radio-button :value="-1">全部</el-radio-button>
      </el-radio-group>
      <el-select v-model="priorityFilter" class="filter-item" placeholder="全部优先级" clearable @change="reload">
        <el-option v-for="(label, value) in TodoPriorityMap" :key="value" :label="label" :value="Number(value)" />
      </el-select>
      <el-select v-model="dueScope" class="filter-item" placeholder="全部时间" clearable @change="reload">
        <el-option label="今天到期" value="today" />
        <el-option label="七天内" value="week" />
        <el-option label="已逾期" value="overdue" />
      </el-select>
    </div>
    <div v-loading="loading" class="todo-list">
      <div
        v-for="todo in pendingTodos"
        :key="todo.id"
        class="todo-item"
        :class="[`priority-${todo.priority}`, { overdue: todo.overdue }]"
      >
        <el-checkbox :model-value="todo.done === 1" @change="toggleDone(todo)" />
        <div class="todo-main">
          <span class="content">{{ todo.content }}</span>
          <div class="meta">
            <span class="priority-tag">{{ TodoPriorityMap[todo.priority] }}优先</span>
            <span v-if="todo.dueDate" class="due" :class="{ overdue: todo.overdue }">
              <el-icon :size="13"><Calendar /></el-icon>
              <span>{{ todo.overdue ? '已逾期 · ' : '' }}{{ todo.dueDate }}</span>
            </span>
          </div>
        </div>
        <div class="todo-actions">
          <el-button size="small" text title="编辑" @click="openEdit(todo)"><el-icon><Edit /></el-icon></el-button>
          <el-button size="small" text type="danger" title="删除" @click="handleDelete(todo)"><el-icon><Delete /></el-icon></el-button>
        </div>
      </div>

      <el-empty v-if="!loading && !todos.length" description="还没有待办，先加一条吧" />

      <template v-if="doneTodos.length">
        <div class="done-header" @click="doneCollapsed = !doneCollapsed">
          <el-icon><ArrowRight v-if="doneCollapsed" /><ArrowDown v-else /></el-icon>
          <span>已完成（{{ doneTodos.length }}）</span>
        </div>
        <template v-if="!doneCollapsed">
          <div v-for="todo in doneTodos" :key="todo.id" class="todo-item is-done">
            <el-checkbox :model-value="true" @change="toggleDone(todo)" />
            <div class="todo-main">
              <span class="content">{{ todo.content }}</span>
              <div class="meta">
                <span v-if="todo.finishedAt" class="due">
                  <el-icon :size="13"><CircleCheck /></el-icon>
                  <span>完成于 {{ todo.finishedAt }}</span>
                </span>
              </div>
            </div>
            <div class="todo-actions">
              <el-button size="small" text type="danger" title="删除" @click="handleDelete(todo)"><el-icon><Delete /></el-icon></el-button>
            </div>
          </div>
        </template>
      </template>

      <div v-if="hasMore" class="list-footer">
        <el-button text :loading="loadingMore" @click="loadMore">加载更多</el-button>
      </div>
    </div>

      </div>

      <aside class="todos-aside">
        <div class="aside-card">
          <div class="aside-title">完成进度</div>
          <el-progress :percentage="completionRate" :stroke-width="10" />
          <div class="mini-stats">
            <div class="mini-stat"><span class="num">{{ stats.pending ?? '—' }}</span><span class="label">未完成</span></div>
            <div class="mini-stat"><span class="num">{{ stats.dueToday ?? '—' }}</span><span class="label">今日到期</span></div>
            <div class="mini-stat danger"><span class="num">{{ stats.overdue ?? '—' }}</span><span class="label">已逾期</span></div>
            <div class="mini-stat"><span class="num">{{ stats.done ?? '—' }}</span><span class="label">已完成</span></div>
          </div>
        </div>

        <div class="aside-card">
          <div class="aside-title">小贴士</div>
          <ul class="tips">
            <li><el-icon><Flag /></el-icon><span>高优先级的事先做</span></li>
            <li><el-icon><Calendar /></el-icon><span>给重要任务设置截止日期</span></li>
            <li><el-icon><CircleCheck /></el-icon><span>完成后及时勾选，保持清单清爽</span></li>
          </ul>
        </div>
      </aside>
    </div>

    <el-dialog v-model="editVisible" title="编辑待办" width="460px">
      <el-form label-width="80px">
        <el-form-item label="内容">
          <el-input v-model="editForm.content" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="editForm.priority">
            <el-option v-for="(label, value) in TodoPriorityMap" :key="value" :label="label" :value="Number(value)" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="editForm.dueDate" type="date" value-format="YYYY-MM-DD" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleUpdate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createTodo,
  deleteTodo,
  getTodoStats,
  getTodos,
  setTodoDone,
  updateTodo,
} from '@/api/todo'
import { TodoPriorityMap } from '@/types/todo'
import type { Todo, TodoDueScope, TodoStats } from '@/types/todo'

const PAGE_SIZE = 50

const todos = ref<Todo[]>([])
const total = ref(0)
const currentPage = ref(1)
const loading = ref(false)
const loadingMore = ref(false)
const creating = ref(false)
const saving = ref(false)
const doneCollapsed = ref(true)
const stats = ref<TodoStats>({ pending: null, dueToday: null, overdue: null, done: null })

/** -1 表示不限：el-radio-button 的 value 不接受 null，请求时再转回 undefined */
const doneFilter = ref(0)
const priorityFilter = ref<number | null>(null)
const dueScope = ref<TodoDueScope | null>(null)

const draft = reactive({ content: '', priority: 1, dueDate: null as string | null })

const editVisible = ref(false)
const editingId = ref<number | null>(null)
const editForm = reactive({ content: '', priority: 1, dueDate: null as string | null })

const pendingTodos = computed(() => todos.value.filter((t) => t.done !== 1))
const doneTodos = computed(() => todos.value.filter((t) => t.done === 1))
const hasMore = computed(() => todos.value.length < total.value)

const completionRate = computed(() => {
  const done = stats.value.done ?? 0
  const pending = stats.value.pending ?? 0
  const total = done + pending
  return total > 0 ? Math.round((done / total) * 100) : 0
})

const fetchPage = async (page: number) => {
  const res = await getTodos({
    page,
    size: PAGE_SIZE,
    done: doneFilter.value >= 0 ? doneFilter.value : null,
    priority: priorityFilter.value,
    dueScope: dueScope.value,
  })
  total.value = res.data?.total || 0
  return res.data?.records || []
}
const reload = async () => {
  loading.value = true
  currentPage.value = 1
  try {
    todos.value = await fetchPage(1)
  } catch {
    /* 请求层已提示 */
  } finally {
    loading.value = false
  }
}

const loadMore = async () => {
  if (loadingMore.value) return
  loadingMore.value = true
  try {
    const next = currentPage.value + 1
    const records = await fetchPage(next)
    todos.value.push(...records)
    currentPage.value = next
  } catch {
    /* 请求层已提示 */
  } finally {
    loadingMore.value = false
  }
}

const loadStats = async () => {
  try {
    const res = await getTodoStats()
    if (res.data) stats.value = res.data
  } catch {
    /* 请求层已提示 */
  }
}

const handleCreate = async () => {
  const content = draft.content.trim()
  if (!content) {
    ElMessage.warning('请输入待办内容')
    return
  }
  creating.value = true
  try {
    await createTodo({ content, priority: draft.priority, dueDate: draft.dueDate })
    draft.content = ''
    draft.dueDate = null
    await Promise.all([reload(), loadStats()])
  } catch {
    /* 请求层已提示 */
  } finally {
    creating.value = false
  }
}

/**
 * 就地更新而不整页重载
 *
 * 勾选是高频操作，重载会让列表跳动、丢失滚动位置。
 * 但筛选条件为「未完成/已完成」时该条目已不属于当前筛选，此时才移除。
 */
const toggleDone = async (todo: Todo) => {
  const next = todo.done !== 1
  try {
    const res = await setTodoDone(todo.id, next)
    const updated = res.data
    if (!updated) return
    if (doneFilter.value >= 0) {
      todos.value = todos.value.filter((t) => t.id !== todo.id)
      total.value = Math.max(total.value - 1, 0)
    } else {
      Object.assign(todo, updated)
    }
    await loadStats()
  } catch {
    /* 请求层已提示 */
  }
}
const openEdit = (todo: Todo) => {
  editingId.value = todo.id
  editForm.content = todo.content
  editForm.priority = todo.priority
  editForm.dueDate = todo.dueDate
  editVisible.value = true
}

const handleUpdate = async () => {
  if (!editingId.value) return
  const content = editForm.content.trim()
  if (!content) {
    ElMessage.warning('内容不能为空')
    return
  }
  saving.value = true
  try {
    const res = await updateTodo(editingId.value, {
      content,
      priority: editForm.priority,
      dueDate: editForm.dueDate,
    })
    const updated = res.data
    const target = todos.value.find((t) => t.id === editingId.value)
    if (updated && target) Object.assign(target, updated)
    editVisible.value = false
    await loadStats()
  } catch {
    /* 请求层已提示 */
  } finally {
    saving.value = false
  }
}

const handleDelete = async (todo: Todo) => {
  try {
    await ElMessageBox.confirm(`确定删除「${todo.content.slice(0, 20)}」？`, '删除待办', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteTodo(todo.id)
    todos.value = todos.value.filter((t) => t.id !== todo.id)
    total.value = Math.max(total.value - 1, 0)
    await loadStats()
  } catch {
    /* 请求层已提示 */
  }
}

onMounted(() => {
  void reload()
  void loadStats()
})
</script>
<style scoped lang="scss">
.todos-page {
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

.todos-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.todos-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.todos-aside {
  width: 300px;
  flex-shrink: 0;
  position: sticky;
  top: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.aside-card {
  padding: 16px;
  border-radius: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
}

.aside-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
}

.mini-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 14px;
}

.mini-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 0;
  border-radius: 8px;
  background: var(--el-fill-color-light);

  .num {
    font-size: 18px;
    font-weight: 600;
  }

  .label {
    font-size: 12px;
    color: var(--ev-text-muted, #90a4bb);
  }

  &.danger .num {
    color: var(--el-color-danger);
  }
}

.tips {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;

  li {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: var(--el-text-color-regular);

    .el-icon {
      color: var(--el-color-primary);
    }
  }
}

@media (max-width: 900px) {
  .todos-layout {
    flex-direction: column;
  }

  .todos-aside {
    width: 100%;
    position: static;
  }
}
.quick-add {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  /* 超宽屏下不必铺满整行，单行输入框拉到上千像素反而难读 */
  max-width: 900px;
}

.quick-add :deep(.el-input) {
  flex: 1 1 320px;
  max-width: 420px;
}

.priority-select {
  width: 120px;
  flex: 0 0 auto;
}

/* Element Plus 给 .el-date-editor 设了 220px 的默认宽度，必须连 CSS 变量一起覆写 */
.date-picker {
  flex: 0 0 auto;
}

.quick-add :deep(.el-date-editor.el-input) {
  --el-date-editor-width: 160px;
  width: 160px;
  flex: 0 0 auto;
  max-width: 160px;
}

@media (max-width: 700px) {
  .quick-add :deep(.el-input),
  .quick-add :deep(.el-date-editor.el-input),
  .priority-select {
    flex: 1 1 100%;
    max-width: none;
    width: 100%;
  }
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  /* 与快速添加区同宽，两行左对齐看起来才整齐 */
  max-width: 900px;
}

.filter-item {
  width: 140px;
  flex: 0 0 auto;
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 200px;
}

.todo-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px 12px 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-bg-color);
  overflow: hidden;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;

  // 左侧优先级色条
  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 4px;
    background: var(--el-text-color-placeholder);
  }

  &.priority-1::before {
    background: var(--el-color-primary);
  }

  &.priority-2::before,
  &.overdue::before {
    background: var(--el-color-danger);
  }

  &:hover {
    border-color: var(--el-color-primary-light-5);
    box-shadow: 0 2px 10px rgba(47, 124, 246, 0.08);
  }

  &.is-done {
    opacity: 0.6;

    .content {
      color: var(--el-text-color-placeholder);
      text-decoration: line-through;
    }
  }
}
.todo-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.content {
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
  color: var(--el-text-color-primary);
}

.meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--ev-text-muted, #90a4bb);
}

.priority-tag {
  padding: 1px 8px;
  border-radius: 6px;
  line-height: 18px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
}

.todo-item.priority-1 .priority-tag {
  background: rgba(64, 158, 255, 0.12);
  color: var(--el-color-primary);
}

.todo-item.priority-2 .priority-tag {
  background: rgba(245, 108, 108, 0.12);
  color: var(--el-color-danger);
}

.due {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.due.overdue {
  color: var(--el-color-danger);
  font-weight: 500;
}

.todo-actions {
  flex: 0 0 auto;
  display: flex;
  gap: 2px;
}

.done-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 13px;
  color: var(--ev-text-muted, #90a4bb);
  cursor: pointer;
  user-select: none;
}

.list-footer {
  display: flex;
  justify-content: center;
  padding: 6px 0;
}
</style>

