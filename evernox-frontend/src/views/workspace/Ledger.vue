<template>
  <div class="ledger-page">
    <!-- 头部统计 -->
    <div class="page-header">
      <div class="header-text">
        <h2>记账</h2>
        <p>只有你自己能看到这些记录</p>
      </div>
      <div class="stat-row">
        <div class="stat">
          <span class="num">{{ formatAmount(stats.total) }}</span>
          <span class="label">范围支出</span>
        </div>
        <div class="stat">
          <span class="num">{{ stats.count }}</span>
          <span class="label">范围笔数</span>
        </div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="action-bar">
      <el-button type="primary" @click="openRecords">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>记账
      </el-button>
      <el-button @click="openCategoryDialog">
        <el-icon style="margin-right: 4px"><Setting /></el-icon>消费类型
      </el-button>
    </div>

    <!-- 曲线图 -->
    <div class="card chart-card">
      <div class="card-head">
        <div class="card-title">消费趋势</div>
        <div class="chart-tools">
          <el-select v-model="selectedMonth" placeholder="选择月份" clearable class="month-select" @change="onMonthChange">
            <el-option label="本月" value="current" />
            <el-option v-for="m in months" :key="m" :label="formatMonth(m)" :value="m" />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            class="range-picker"
            @change="onRangeChange"
          />
        </div>
      </div>
      <ExpenseChart :points="chartPoints" :start-date="chartStart" :end-date="chartEnd" />
    </div>

    <!-- 最近消费 -->
    <div class="card recent-card">
      <div class="card-head">
        <div class="card-title">最近消费</div>
        <el-button text type="primary" @click="openRecords">查看全部</el-button>
      </div>
      <div v-loading="recentLoading" class="record-list">
        <div v-for="r in recentRecords" :key="r.id" class="record-item">
          <div class="record-main">
            <span class="record-date">{{ r.expenseDate }}</span>
            <el-tag size="small" class="record-tag">{{ r.categoryName || '未分类' }}</el-tag>
            <span class="record-remark">{{ r.remark || '—' }}</span>
          </div>
          <div class="record-amount">{{ formatAmount(r.amount) }}</div>
        </div>
        <el-empty v-if="!recentLoading && !recentRecords.length" description="还没有消费记录" />
      </div>
    </div>

    <!-- 消费记录弹窗 -->
    <el-dialog v-model="recordsVisible" title="消费记录" width="760px">
      <div class="records-toolbar">
        <el-button type="primary" @click="openRecordForm()">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>记账
        </el-button>
        <div class="filters">
          <el-select v-model="recordFilter.categoryId" placeholder="全部类型" clearable class="f-cat">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-date-picker
            v-model="recordFilter.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            class="f-range"
          />
          <el-input
            v-model="recordFilter.keyword"
            placeholder="搜索备注"
            clearable
            class="f-keyword"
            @keyup.enter="reloadRecords"
          />
          <el-button @click="reloadRecords">查询</el-button>
        </div>
      </div>

      <div v-loading="listLoading" class="records-list">
        <div v-for="r in records" :key="r.id" class="record-item">
          <div class="record-main">
            <span class="record-date">{{ r.expenseDate }}</span>
            <el-tag size="small" class="record-tag">{{ r.categoryName || '未分类' }}</el-tag>
            <span class="record-remark">{{ r.remark || '—' }}</span>
          </div>
          <div class="record-amount">{{ formatAmount(r.amount) }}</div>
          <div class="record-actions">
            <el-button size="small" text @click="openRecordForm(r)">编辑</el-button>
            <el-button size="small" text type="danger" @click="handleDelete(r)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!listLoading && !records.length" description="没有符合条件的消费记录" />
      </div>

      <div v-if="recordsTotal > PAGE_SIZE" class="pagination-wrap">
        <el-pagination
          v-model:current-page="recordsPage"
          :page-size="PAGE_SIZE"
          :total="recordsTotal"
          layout="prev, pager, next, total"
          background
          @current-change="loadRecordsPage"
        />
      </div>
    </el-dialog>

    <!-- 记一笔 / 编辑消费弹窗 -->
    <el-dialog v-model="recordFormVisible" :title="editingId ? '编辑消费' : '记一笔'" width="460px">
      <el-form label-width="80px">
        <el-form-item label="日期">
          <el-date-picker v-model="form.expenseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="类型">
          <div class="form-cat">
            <el-select v-model="form.categoryId" placeholder="选择类型" class="form-cat-select">
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
            <el-button text type="primary" @click="openCategoryDialog">管理类型</el-button>
          </div>
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="form.amount" :min="0.01" :max="9999999999.99" :precision="2" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" placeholder="备注（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recordFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveForm">{{ editingId ? '保存' : '记下' }}</el-button>
      </template>
    </el-dialog>

    <!-- 消费类型弹窗 -->
    <el-dialog v-model="categoryDialogVisible" title="消费类型" width="480px">
      <div class="cat-add-row">
        <el-input v-model="newCategoryName" placeholder="新类型名称" maxlength="50" @keyup.enter="handleAddCategory" />
        <el-button type="primary" :loading="addingCategory" @click="handleAddCategory">添加</el-button>
      </div>
      <div class="cat-list">
        <div v-for="c in categories" :key="c.id" class="cat-item">
          <template v-if="editingCategoryId === c.id">
            <el-input v-model="editCategoryName" maxlength="50" class="cat-edit-input" />
            <el-button size="small" type="primary" @click="handleRenameCategory(c.id)">保存</el-button>
            <el-button size="small" @click="editingCategoryId = null">取消</el-button>
          </template>
          <template v-else>
            <span class="cat-name">{{ c.name }}</span>
            <span class="cat-ops">
              <el-button size="small" text @click="startEditCategory(c)">重命名</el-button>
              <el-button size="small" text type="danger" @click="handleDeleteCategory(c)">删除</el-button>
            </span>
          </template>
        </div>
        <el-empty v-if="!categories.length" description="还没有类型，先添加一个吧" :image-size="60" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ExpenseChart from '@/components/ExpenseChart.vue'
