<template>
  <div class="performance-page">
    <!-- 头部 -->
    <div class="page-header">
      <div class="header-text">
        <h2>记录绩效</h2>
        <p>只有你自己能看到这些记录</p>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="action-bar">
      <el-button type="primary" @click="openReports">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>工作汇报
      </el-button>
      <el-button @click="openProjectDialog">
        <el-icon style="margin-right: 4px"><Setting /></el-icon>生产项目配置
      </el-button>
      <el-button @click="openOvertimeDialog">
        <el-icon style="margin-right: 4px"><Timer /></el-icon>加班记录
      </el-button>
      <el-button @click="openLateDialog">
        <el-icon style="margin-right: 4px"><Clock /></el-icon>迟到记录
      </el-button>
    </div>

    <!-- 曲线图 -->
    <div class="card chart-card">
      <div class="card-head">
        <div class="card-title">绩效趋势</div>
        <div class="chart-tools">
          <el-date-picker
            v-model="selectedMonth"
            type="month"
            value-format="YYYY-MM"
            placeholder="选择月份"
            clearable
            class="month-picker"
            @change="onMonthChange"
          />
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
      <div class="board">
        <div class="board-item">
          <span class="board-label">总绩效（净）</span>
          <span class="board-value">{{ formatDays(stats.totalDays) }} 人天</span>
        </div>
        <div class="board-item">
          <span class="board-label">上班天数</span>
          <span class="board-value">{{ stats.workDays }} 天</span>
        </div>
      </div>
      <PerformanceChart :points="chartPoints" :start-date="chartStart" :end-date="chartEnd" />
    </div>

    <!-- 工作汇报弹窗 -->
    <el-dialog v-model="reportsVisible" title="工作汇报" width="860px">
      <div class="records-toolbar">
        <el-button type="primary" @click="openRecordForm()">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>添加记录
        </el-button>
        <div class="filters">
          <el-select v-model="recordFilter.projectId" placeholder="全部项目" clearable class="f-project" @change="reloadRecords">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-date-picker
            v-model="recordFilter.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            class="f-range"
            @change="reloadRecords"
          />
        </div>
      </div>

      <div v-loading="listLoading" class="records-list">
        <div v-for="r in records" :key="r.id" class="record-item">
          <div class="record-main">
            <span class="record-date">{{ r.workDate }}</span>
            <el-tag size="small" class="record-tag">{{ r.projectName || '—' }}</el-tag>
            <el-tag size="small" :type="r.processType === 1 ? 'warning' : 'primary'" class="record-tag">
              {{ processTypeLabel(r.processType) }}
            </el-tag>
            <span class="record-meta">定额 {{ formatNum(r.quota) }}</span>
            <span class="record-meta">工作量 {{ formatNum(r.actualWorkload) }}</span>
          </div>
          <div class="record-days">{{ formatDays(r.performanceDays) }} 人天</div>
          <div class="record-actions">
            <el-button size="small" text @click="openRecordForm(r)">编辑</el-button>
            <el-button size="small" text type="danger" @click="handleDelete(r)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!listLoading && !records.length" description="还没有绩效记录" />
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

    <!-- 添加记录 / 编辑弹窗（嵌套） -->
    <el-dialog v-model="recordFormVisible" :title="editingId ? '编辑记录' : '添加记录'" width="480px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="日期">
          <el-date-picker v-model="form.workDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="项目名称">
          <el-select v-model="form.projectId" placeholder="选择项目" style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="工序类型">
          <el-select v-model="form.processType" style="width: 100%">
            <el-option label="作业" :value="0" />
            <el-option label="质检" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="定额效率">
          <el-input :model-value="formatNum(currentQuota)" disabled />
        </el-form-item>
        <el-form-item label="实际工作量">
          <el-input-number v-model="form.actualWorkload" :min="1" :max="9999999999" :precision="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="绩效人天">
          <el-input :model-value="previewDaysText" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recordFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveForm">{{ editingId ? '保存' : '添加' }}</el-button>
      </template>
    </el-dialog>

    <!-- 生产项目配置弹窗 -->
    <el-dialog v-model="projectDialogVisible" title="生产项目配置" width="640px">
      <div class="project-add-row">
        <el-input v-model="newProject.name" placeholder="项目名称" maxlength="100" class="p-name" />
        <el-input-number v-model="newProject.workQuota" :min="1" :precision="0" :step="1" placeholder="作业定额" class="p-quota" :controls="false" />
        <el-input-number v-model="newProject.inspectQuota" :min="1" :precision="0" :step="1" placeholder="质检定额" class="p-quota" :controls="false" />
        <el-button type="primary" :loading="addingProject" @click="handleAddProject">添加</el-button>
      </div>
      <div class="project-list">
        <div v-for="p in projects" :key="p.id" class="project-item">
          <template v-if="editingProjectId === p.id">
            <el-input v-model="editProject.name" maxlength="100" class="p-name" />
            <el-input-number v-model="editProject.workQuota" :min="1" :precision="0" :step="1" :controls="false" class="p-quota" />
            <el-input-number v-model="editProject.inspectQuota" :min="1" :precision="0" :step="1" :controls="false" class="p-quota" />
            <el-button size="small" type="primary" @click="handleSaveProject(p.id)">保存</el-button>
            <el-button size="small" @click="editingProjectId = null">取消</el-button>
          </template>
          <template v-else>
            <span class="p-name project-name">{{ p.name }}</span>
            <span class="p-quota">作业 {{ formatNum(p.workQuota) }}</span>
            <span class="p-quota">质检 {{ formatNum(p.inspectQuota) }}</span>
            <span class="project-ops">
              <el-button size="small" text @click="startEditProject(p)">编辑</el-button>
              <el-button size="small" text type="danger" @click="handleDeleteProject(p)">删除</el-button>
            </span>
          </template>
        </div>
        <el-empty v-if="!projects.length" description="还没有项目，先添加一个吧" :image-size="60" />
      </div>
    </el-dialog>

    <!-- 加班记录弹窗 -->
    <el-dialog v-model="overtimeVisible" title="加班记录" width="720px">
      <div class="records-toolbar">
        <el-button type="primary" @click="openOvertimeForm()">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>添加记录
        </el-button>
        <div class="filters">
          <el-button type="danger" plain :disabled="!overtimeSelection.length" @click="handleBatchDeleteOvertime">
            批量删除<span v-if="overtimeSelection.length"> ({{ overtimeSelection.length }})</span>
          </el-button>
          <el-date-picker
            v-model="overtimeFilter.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            class="f-range"
            @change="reloadOvertime"
          />
        </div>
      </div>

      <div v-loading="overtimeLoading" class="records-list">
        <el-checkbox-group v-model="overtimeSelection">
          <div v-for="r in overtimeList" :key="r.id" class="record-item">
            <el-checkbox :value="r.id" class="record-checkbox" />
            <div class="record-main">
              <span class="record-date">{{ r.workDate }}</span>
              <span class="record-meta">加班 {{ formatNum(r.overtimeHours) }} 小时</span>
              <span class="record-meta">折算 {{ formatDays(r.overtimeDays) }} 天</span>
            </div>
            <div class="record-actions">
              <el-button size="small" text @click="openOvertimeForm(r)">编辑</el-button>
              <el-button size="small" text type="danger" @click="handleDeleteOvertime(r)">删除</el-button>
            </div>
          </div>
        </el-checkbox-group>
        <el-empty v-if="!overtimeLoading && !overtimeList.length" description="还没有加班记录" />
      </div>

      <div v-if="overtimeTotal > PAGE_SIZE" class="pagination-wrap">
        <el-pagination
          v-model:current-page="overtimePage"
          :page-size="PAGE_SIZE"
          :total="overtimeTotal"
          layout="prev, pager, next, total"
          background
          @current-change="loadOvertimePage"
        />
      </div>
    </el-dialog>

    <!-- 加班添加 / 编辑弹窗 -->
    <el-dialog v-model="overtimeFormVisible" :title="overtimeEditingId ? '编辑加班' : '添加加班'" width="460px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="日期">
          <el-date-picker v-model="overtimeForm.workDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="加班时长">
          <div class="hours-stepper">
            <el-button :disabled="overtimeForm.overtimeHours <= 0" @click="stepHours(-0.5)">−</el-button>
            <el-input :model-value="overtimeForm.overtimeHours" class="hours-value" readonly />
            <el-button :disabled="overtimeForm.overtimeHours >= 24" @click="stepHours(0.5)">+</el-button>
            <span class="hours-unit">小时</span>
          </div>
        </el-form-item>
        <el-form-item label="折算天数">
          <el-input :model-value="formatDays(overtimeForm.overtimeHours / 8)" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="overtimeFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="overtimeSaving" @click="saveOvertimeForm">{{ overtimeEditingId ? '保存' : '添加' }}</el-button>
      </template>
    </el-dialog>

    <!-- 迟到记录弹窗 -->
    <el-dialog v-model="lateVisible" title="迟到记录" width="720px">
      <div class="records-toolbar">
        <el-button type="primary" @click="openLateForm()">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>添加记录
        </el-button>
        <div class="filters">
          <el-button type="danger" plain :disabled="!lateSelection.length" @click="handleBatchDeleteLate">
            批量删除<span v-if="lateSelection.length"> ({{ lateSelection.length }})</span>
          </el-button>
          <el-date-picker
            v-model="lateFilter.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            class="f-range"
            @change="reloadLate"
          />
        </div>
      </div>

      <div v-loading="lateLoading" class="records-list">
        <el-checkbox-group v-model="lateSelection">
          <div v-for="r in lateList" :key="r.id" class="record-item">
            <el-checkbox :value="r.id" class="record-checkbox" />
            <div class="record-main">
              <span class="record-date">{{ r.workDate }}</span>
              <span class="record-meta">迟到 {{ r.lateMinutes }} 分钟</span>
              <span class="record-meta">折算 {{ formatDays(r.lateDays) }} 天</span>
            </div>
            <div class="record-actions">
              <el-button size="small" text type="danger" @click="handleDeleteLate(r)">删除</el-button>
            </div>
          </div>
        </el-checkbox-group>
        <el-empty v-if="!lateLoading && !lateList.length" description="还没有迟到记录" />
      </div>

      <div v-if="lateTotal > PAGE_SIZE" class="pagination-wrap">
        <el-pagination
          v-model:current-page="latePage"
          :page-size="PAGE_SIZE"
          :total="lateTotal"
          layout="prev, pager, next, total"
          background
          @current-change="loadLatePage"
        />
      </div>
    </el-dialog>

    <!-- 迟到添加弹窗 -->
    <el-dialog v-model="lateFormVisible" title="添加迟到" width="460px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="日期">
          <el-date-picker v-model="lateForm.workDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="迟到分钟">
          <el-input-number v-model="lateForm.lateMinutes" :min="1" :max="480" :precision="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="折算天数">
          <el-input :model-value="formatDays(lateForm.lateMinutes / 480)" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lateFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="lateSaving" @click="saveLateForm">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PerformanceChart from '@/components/PerformanceChart.vue'
