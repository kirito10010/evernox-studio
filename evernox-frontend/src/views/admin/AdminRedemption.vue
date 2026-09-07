<template>
  <div class="admin-redemption">
    <div class="toolbar">
      <span class="toolbar-label">时长：</span>
      <el-radio-group v-model="days">
        <el-radio-button :value="7">一周（7天）</el-radio-button>
        <el-radio-button :value="30">一个月（30天）</el-radio-button>
      </el-radio-group>
      <span class="toolbar-label">数量：</span>
      <el-input-number v-model="count" :min="1" :max="100" :precision="0" />
      <el-button type="primary" :loading="generating" @click="doGenerate">生成卡密</el-button>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="filters.keyword"
        placeholder="搜索卡密"
        clearable
        :prefix-icon="Search"
        class="filter-keyword"
        @input="onKeywordInput"
        @clear="applyFilters"
      />
      <el-select v-model="filters.days" placeholder="全部时长" clearable class="filter-select" @change="applyFilters">
        <el-option label="7 天" :value="7" />
        <el-option label="30 天" :value="30" />
      </el-select>
      <el-select v-model="filters.status" placeholder="全部状态" clearable class="filter-select" @change="applyFilters">
        <el-option label="未使用" :value="0" />
        <el-option label="已使用" :value="1" />
      </el-select>
      <el-input
        v-model="filters.username"
        placeholder="使用账户"
        clearable
        :prefix-icon="Search"
        class="filter-username"
        @input="onUsernameInput"
        @clear="applyFilters"
      />
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="~"
        start-placeholder="生成起"
        end-placeholder="止"
        class="filter-date"
        @change="applyFilters"
      />
      <el-select v-model="filters.sortField" class="filter-select" @change="applyFilters">
        <el-option label="生成时间" value="createdAt" />
        <el-option label="使用时间" value="usedAt" />
        <el-option label="时长" value="days" />
      </el-select>
      <el-select v-model="filters.sortOrder" class="filter-order" @change="applyFilters">
        <el-option label="降序" value="desc" />
        <el-option label="升序" value="asc" />
      </el-select>
      <el-button class="filter-reset" @click="resetFilters">重置</el-button>
    </div>

    <div class="table-wrap">
      <el-table
        :data="codes"
        v-loading="loading"
        border
        stripe
        row-key="id"
        @selection-change="onSelectionChange"
        empty-text="没有符合条件的卡密"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column prop="code" label="卡密" min-width="200">
          <template #default="{ row }">
            <span class="code">{{ row.code }}</span>
            <el-button link type="primary" size="small" @click="copyCode(row as RedemptionCode)">复制</el-button>
          </template>
        </el-table-column>
        <el-table-column label="时长" width="120">
          <template #default="{ row }">{{ row.days }} 天</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '已使用' : '未使用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="使用账户" min-width="120">
          <template #default="{ row }">{{ row.username || '-' }}</template>
        </el-table-column>
        <el-table-column label="使用时间" min-width="160">
          <template #default="{ row }">{{ fmtTime(row.usedAt) }}</template>
        </el-table-column>
        <el-table-column label="生成时间" min-width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="handleDelete(row as RedemptionCode)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="table-footer">
      <div class="footer-left">
        <span v-if="selectedIds.length" class="selected-hint">已选 {{ selectedIds.length }} 项</span>
        <el-button v-if="selectedIds.length" type="danger" size="small" @click="handleBatchDelete">
          批量删除
        </el-button>
      </div>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadCodes"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="generateVisible" title="生成的卡密" width="480px">
      <div class="generated-list">
        <div v-for="c in generatedCodes" :key="c.id" class="generated-item">
          <span class="code">{{ c.code }}</span>
          <span class="meta">{{ c.days }} 天</span>
          <el-button link type="primary" size="small" @click="copyText(c.code)">复制</el-button>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="copyAll">复制全部</el-button>
        <el-button @click="generateVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import {
  generateRedemptionCodes,
  getRedemptionCodes,
  deleteRedemptionCode,
  deleteRedemptionCodes,
} from '@/api/admin'
import type { RedemptionCode, RedemptionCodeListParams } from '@/types/user'

const days = ref(7)
const count = ref(1)
const generating = ref(false)
const codes = ref<RedemptionCode[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const generatedCodes = ref<RedemptionCode[]>([])
const generateVisible = ref(false)
const selectedIds = ref<number[]>([])

const dateRange = ref<[string, string] | null>(null)

const filters = reactive({
  keyword: '',
  days: null as number | null,
  status: null as number | null,
  username: '',
  sortField: 'createdAt' as NonNullable<RedemptionCodeListParams['sortField']>,
  sortOrder: 'desc' as NonNullable<RedemptionCodeListParams['sortOrder']>,
})

const fmtTime = (v?: string | null): string => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-')

const loadCodes = async () => {
  loading.value = true
  try {
    const res = await getRedemptionCodes({
      page: currentPage.value,
      size: pageSize.value,
      keyword: filters.keyword.trim() || undefined,
      days: filters.days,
      status: filters.status,
      username: filters.username.trim() || undefined,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      sortField: filters.sortField,
      sortOrder: filters.sortOrder,
    })
    codes.value = res.data?.records || []
    total.value = res.data?.total || 0

    // 删完最后一页时自动回退，避免停在空页
    if (codes.value.length === 0 && currentPage.value > 1) {
      currentPage.value -= 1
      await loadCodes()
    }
  } catch { /* 请求层已提示 */ }
  finally { loading.value = false }
}

const applyFilters = () => {
  currentPage.value = 1
  loadCodes()
}

const onSizeChange = () => {
  currentPage.value = 1
  loadCodes()
}

// 关键字 / 账户输入防抖：避免每敲一个字符打一次接口
let debounceTimer: ReturnType<typeof setTimeout> | null = null
const debounce = (fn: () => void) => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(fn, 300)
}
const onKeywordInput = () => debounce(applyFilters)
const onUsernameInput = () => debounce(applyFilters)