import {
  createCategory,
  createRecord,
  deleteCategory,
  deleteRecord,
  getChart,
  getMonths,
  listCategories,
  listRecords,
  updateCategory,
  updateRecord,
} from '@/api/expense'
import type {
  ExpenseCategory,
  ExpenseChartPoint,
  ExpenseRecord,
} from '@/types/expense'

const RECENT_SIZE = 5
const PAGE_SIZE = 10

// ---------- 数据 ----------
const categories = ref<ExpenseCategory[]>([])
const months = ref<string[]>([])
const chartPoints = ref<ExpenseChartPoint[]>([])
const chartStart = ref('')
const chartEnd = ref('')
const stats = reactive({ total: 0, count: 0 })

// 最近消费
const recentRecords = ref<ExpenseRecord[]>([])
const recentLoading = ref(false)

// 消费记录弹窗
const recordsVisible = ref(false)
const records = ref<ExpenseRecord[]>([])
const recordsTotal = ref(0)
const recordsPage = ref(1)
const listLoading = ref(false)
const recordFilter = reactive({
  categoryId: null as number | null,
  dateRange: null as [string, string] | null,
  keyword: '',
})

// 记一笔 / 编辑
const recordFormVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({
  expenseDate: '',
  categoryId: null as number | null,
  amount: 0.01,
  remark: '',
})

// 消费类型
const categoryDialogVisible = ref(false)
const newCategoryName = ref('')
const addingCategory = ref(false)
const editingCategoryId = ref<number | null>(null)
const editCategoryName = ref('')

// 图表范围
const selectedMonth = ref('current')
const dateRange = ref<[string, string] | null>(null)

// ---------- 工具 ----------
function formatDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const formatAmount = (v: number): string =>
  '¥' + Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const formatMonth = (ym: string): string => {
  const [y, m] = ym.split('-')
  return `${y}年${Number(m)}月`
}

const currentMonth = (): string => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

const monthRange = (ym: string): [string, string] => {
  const [y, m] = ym.split('-').map(Number)
  return [formatDate(new Date(y, m - 1, 1)), formatDate(new Date(y, m, 0))]
}

// ---------- 图表 ----------
const loadChart = async (start: string, end: string) => {
  try {
    const res = await getChart(start, end)
    chartPoints.value = res.data?.points ?? []
    stats.total = res.data?.totalAmount ?? 0
    stats.count = res.data?.count ?? 0
  } catch {
    /* 请求层已提示 */
  }
}