import {
  batchDeleteLate,
  batchDeleteOvertime,
  createLate,
  createOvertime,
  createProject,
  createRecord,
  deleteLate,
  deleteOvertime,
  deleteProject,
  deleteRecord,
  getChart,
  listLate,
  listOvertime,
  listProjects,
  listRecords,
  updateOvertime,
  updateProject,
  updateRecord,
} from '@/api/performance'
import type {
  LateRecord,
  OvertimeRecord,
  PerformanceChartPoint,
  PerformanceProject,
  PerformanceRecord,
  ProcessType,
} from '@/types/performance'
import { currentPeriodLabel, periodRange } from '@/utils/performance'

const PAGE_SIZE = 10

// ---------- 数据 ----------
const projects = ref<PerformanceProject[]>([])
const chartPoints = ref<PerformanceChartPoint[]>([])
const chartStart = ref('')
const chartEnd = ref('')
const stats = reactive({ totalDays: 0, workDays: 0 })

// 图表范围
const selectedMonth = ref<string | null>(null)
const dateRange = ref<[string, string] | null>(null)

// 工作汇报弹窗
const reportsVisible = ref(false)
const records = ref<PerformanceRecord[]>([])
const recordsTotal = ref(0)
const recordsPage = ref(1)
const listLoading = ref(false)
const recordFilter = reactive({
  projectId: null as number | null,
  dateRange: null as [string, string] | null,
})

