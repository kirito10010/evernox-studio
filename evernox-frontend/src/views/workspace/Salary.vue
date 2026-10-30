<template>
  <div v-if="userStore.isLoggedIn" class="salary-page">
    <!-- 头部 -->
    <div class="page-header">
      <div class="header-text">
        <h2>记录工资</h2>
        <p>只有你自己能看到这些记录</p>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="action-bar">
      <el-button type="primary" @click="openAdd">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>添加工资
      </el-button>
      <el-button @click="openRecords">
        <el-icon style="margin-right: 4px"><Document /></el-icon>工资记录
      </el-button>
      <el-button @click="openConfig">
        <el-icon style="margin-right: 4px"><Setting /></el-icon>工资配置
      </el-button>
    </div>

    <!-- 工资曲线 -->
    <div class="card chart-card">
      <div class="card-head">
        <div class="card-title">工资曲线</div>
        <el-date-picker
          v-model="chartYear"
          type="year"
          value-format="YYYY"
          placeholder="选择年份"
          class="year-picker"
          :clearable="false"
        />
      </div>
      <SalaryChart :year="chartYear" :points="chartPoints" />
    </div>

    <!-- 最近工资 -->
    <div class="card recent-card">
      <div class="card-head">
        <div class="card-title">最近工资</div>
        <el-button type="primary" text @click="openRecords">查看全部</el-button>
      </div>
      <div v-loading="listLoading" class="record-list">
        <div v-for="r in recentRecords" :key="r.id ?? r.month" class="record-item">
          <div class="record-main">
            <span class="record-month">{{ formatMonth(r.month) }}</span>
            <el-tag size="small" class="record-tag">{{ r.startDate }} ~ {{ r.endDate }}</el-tag>
            <span class="record-meta">应出勤 {{ formatNum(r.attendanceDays) }} 天</span>
            <span class="record-meta">净绩效 {{ formatNum(r.performanceDays) }}</span>
            <span class="record-meta">绩效薪资 {{ formatMoney(r.performanceSalary) }}</span>
          </div>
          <div class="record-total">{{ formatMoney(r.totalSalary) }}</div>
        </div>
        <el-empty v-if="!listLoading && !recentRecords.length" description="还没有工资记录" />
      </div>
    </div>

    <!-- 工资记录弹窗（全部数据 / 搜索 / 分页 / 编辑 / 删除） -->
    <el-dialog
      v-model="recordsVisible"
      title="工资记录"
      width="1100px"
      align-center
      append-to-body
      body-class="ev-dialog-body"
    >
      <div class="record-tools">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索月份，如 2026-09"
          clearable
          style="width: 240px"
          @input="onSearch"
        />
        <el-select v-model="sortMode" class="sort-select" @change="onSearch">
          <el-option label="月份（新 → 旧）" value="month-desc" />
          <el-option label="月份（旧 → 新）" value="month-asc" />
          <el-option label="工资（高 → 低）" value="salary-desc" />
          <el-option label="工资（低 → 高）" value="salary-asc" />
        </el-select>
      </div>

      <el-table :data="pagedRecords" v-loading="listLoading" stripe>
        <el-table-column label="月份" width="110">
          <template #default="{ row }">{{ formatMonth(row.month) }}</template>
        </el-table-column>
        <el-table-column label="结算周期" min-width="190">
          <template #default="{ row }">
            <el-tag size="small">{{ row.startDate }} ~ {{ row.endDate }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="应出勤" width="90" align="right">
          <template #default="{ row }">{{ formatNum(row.attendanceDays) }}</template>
        </el-table-column>
        <el-table-column label="实际出勤" width="100" align="right">
          <template #default="{ row }">{{ formatNum(row.actualAttendanceDays) }}</template>
        </el-table-column>
        <el-table-column label="净绩效" width="100" align="right">
          <template #default="{ row }">{{ formatNum(row.performanceDays) }}</template>
        </el-table-column>
        <el-table-column label="绩效薪资" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.performanceSalary) }}</template>
        </el-table-column>
        <el-table-column label="加班" width="110" align="right">
          <template #default="{ row }">
            {{ formatNum(row.overtimeDays) }} 天 / {{ formatMoney(row.overtimeSalary) }}
          </template>
        </el-table-column>
        <el-table-column label="迟到" width="90" align="right">
          <template #default="{ row }">{{ row.lateMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="出勤比" width="90" align="right">
          <template #default="{ row }">{{ formatRatio(row.attendanceRatio) }}</template>
        </el-table-column>
        <el-table-column label="实发工资" width="130" align="right">
          <template #default="{ row }">
            <span class="record-total">{{ formatMoney(row.totalSalary) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openEditRow(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="handleDeleteRow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="filteredRecords.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="onPageChange"
          @size-change="onPageSizeChange"
        />
      </div>
    </el-dialog>

    <!-- 添加工资弹窗 -->
    <el-dialog v-model="addVisible" :title="dialogTitle" width="680px" align-center>
      <div class="salary-toolbar">
        <el-date-picker
          v-model="addMonth"
          type="month"
          value-format="YYYY-MM"
          placeholder="选择月份"
          class="month-picker"
          @change="onMonthChange"
        />
      </div>

      <div v-loading="previewLoading" class="salary-detail">
        <template v-if="previewData">
          <div class="detail-row">
            <span class="detail-label">日期范围</span>
            <span class="detail-value">{{ previewData.startDate }} ~ {{ previewData.endDate }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">实际上班天数</span>
            <span class="detail-value">{{ formatNum(previewData.actualAttendanceDays) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">应出勤天数</span>
            <el-input-number
              v-model="attendanceDays"
              :min="0"
              :precision="5"
              :step="1"
              class="attendance-input"
              @change="onAttendanceChange"
            />
          </div>
          <div class="detail-row">
            <span class="detail-label">绩效（净）</span>
            <span class="detail-value">{{ formatNum(previewData.performanceDays) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">绩效薪资</span>
            <span class="detail-value">{{ formatMoney(previewData.performanceSalary) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">加班天数</span>
            <span class="detail-value">{{ formatNum(previewData.overtimeDays) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">加班工资</span>
            <span class="detail-value">{{ formatMoney(previewData.overtimeSalary) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">出勤比</span>
            <span class="detail-value">{{ formatRatio(previewData.attendanceRatio) }}</span>
          </div>
          <div class="detail-divider"></div>
          <div class="detail-row">
            <span class="detail-label">基本薪资</span>
            <span class="detail-value">{{ formatMoney(previewData.baseSalary) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">岗位绩效</span>
            <span class="detail-value">{{ formatMoney(previewData.postPerformance) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">餐补</span>
            <span class="detail-value">{{ formatMoney(previewData.mealAllowance) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">房补</span>
            <span class="detail-value">{{ formatMoney(previewData.housingAllowance) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">全勤奖</span>
            <span class="detail-value">{{ formatMoney(previewData.fullAttendanceBonus) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">其他奖金</span>
            <span class="detail-value">{{ formatMoney(previewData.otherBonus) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">养老保险</span>
            <span class="detail-value deduct">-{{ formatMoney(previewData.pension) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">医疗保险</span>
            <span class="detail-value deduct">-{{ formatMoney(previewData.medicalInsurance) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">失业保险</span>
            <span class="detail-value deduct">-{{ formatMoney(previewData.unemploymentInsurance) }}</span>
          </div>
          <div class="detail-divider"></div>
          <div class="detail-row total-row">
            <span class="detail-label">合计</span>
            <span class="detail-value">{{ formatMoney(previewData.totalSalary) }}</span>
          </div>
        </template>
        <el-empty v-else-if="!previewLoading" description="请选择月份" />
      </div>

      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 工资配置弹窗 -->
    <el-dialog v-model="configVisible" title="工资配置" width="560px" align-center>
      <el-form v-loading="configLoading" label-width="120px">
        <el-form-item label="基本薪资">
          <el-input-number v-model="configForm.baseSalary" :min="0" :precision="5" :step="1" class="config-input" />
        </el-form-item>
        <el-form-item label="岗位绩效">
          <el-input-number v-model="configForm.postPerformance" :min="0" :precision="5" :step="1" class="config-input" />
        </el-form-item>
        <el-form-item label="餐补">
          <el-input-number v-model="configForm.mealAllowance" :min="0" :precision="5" :step="1" class="config-input" />
        </el-form-item>
        <el-form-item label="房补">
          <el-input-number v-model="configForm.housingAllowance" :min="0" :precision="5" :step="1" class="config-input" />
        </el-form-item>
        <el-form-item label="全勤奖">
          <el-input-number v-model="configForm.fullAttendanceBonus" :min="0" :precision="5" :step="1" class="config-input" />
        </el-form-item>
        <el-form-item label="其他奖金">
          <el-input-number v-model="configForm.otherBonus" :min="0" :precision="5" :step="1" class="config-input" />
        </el-form-item>
        <el-form-item label="养老保险">
          <el-input-number v-model="configForm.pension" :min="0" :precision="5" :step="1" class="config-input" />
        </el-form-item>
        <el-form-item label="医疗保险">
          <el-input-number v-model="configForm.medicalInsurance" :min="0" :precision="5" :step="1" class="config-input" />
        </el-form-item>
        <el-form-item label="失业保险">
          <el-input-number v-model="configForm.unemploymentInsurance" :min="0" :precision="5" :step="1" class="config-input" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="primary" :loading="configSaving" @click="handleSaveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
  <AuthRequired
    v-else
    title="登录后记录工资"
    description="登录后可记录每月工资、社保公积金配置并查看趋势。"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SalaryChart from '@/components/SalaryChart.vue'
import { createRecord, deleteRecord, getConfig, listRecords, preview, updateConfig, updateRecord } from '@/api/salary'
import type { SalaryRecord } from '@/types/salary'
import AuthRequired from '@/components/auth/AuthRequired.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

// ---------- 数据 ----------
const records = ref<SalaryRecord[]>([])
const listLoading = ref(false)

// 曲线图与列表
type SortMode = 'month-desc' | 'month-asc' | 'salary-desc' | 'salary-asc'
const chartYear = ref('')
const sortMode = ref<SortMode>('month-desc')

// 列表搜索 / 分页
const searchKeyword = ref('')
const page = ref(1)
const pageSize = ref(10)

const chartPoints = computed<number[]>(() => {
  const values = new Array<number>(12).fill(0)
  for (const r of records.value) {
    const [y, m] = r.month.split('-')
    if (y === chartYear.value) {
      const idx = Number(m) - 1
      if (idx >= 0 && idx < 12) values[idx] = Number(r.totalSalary) || 0
    }
  }
  return values
})

const filteredRecords = computed<SalaryRecord[]>(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  const list = records.value.filter((r) => {
    if (!kw) return true
    return (
      r.month.toLowerCase().includes(kw) ||
      formatMonth(r.month).includes(kw) ||
      String(r.totalSalary).includes(kw)
    )
  })
  switch (sortMode.value) {
    case 'month-asc':
      list.sort((a, b) => a.month.localeCompare(b.month))
      break
    case 'salary-desc':
      list.sort((a, b) => (Number(b.totalSalary) || 0) - (Number(a.totalSalary) || 0))
      break
    case 'salary-asc':
      list.sort((a, b) => (Number(a.totalSalary) || 0) - (Number(b.totalSalary) || 0))
      break
    default:
      list.sort((a, b) => b.month.localeCompare(a.month))
  }
  return list
})

const pagedRecords = computed<SalaryRecord[]>(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredRecords.value.slice(start, start + pageSize.value)
})

/** 首页只展示最近 5 条，完整数据在弹窗里 */
const recentRecords = computed<SalaryRecord[]>(() =>
  [...records.value].sort((a, b) => b.month.localeCompare(a.month)).slice(0, 5)
)

const recordsVisible = ref(false)

const openRecords = () => {
  page.value = 1
  recordsVisible.value = true
}

const onSearch = () => {
  page.value = 1
}

const onPageChange = (value: number) => {
  page.value = value
}

const onPageSizeChange = (value: number) => {
  pageSize.value = value
  page.value = 1
}

// 添加工资 / 编辑工资弹窗
const addVisible = ref(false)
const editingId = ref<number | null>(null)
const addMonth = ref<string | null>(null)
const attendanceDays = ref<number | null>(null)
const previewData = ref<SalaryRecord | null>(null)
const previewLoading = ref(false)
const saving = ref(false)

const dialogTitle = computed(() => (editingId.value ? '编辑工资' : '添加工资'))

// 工资配置弹窗
const configVisible = ref(false)
const configLoading = ref(false)
const configSaving = ref(false)
const configForm = reactive({
  baseSalary: 0,
  postPerformance: 0,
  mealAllowance: 0,
  housingAllowance: 0,
  fullAttendanceBonus: 0,
  otherBonus: 0,
  pension: 0,
  medicalInsurance: 0,
  unemploymentInsurance: 0,
})

// ---------- 工具 ----------
const formatNum = (v: number): string =>
  (Number(v) || 0).toLocaleString('zh-CN', { maximumFractionDigits: 5 })

const formatMoney = (v: number): string =>
  '¥' + (Number(v) || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 5 })

const formatRatio = (v: number): string => (Number(v) || 0).toFixed(5)

const formatMonth = (ym: string): string => {
  const [y, m] = ym.split('-')
  return `${y}年${Number(m)}月`
}

// ---------- 记录列表 ----------
const loadRecords = async () => {
  listLoading.value = true
  try {
    const res = await listRecords()
    records.value = res.data ?? []
    if (!chartYear.value) {
      chartYear.value = records.value[0]?.month.split('-')[0] ?? String(new Date().getFullYear())
    }
  } catch {
    /* 请求层已提示 */
  } finally {
    listLoading.value = false
  }
}

const handleDelete = async (r: SalaryRecord) => {
  try {
    await ElMessageBox.confirm(`确定删除 ${formatMonth(r.month)} 的工资记录？`, '删除工资记录', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteRecord(r.id as number)
    await loadRecords()
  } catch {
    /* 请求层已提示 */
  }
}

// ---------- 添加工资 / 编辑工资 ----------
const openAdd = () => {
  editingId.value = null
  addMonth.value = null
  attendanceDays.value = null
  previewData.value = null
  addVisible.value = true
}

const openEdit = (r: SalaryRecord) => {
  editingId.value = r.id
  addMonth.value = r.month
  attendanceDays.value = Number(r.attendanceDays)
  previewData.value = null
  addVisible.value = true
  void loadPreview()
}

/** el-table 插槽的 row 是 DefaultRow，这里收窄成 SalaryRecord */
const openEditRow = (row: unknown) => openEdit(row as SalaryRecord)
const handleDeleteRow = (row: unknown) => void handleDelete(row as SalaryRecord)

const onMonthChange = (val: string | null) => {
  attendanceDays.value = null
  previewData.value = null
  if (!val) return
  void loadPreview()
}

const onAttendanceChange = () => {
  void loadPreview()
}

const loadPreview = async () => {
  if (!addMonth.value) return
  previewLoading.value = true
  try {
    const res = await preview(addMonth.value, attendanceDays.value)
    previewData.value = res.data ?? null
    // 首次（用户未手动改出勤天数）时，回填系统自动算出的出勤天数
    if (attendanceDays.value === null && previewData.value) {
      attendanceDays.value = previewData.value.attendanceDays
    }
  } catch {
    /* 请求层已提示 */
  } finally {
    previewLoading.value = false
  }
}

const handleSave = async () => {
  if (!addMonth.value) {
    ElMessage.warning('请选择月份')
    return
  }
  if (attendanceDays.value === null || attendanceDays.value < 0) {
    ElMessage.warning('请填写出勤天数')
    return
  }
  saving.value = true
  try {
    const payload = { month: addMonth.value, attendanceDays: attendanceDays.value }
    if (editingId.value) {
      await updateRecord(editingId.value, payload)
    } else {
      await createRecord(payload)
    }
    addVisible.value = false
    await loadRecords()
  } catch {
    /* 请求层已提示 */
  } finally {
    saving.value = false
  }
}

// ---------- 工资配置 ----------
const openConfig = () => {
  configVisible.value = true
  void loadConfig()
}

const loadConfig = async () => {
  configLoading.value = true
  try {
    const res = await getConfig()
    const c = res.data
    if (c) {
      configForm.baseSalary = c.baseSalary
      configForm.postPerformance = c.postPerformance
      configForm.mealAllowance = c.mealAllowance
      configForm.housingAllowance = c.housingAllowance
      configForm.fullAttendanceBonus = c.fullAttendanceBonus
      configForm.otherBonus = c.otherBonus
      configForm.pension = c.pension
      configForm.medicalInsurance = c.medicalInsurance
      configForm.unemploymentInsurance = c.unemploymentInsurance
    }
  } catch {
    /* 请求层已提示 */
  } finally {
    configLoading.value = false
  }
}

const handleSaveConfig = async () => {
  configSaving.value = true
  try {
    await updateConfig({ ...configForm })
    configVisible.value = false
  } catch {
    /* 请求层已提示 */
  } finally {
    configSaving.value = false
  }
}

// ---------- 初始化 ----------
onMounted(() => {
  if (userStore.isLoggedIn) void loadRecords()
})

watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) void loadRecords()
  }
)
</script>

<style scoped lang="scss">
.salary-page {
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

.action-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.card {
  border: 1px solid var(--ev-border-subtle, rgba(47, 124, 246, 0.08));
  border-radius: 16px;
  background: var(--ev-bg-glass);
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

.record-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 60px;
}

.record-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--ev-bg-glass);

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
  flex-wrap: wrap;
}

.record-month {
  font-size: 14px;
  font-weight: 600;
  color: var(--ev-text-primary, #12304f);
}

.record-tag {
  flex: 0 0 auto;
}

.record-meta {
  font-size: 12px;
  color: var(--ev-text-muted, #90a4bb);
  font-variant-numeric: tabular-nums;
}

.record-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.record-total {
  font-size: 15px;
  font-weight: 700;
  color: var(--ev-primary, #2f7cf6);
  font-variant-numeric: tabular-nums;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.year-picker {
  width: 120px;
}

.sort-select {
  width: 180px;
}

/* 添加工资弹窗 */
.salary-toolbar {
  margin-bottom: 14px;
}

.month-picker {
  width: 200px;
}

.salary-detail {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 120px;
}

.detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-label {
  font-size: 13px;
  color: var(--ev-text-secondary, #4c6b8a);
}

.detail-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--ev-text-primary, #12304f);
  font-variant-numeric: tabular-nums;

  &.deduct {
    color: var(--ev-danger, #f2637f);
  }
}

.total-row {
  .detail-label {
    font-weight: 700;
    color: var(--ev-text-primary, #12304f);
  }

  .detail-value {
    font-size: 16px;
    color: var(--ev-primary, #2f7cf6);
  }
}

.detail-divider {
  height: 1px;
  margin: 4px 0;
  background: var(--el-border-color-lighter);
}

.attendance-input {
  width: 160px;
}

/* 工资配置弹窗 */
.config-input {
  width: 100%;
}
</style>