const applyMonth = (ym: string) => {
  const [start, end] = monthRange(ym === 'current' ? currentMonth() : ym)
  chartStart.value = start
  chartEnd.value = end
  dateRange.value = null
  void loadChart(start, end)
}

const onMonthChange = (val: string | null) => {
  if (!val) return
  applyMonth(val)
}

const onRangeChange = (val: [string, string] | null) => {
  if (!val || !val[0] || !val[1]) {
    selectedMonth.value = 'current'
    applyMonth('current')
    return
  }
  selectedMonth.value = ''
  chartStart.value = val[0]
  chartEnd.value = val[1]
  void loadChart(val[0], val[1])
}

// ---------- 类型 ----------
const loadCategories = async () => {
  try {
    const res = await listCategories()
    categories.value = res.data ?? []
  } catch {
    /* 请求层已提示 */
  }
}

const loadMonths = async () => {
  try {
    const res = await getMonths()
    months.value = res.data ?? []
  } catch {
    /* 请求层已提示 */
  }
}

const openCategoryDialog = () => {
  editingCategoryId.value = null
  newCategoryName.value = ''
  categoryDialogVisible.value = true
}

const handleAddCategory = async () => {
  const name = newCategoryName.value.trim()
  if (!name) {
    ElMessage.warning('请输入类型名称')
    return
  }
  addingCategory.value = true
  try {
    await createCategory({ name })
    newCategoryName.value = ''
    await loadCategories()
  } catch {
    /* 请求层已提示 */
  } finally {
    addingCategory.value = false
  }
}

const startEditCategory = (c: ExpenseCategory) => {
  editingCategoryId.value = c.id
  editCategoryName.value = c.name
}

const handleRenameCategory = async (id: number) => {
  const name = editCategoryName.value.trim()
  if (!name) {
    ElMessage.warning('类型名称不能为空')
    return
  }
  try {
    await updateCategory(id, { name })
    editingCategoryId.value = null
    await loadCategories()
  } catch {
    /* 请求层已提示 */
  }
}