// 添加记录 / 编辑
const recordFormVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({
  workDate: '',
  projectId: null as number | null,
  processType: 0 as ProcessType,
  actualWorkload: 0,
})

// 生产项目配置
const projectDialogVisible = ref(false)
const newProject = reactive({ name: '', workQuota: 1, inspectQuota: 1 })
const addingProject = ref(false)
const editingProjectId = ref<number | null>(null)
const editProject = reactive({ name: '', workQuota: 0, inspectQuota: 0 })

// 加班记录
const overtimeVisible = ref(false)
const overtimeList = ref<OvertimeRecord[]>([])
const overtimeTotal = ref(0)
const overtimePage = ref(1)
const overtimeLoading = ref(false)
const overtimeSelection = ref<number[]>([])
const overtimeFilter = reactive({ dateRange: null as [string, string] | null })
const overtimeFormVisible = ref(false)
const overtimeEditingId = ref<number | null>(null)
const overtimeSaving = ref(false)
const overtimeForm = reactive({ workDate: '', overtimeHours: 0 })

// 迟到记录
const lateVisible = ref(false)
const lateList = ref<LateRecord[]>([])
const lateTotal = ref(0)
const latePage = ref(1)
const lateLoading = ref(false)
const lateSelection = ref<number[]>([])
const lateFilter = reactive({ dateRange: null as [string, string] | null })
const lateFormVisible = ref(false)
const lateSaving = ref(false)
const lateForm = reactive({ workDate: '', lateMinutes: 1 })

