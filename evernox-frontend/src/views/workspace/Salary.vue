<template>
  <div class="salary-page">
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

    <!-- 工资记录列表 -->
    <div class="card record-card">
      <div class="card-head">
        <div class="card-title">工资记录</div>
        <el-select v-model="sortMode" class="sort-select">
          <el-option label="月份（新 → 旧）" value="month-desc" />
          <el-option label="月份（旧 → 新）" value="month-asc" />
          <el-option label="工资（高 → 低）" value="salary-desc" />
          <el-option label="工资（低 → 高）" value="salary-asc" />
        </el-select>
      </div>
      <div v-loading="listLoading" class="record-list">
        <div v-for="r in sortedRecords" :key="r.id ?? r.month" class="record-item">
          <div class="record-main">
            <span class="record-month">{{ formatMonth(r.month) }}</span>
            <el-tag size="small" class="record-tag">{{ r.startDate }} ~ {{ r.endDate }}</el-tag>
            <span class="record-meta">应出勤 {{ formatNum(r.attendanceDays) }} 天</span>
            <span class="record-meta">净绩效 {{ formatNum(r.performanceDays) }}</span>
            <span class="record-meta">绩效薪资 {{ formatMoney(r.performanceSalary) }}</span>
            <span class="record-meta">加班工资 {{ formatMoney(r.overtimeSalary) }}</span>
          </div>
          <div class="record-total">{{ formatMoney(r.totalSalary) }}</div>
          <div class="record-actions">
            <el-button size="small" text type="danger" @click="handleDelete(r)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!listLoading && !sortedRecords.length" description="还没有工资记录" />
      </div>
    </div>

    <!-- 添加工资弹窗 -->
    <el-dialog v-model="addVisible" title="添加工资" width="680px">
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
    <el-dialog v-model="configVisible" title="工资配置" width="520px">
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
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SalaryChart from '@/components/SalaryChart.vue'
import { createRecord, deleteRecord, getConfig, listRecords, preview, updateConfig } from '@/api/salary'
import type { SalaryRecord } from '@/types/salary'

// ---------- 数据 ----------
const records = ref<SalaryRecord[]>([])
const listLoading = ref(false)

// 曲线图与排序
type SortMode = 'month-desc' | 'month-asc' | 'salary-desc' | 'salary-asc'
const chartYear = ref('')
const sortMode = ref<SortMode>('month-desc')

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

const sortedRecords = computed<SalaryRecord[]>(() => {
  const list = [...records.value]
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

// 添加工资弹窗
const addVisible = ref(false)
const addMonth = ref<string | null>(null)
const attendanceDays = ref<number | null>(null)
const previewData = ref<SalaryRecord | null>(null)
const previewLoading = ref(false)
const saving = ref(false)

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

// ---------- 添加工资 ----------
const openAdd = () => {
  addMonth.value = null
  attendanceDays.value = null
  previewData.value = null
  addVisible.value = true
}

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
    await createRecord({ month: addMonth.value, attendanceDays: attendanceDays.value })
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
  void loadRecords()
})
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

.record-total {
  font-size: 15px;
  font-weight: 700;
  color: var(--ev-primary, #2f7cf6);
  font-variant-numeric: tabular-nums;
}

.record-actions {
  flex: 0 0 auto;
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