const handleDeleteCategory = async (c: ExpenseCategory) => {
  try {
    await ElMessageBox.confirm(`确定删除类型「${c.name}」？`, '删除类型', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteCategory(c.id)
    if (editingCategoryId.value === c.id) editingCategoryId.value = null
    await loadCategories()
  } catch {
    /* 请求层已提示 */
  }
}

// ---------- 最近消费 ----------
const loadRecent = async () => {
  recentLoading.value = true
  try {
    const res = await listRecords({ page: 1, size: RECENT_SIZE })
    recentRecords.value = res.data?.records ?? []
  } catch {
    /* 请求层已提示 */
  } finally {
    recentLoading.value = false
  }
}

// ---------- 消费记录弹窗 ----------
const openRecords = () => {
  // 每次打开都回到「全部记录」的默认视图
  recordFilter.categoryId = null
  recordFilter.dateRange = null
  recordFilter.keyword = ''
  recordsPage.value = 1
  recordsVisible.value = true
  void loadRecordsPage(1)
}

const loadRecordsPage = async (page: number) => {
  listLoading.value = true
  try {
    const res = await listRecords({
      page,
      size: PAGE_SIZE,
      categoryId: recordFilter.categoryId,
      startDate: recordFilter.dateRange?.[0] ?? null,
      endDate: recordFilter.dateRange?.[1] ?? null,
      keyword: recordFilter.keyword.trim() || null,
    })
    records.value = res.data?.records ?? []
    recordsTotal.value = res.data?.total ?? 0
    recordsPage.value = page
  } catch {
    /* 请求层已提示 */
  } finally {
    listLoading.value = false
  }
}

const reloadRecords = () => {
  recordsPage.value = 1
  void loadRecordsPage(1)
}

// ---------- 记一笔 / 编辑 ----------
const openRecordForm = (record?: ExpenseRecord) => {
  if (record) {
    editingId.value = record.id
    form.expenseDate = record.expenseDate
    form.categoryId = record.categoryId
    form.amount = record.amount
    form.remark = record.remark ?? ''
  } else {
    editingId.value = null
    form.expenseDate = formatDate(new Date())
    form.categoryId = null
    form.amount = 0.01
    form.remark = ''
  }
  recordFormVisible.value = true
}

const saveForm = async () => {
  if (!form.categoryId) {
    ElMessage.warning('请选择消费类型')
    return
  }
  if (!form.amount || form.amount <= 0) {
    ElMessage.warning('请填写消费金额')
    return
  }
  if (!form.expenseDate) {
    ElMessage.warning('请选择日期')
    return
  }
  saving.value = true
  const payload = {
    categoryId: form.categoryId,
    amount: form.amount,
    expenseDate: form.expenseDate,
    remark: form.remark.trim() || null,
  }
  try {
    if (editingId.value) {
      await updateRecord(editingId.value, payload)
    } else {
      await createRecord(payload)
    }
    recordFormVisible.value = false
    await refreshAfterMutation()
  } catch {
    /* 请求层已提示 */
  } finally {
    saving.value = false
  }
}

const handleDelete = async (r: ExpenseRecord) => {
  try {
    await ElMessageBox.confirm(`确定删除这条消费记录（${formatAmount(r.amount)}）？`, '删除消费', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    // 当前页仅剩一条且非第一页时，删除后回退一页，避免落在空页
    const shouldGoBack = recordsVisible.value && records.value.length === 1 && recordsPage.value > 1
    await deleteRecord(r.id)
    if (shouldGoBack) recordsPage.value -= 1
    await refreshAfterMutation()
  } catch {
    /* 请求层已提示 */
  }
}

// ---------- 汇总刷新 ----------
const refreshAfterMutation = async () => {
  await Promise.all([
    loadChart(chartStart.value, chartEnd.value),
    loadMonths(),
    loadRecent(),
    recordsVisible.value ? loadRecordsPage(recordsPage.value) : Promise.resolve(),
  ])
}

// ---------- 初始化 ----------
onMounted(async () => {
  await Promise.all([loadCategories(), loadMonths()])
  applyMonth('current')
  await loadRecent()
})
</script>

<style scoped lang="scss">
.ledger-page {
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
    color: var(--ev-primary, #2f7cf6);
  }

  .label {
    font-size: 12px;
    color: var(--ev-text-muted, #90a4bb);
  }
}

.action-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.card {
  border: 1px solid var(--ev-border-subtle, rgba(47, 124, 246, 0.08));
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.66);
  backdrop-filter: blur(20px) saturate(160%);
  -webkit-backdrop-filter: blur(20px) saturate(160%);
  box-shadow: 0 1px 2px rgba(18, 48, 79, 0.04), 0 8px 24px -8px rgba(47, 124, 246, 0.12);
  padding: 18px 20px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--ev-text-primary, #12304f);
}

.chart-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.month-select {
  width: 140px;
}

.range-picker {
  width: 260px;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 80px;
}

.record-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.6);

  &:hover {
    border-color: var(--el-color-primary-light-5);
  }
}

.record-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.record-date {
  font-size: 13px;
  color: var(--ev-text-secondary, #4c6b8a);
  font-variant-numeric: tabular-nums;
}

.record-tag {
  flex: 0 0 auto;
}

.record-remark {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--ev-text-muted, #90a4bb);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-amount {
  font-size: 15px;
  font-weight: 700;
  color: var(--ev-text-primary, #12304f);
  font-variant-numeric: tabular-nums;
}

.record-actions {
  flex: 0 0 auto;
}

/* 消费记录弹窗 */
.records-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.f-cat {
  width: 130px;
}

.f-range {
  width: 240px;
}

.f-keyword {
  width: 150px;
}

.records-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 200px;
  max-height: 420px;
  overflow-y: auto;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 14px;
}

/* 记一笔表单 */
.form-cat {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.form-cat-select {
  flex: 1;
}

/* 消费类型弹窗 */
.cat-add-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.cat-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 320px;
  overflow-y: auto;
}

.cat-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.6);
}

.cat-name {
  flex: 1;
  font-size: 14px;
  color: var(--ev-text-primary, #12304f);
}

.cat-edit-input {
  flex: 1;
}

.cat-ops {
  flex: 0 0 auto;
}
</style>