// ---------- 工具 ----------
function formatDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 数值格式化：最多2位小数 */
const formatNum = (v: number): string =>
  (Number(v) || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })

/** 人天格式化：最多5位小数 */
const formatDays = (v: number): string =>
  (Number(v) || 0).toLocaleString('zh-CN', { maximumFractionDigits: 5 })

const processTypeLabel = (t: number): string => (t === 1 ? '质检' : '作业')

// ---------- 图表 ----------
const loadChart = async (start: string, end: string) => {
  try {
    const res = await getChart(start, end)
    chartPoints.value = res.data?.points ?? []
    stats.totalDays = res.data?.totalDays ?? 0
    stats.workDays = res.data?.workDays ?? 0
  } catch {
    /* 请求层已提示 */
  }
}

const applyPeriodLabel = (label: string) => {
  const [start, end] = periodRange(label)
  chartStart.value = start
  chartEnd.value = end
  void loadChart(start, end)
}

const onMonthChange = (val: string | null) => {
  if (!val) {
    // 清空时回到当前周期
    selectedMonth.value = null
    dateRange.value = null
    applyPeriodLabel(currentPeriodLabel())
    return
  }
  dateRange.value = null
  applyPeriodLabel(val)
}

const onRangeChange = (val: [string, string] | null) => {
  if (!val || !val[0] || !val[1]) {
    selectedMonth.value = null
    dateRange.value = null
    applyPeriodLabel(currentPeriodLabel())
    return
  }
  selectedMonth.value = null
  chartStart.value = val[0]
  chartEnd.value = val[1]
  void loadChart(val[0], val[1])
}

// ---------- 项目配置 ----------
const loadProjects = async () => {
  try {
    const res = await listProjects()
    projects.value = res.data ?? []
  } catch {
    /* 请求层已提示 */
  }
}

const openProjectDialog = () => {
  editingProjectId.value = null
  newProject.name = ''
  newProject.workQuota = 1
  newProject.inspectQuota = 1
  projectDialogVisible.value = true
}

const handleAddProject = async () => {
  const name = newProject.name.trim()
  if (!name) {
    ElMessage.warning('请输入项目名称')
    return
  }
  if (!newProject.workQuota || newProject.workQuota <= 0) {
    ElMessage.warning('请填写作业定额')
    return
  }
  if (!newProject.inspectQuota || newProject.inspectQuota <= 0) {
    ElMessage.warning('请填写质检定额')
    return
  }
  addingProject.value = true
  try {
    await createProject({
      name,
      workQuota: newProject.workQuota,
      inspectQuota: newProject.inspectQuota,
    })
    newProject.name = ''
    await loadProjects()
  } catch {
    /* 请求层已提示 */
  } finally {
    addingProject.value = false
  }
}