const resetFilters = () => {
  filters.keyword = ''
  filters.days = null
  filters.status = null
  filters.username = ''
  filters.sortField = 'createdAt'
  filters.sortOrder = 'desc'
  dateRange.value = null
  applyFilters()
}

const onSelectionChange = (rows: RedemptionCode[]) => {
  selectedIds.value = rows.map((row) => row.id)
}

const doGenerate = async () => {
  generating.value = true
  try {
    const res = await generateRedemptionCodes({ days: days.value, count: count.value })
    generatedCodes.value = res.data ?? []
    generateVisible.value = true
    await loadCodes()
  } finally {
    generating.value = false
  }
}

const copyText = async (text: string) => {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      // 服务器走 http（非安全上下文）时 Clipboard API 不可用，降级到 execCommand
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.setAttribute('readonly', '')
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

const copyCode = (row: RedemptionCode) => copyText(row.code)

const copyAll = () => {
  const text = generatedCodes.value.map((c) => c.code).join('\n')
  copyText(text)
}

const handleDelete = async (row: RedemptionCode) => {
  try {
    await ElMessageBox.confirm(`确定删除卡密「${row.code}」吗？此操作不可恢复。`, '删除卡密', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteRedemptionCode(row.id)
    ElMessage.success('卡密已删除')
    await loadCodes()
  } catch { /* 请求层已提示 */ }
}

const handleBatchDelete = async () => {
  const n = selectedIds.value.length
  if (!n) return
  try {
    await ElMessageBox.confirm(`即将删除 ${n} 张卡密，此操作不可恢复。`, '批量删除卡密', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteRedemptionCodes(selectedIds.value)
    ElMessage.success(`已删除 ${n} 张卡密`)
    selectedIds.value = []
    await loadCodes()
  } catch { /* 请求层已提示 */ }
}

onMounted(loadCodes)
onUnmounted(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<style scoped lang="scss">
.admin-redemption {
  display: flex;
  flex-direction: column;
  gap: 14px;

  .toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;

    .toolbar-label {
      font-size: 13px;
      color: var(--ev-text-secondary);
    }
  }

  .filter-bar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px;
    padding: 16px 20px;
    background: var(--ev-bg-glass);
    border: 1px solid var(--ev-border-subtle);
    border-top-color: var(--ev-border-gloss);
    border-radius: 16px;
    backdrop-filter: var(--ev-blur-md);
    -webkit-backdrop-filter: var(--ev-blur-md);
    box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);

    .filter-keyword { flex: 3 1 180px; min-width: 0; }
    .filter-username { flex: 2 1 150px; min-width: 0; }
    .filter-select { flex: 1 1 120px; min-width: 0; max-width: 150px; }
    .filter-order { flex: 1 1 88px; min-width: 0; max-width: 110px; }
    :deep(.filter-date) {
      flex: none !important;
      width: 250px !important;
      max-width: 250px !important;
    }
    .filter-reset { flex: 0 0 auto; }
  }

  .table-wrap {
    padding: 8px 12px;
    overflow: hidden;
    background: var(--ev-bg-glass);
    border: 1px solid var(--ev-border-subtle);
    border-top-color: var(--ev-border-gloss);
    border-radius: 16px;
    backdrop-filter: var(--ev-blur-md);
    -webkit-backdrop-filter: var(--ev-blur-md);
    box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);

    :deep(.el-table) {
      background: transparent;
    }

    :deep(.el-table tr),
    :deep(.el-table th.el-table__cell) {
      background: transparent;
    }
  }

  .table-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
    padding: 12px 20px;
    background: var(--ev-bg-glass);
    border: 1px solid var(--ev-border-subtle);
    border-top-color: var(--ev-border-gloss);
    border-radius: 16px;
    backdrop-filter: var(--ev-blur-md);
    -webkit-backdrop-filter: var(--ev-blur-md);
    box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
  }

  .footer-left {
    display: flex;
    align-items: center;
    gap: 10px;

    .selected-hint {
      font-size: 13px;
      color: var(--ev-text-secondary);
    }
  }

  .code {
    font-family: monospace;
    letter-spacing: 1px;
    color: var(--ev-text-primary);
    margin-right: 6px;
  }

  .generated-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
    max-height: 400px;
    overflow-y: auto;

    .generated-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px 10px;
      background: rgba(47, 124, 246, 0.05);
      border-radius: 8px;

      .code {
        flex: 1;
      }

      .meta {
        font-size: 12px;
        color: var(--ev-text-muted);
      }
    }
  }
}
</style>