const startEditProject = (p: PerformanceProject) => {
  editingProjectId.value = p.id
  editProject.name = p.name
  editProject.workQuota = p.workQuota
  editProject.inspectQuota = p.inspectQuota
}

const handleSaveProject = async (id: number) => {
  const name = editProject.name.trim()
  if (!name) {
    ElMessage.warning('项目名称不能为空')
    return
  }
  if (!editProject.workQuota || editProject.workQuota <= 0) {
    ElMessage.warning('请填写作业定额')
    return
  }
  if (!editProject.inspectQuota || editProject.inspectQuota <= 0) {
    ElMessage.warning('请填写质检定额')
    return
  }
  try {
    await updateProject(id, {
      name,
      workQuota: editProject.workQuota,
      inspectQuota: editProject.inspectQuota,
    })
    editingProjectId.value = null
    await loadProjects()
  } catch {
    /* 请求层已提示 */
  }
}

const handleDeleteProject = async (p: PerformanceProject) => {
  try {
    await ElMessageBox.confirm(`确定删除项目「${p.name}」？`, '删除项目', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteProject(p.id)
    if (editingProjectId.value === p.id) editingProjectId.value = null
    await loadProjects()
  } catch {
    /* 请求层已提示 */
  }
}

// ---------- 工作汇报弹窗 ----------
const openReports = () => {
  recordFilter.projectId = null
  recordFilter.dateRange = null
  recordsPage.value = 1
  reportsVisible.value = true
  void loadRecordsPage(1)
}

const loadRecordsPage = async (page: number) => {
  listLoading.value = true
  try {
    const res = await listRecords({
      page,
      size: PAGE_SIZE,
      projectId: recordFilter.projectId,
      startDate: recordFilter.dateRange?.[0] ?? null,
      endDate: recordFilter.dateRange?.[1] ?? null,
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

// ---------- 添加记录 / 编辑 ----------
/** 当前所选项目 + 工序类型对应的定额效率（锁定展示） */
const currentQuota = computed<number>(() => {
  if (!form.projectId) return 0
  const p = projects.value.find((x) => x.id === form.projectId)
  if (!p) return 0
  return form.processType === 1 ? p.inspectQuota : p.workQuota
})

/** 绩效人天预览 = 实际工作量 / 定额效率 */
const previewDays = computed<number | null>(() => {
  if (!currentQuota.value || currentQuota.value <= 0) return null
  if (!form.actualWorkload || form.actualWorkload <= 0) return null
  return form.actualWorkload / currentQuota.value
})

const previewDaysText = computed(() => (previewDays.value == null ? '' : formatDays(previewDays.value)))

const openRecordForm = (record?: PerformanceRecord) => {
  if (record) {
    editingId.value = record.id
    form.workDate = record.workDate
    form.projectId = record.projectId
    form.processType = record.processType
    form.actualWorkload = record.actualWorkload
  } else {
    editingId.value = null
    form.workDate = formatDate(new Date())
    form.projectId = null
    form.processType = 0
    form.actualWorkload = 0
  }
  recordFormVisible.value = true
}

const saveForm = async () => {
  if (!form.projectId) {
    ElMessage.warning('请选择项目')
    return
  }
  if (!form.workDate) {
    ElMessage.warning('请选择日期')
    return
  }
  if (!form.actualWorkload || form.actualWorkload <= 0) {
    ElMessage.warning('请填写实际工作量')
    return
  }
  saving.value = true
  const payload = {
    projectId: form.projectId,
    workDate: form.workDate,
    processType: form.processType,
    actualWorkload: form.actualWorkload,
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

const handleDelete = async (r: PerformanceRecord) => {
  try {
    await ElMessageBox.confirm(`确定删除这条绩效记录？`, '删除记录', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    const shouldGoBack = reportsVisible.value && records.value.length === 1 && recordsPage.value > 1
    await deleteRecord(r.id)
    if (shouldGoBack) recordsPage.value -= 1
    await refreshAfterMutation()
  } catch {
    /* 请求层已提示 */
  }
}

// ---------- 加班记录 ----------
const openOvertimeDialog = () => {
  overtimeFilter.dateRange = null
  overtimeSelection.value = []
  overtimePage.value = 1
  overtimeVisible.value = true
  void loadOvertimePage(1)
}

const loadOvertimePage = async (page: number) => {
  overtimeLoading.value = true
  try {
    const res = await listOvertime({
      page,
      size: PAGE_SIZE,
      startDate: overtimeFilter.dateRange?.[0] ?? null,
      endDate: overtimeFilter.dateRange?.[1] ?? null,
    })
    overtimeList.value = res.data?.records ?? []
    overtimeTotal.value = res.data?.total ?? 0
    overtimePage.value = page
  } catch {
    /* 请求层已提示 */
  } finally {
    overtimeLoading.value = false
  }
}

const reloadOvertime = () => {
  overtimeSelection.value = []
  overtimePage.value = 1
  void loadOvertimePage(1)
}

const openOvertimeForm = (record?: OvertimeRecord) => {
  if (record) {
    overtimeEditingId.value = record.id
    overtimeForm.workDate = record.workDate
    overtimeForm.overtimeHours = record.overtimeHours
  } else {
    overtimeEditingId.value = null
    overtimeForm.workDate = formatDate(new Date())
    overtimeForm.overtimeHours = 0
  }
  overtimeFormVisible.value = true
}

const stepHours = (delta: number) => {
  const next = Math.round((overtimeForm.overtimeHours + delta) * 10) / 10
  if (next < 0 || next > 24) return
  overtimeForm.overtimeHours = next
}

const saveOvertimeForm = async () => {
  if (!overtimeForm.workDate) {
    ElMessage.warning('请选择日期')
    return
  }
  if (!overtimeForm.overtimeHours || overtimeForm.overtimeHours <= 0) {
    ElMessage.warning('请填写加班时长')
    return
  }
  overtimeSaving.value = true
  const payload = { workDate: overtimeForm.workDate, overtimeHours: overtimeForm.overtimeHours }
  try {
    if (overtimeEditingId.value) {
      await updateOvertime(overtimeEditingId.value, payload)
    } else {
      await createOvertime(payload)
    }
    overtimeFormVisible.value = false
    await refreshAfterMutation()
    if (overtimeVisible.value) await loadOvertimePage(overtimePage.value)
  } catch {
    /* 请求层已提示 */
  } finally {
    overtimeSaving.value = false
  }
}

const handleDeleteOvertime = async (r: OvertimeRecord) => {
  try {
    await ElMessageBox.confirm('确定删除这条加班记录？', '删除加班', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    const shouldGoBack = overtimeVisible.value && overtimeList.value.length === 1 && overtimePage.value > 1
    await deleteOvertime(r.id)
    if (shouldGoBack) overtimePage.value -= 1
    await refreshAfterMutation()
    if (overtimeVisible.value) await loadOvertimePage(overtimePage.value)
  } catch {
    /* 请求层已提示 */
  }
}

const handleBatchDeleteOvertime = async () => {
  if (!overtimeSelection.value.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${overtimeSelection.value.length} 条加班记录？`, '批量删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await batchDeleteOvertime(overtimeSelection.value)
    overtimeSelection.value = []
    await refreshAfterMutation()
    if (overtimeVisible.value) await loadOvertimePage(overtimePage.value)
  } catch {
    /* 请求层已提示 */
  }
}

// ---------- 迟到记录 ----------
const openLateDialog = () => {
  lateFilter.dateRange = null
  lateSelection.value = []
  latePage.value = 1
  lateVisible.value = true
  void loadLatePage(1)
}

const loadLatePage = async (page: number) => {
  lateLoading.value = true
  try {
    const res = await listLate({
      page,
      size: PAGE_SIZE,
      startDate: lateFilter.dateRange?.[0] ?? null,
      endDate: lateFilter.dateRange?.[1] ?? null,
    })
    lateList.value = res.data?.records ?? []
    lateTotal.value = res.data?.total ?? 0
    latePage.value = page
  } catch {
    /* 请求层已提示 */
  } finally {
    lateLoading.value = false
  }
}

const reloadLate = () => {
  lateSelection.value = []
  latePage.value = 1
  void loadLatePage(1)
}

const openLateForm = () => {
  lateForm.workDate = formatDate(new Date())
  lateForm.lateMinutes = 1
  lateFormVisible.value = true
}

const saveLateForm = async () => {
  if (!lateForm.workDate) {
    ElMessage.warning('请选择日期')
    return
  }
  if (!lateForm.lateMinutes || lateForm.lateMinutes <= 0) {
    ElMessage.warning('请填写迟到分钟')
    return
  }
  lateSaving.value = true
  try {
    await createLate({ workDate: lateForm.workDate, lateMinutes: lateForm.lateMinutes })
    lateFormVisible.value = false
    await refreshAfterMutation()
    if (lateVisible.value) await loadLatePage(latePage.value)
  } catch {
    /* 请求层已提示 */
  } finally {
    lateSaving.value = false
  }
}

const handleDeleteLate = async (r: LateRecord) => {
  try {
    await ElMessageBox.confirm('确定删除这条迟到记录？', '删除迟到', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    const shouldGoBack = lateVisible.value && lateList.value.length === 1 && latePage.value > 1
    await deleteLate(r.id)
    if (shouldGoBack) latePage.value -= 1
    await refreshAfterMutation()
    if (lateVisible.value) await loadLatePage(latePage.value)
  } catch {
    /* 请求层已提示 */
  }
}

const handleBatchDeleteLate = async () => {
  if (!lateSelection.value.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${lateSelection.value.length} 条迟到记录？`, '批量删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await batchDeleteLate(lateSelection.value)
    lateSelection.value = []
    await refreshAfterMutation()
    if (lateVisible.value) await loadLatePage(latePage.value)
  } catch {
    /* 请求层已提示 */
  }
}

// ---------- 汇总刷新 ----------
const refreshAfterMutation = async () => {
  await Promise.all([
    loadChart(chartStart.value, chartEnd.value),
    reportsVisible.value ? loadRecordsPage(recordsPage.value) : Promise.resolve(),
  ])
}

// ---------- 初始化 ----------
onMounted(async () => {
  await loadProjects()
  selectedMonth.value = currentPeriodLabel()
  applyPeriodLabel(selectedMonth.value)
})
</script>

<style scoped lang="scss">
.performance-page {
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

.board {
  display: flex;
  gap: 32px;
  margin-bottom: 12px;
  padding: 14px 16px;
  border: 1px solid var(--ev-border-subtle, rgba(47, 124, 246, 0.08));
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.5);
}

.board-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.board-label {
  font-size: 12px;
  color: var(--ev-text-muted, #90a4bb);
}

.board-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--ev-primary, #2f7cf6);
  font-variant-numeric: tabular-nums;
}

.chart-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.month-picker {
  width: 140px;
}

.range-picker {
  width: 260px;
}

/* 工作汇报弹窗 */
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

.f-project {
  width: 160px;
}

.f-range {
  width: 240px;
}

.records-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 200px;
  max-height: 420px;
  overflow-y: auto;
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

.record-date {
  font-size: 13px;
  color: var(--ev-text-secondary, #4c6b8a);
  font-variant-numeric: tabular-nums;
}

.record-tag {
  flex: 0 0 auto;
}

.record-meta {
  font-size: 12px;
  color: var(--ev-text-muted, #90a4bb);
}

.record-days {
  flex: 0 0 auto;
  font-size: 15px;
  font-weight: 700;
  color: var(--ev-primary, #2f7cf6);
  font-variant-numeric: tabular-nums;
}

.record-actions {
  flex: 0 0 auto;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 14px;
}

/* 生产项目配置弹窗 */
.project-add-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.p-name {
  width: 180px;
}

.p-quota {
  width: 140px;
}

.project-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 320px;
  overflow-y: auto;
}

.project-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.6);
}

.project-name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: var(--ev-text-primary, #12304f);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-ops {
  flex: 0 0 auto;
}

/* 加班/迟到 */
.hours-stepper {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.hours-value {
  width: 120px;
}

.hours-unit {
  color: var(--ev-text-muted, #90a4bb);
  font-size: 13px;
}

.record-checkbox {
  flex: 0 0 auto;
}
</style>
