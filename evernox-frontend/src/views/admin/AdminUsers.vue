<template>
  <div class="admin-users">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-aurora"></div>
      <div class="header-content">
        <div class="header-left">
          <h1><el-icon><UserFilled /></el-icon> 用户管理</h1>
          <p>管理平台内除管理员外的所有账号</p>
        </div>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          新建用户
        </el-button>
      </div>
    </div>

    <!-- Stats -->
    <div class="stats-bar">
      <div class="stat-item" v-for="item in statItems" :key="item.label">
        <span class="stat-value">{{ item.value }}</span>
        <span class="stat-label">{{ item.label }}</span>
      </div>
    </div>

    <!-- Filters -->
    <div class="filter-bar">
      <el-input
        v-model="filters.keyword"
        placeholder="搜索用户名或邮箱"
        clearable
        :prefix-icon="Search"
        class="filter-keyword"
        @input="onKeywordInput"
        @clear="applyFilters"
      />
      <el-select v-model="filters.role" placeholder="全部角色" clearable class="filter-select" @change="applyFilters">
        <el-option label="普通成员" value="member" />
        <el-option label="超级会员" value="super_member" />
      </el-select>
      <el-select v-model="filters.status" placeholder="全部状态" clearable class="filter-select" @change="applyFilters">
        <el-option label="已启用" :value="1" />
        <el-option label="已禁用" :value="0" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="~"
        start-placeholder="注册起"
        end-placeholder="止"
        class="filter-date"
        @change="applyFilters"
      />
      <el-select v-model="filters.sortField" class="filter-select" @change="applyFilters">
        <el-option label="注册时间" value="createdAt" />
        <el-option label="最后登录" value="lastLoginAt" />
        <el-option label="积分" value="points" />
        <el-option label="用户名" value="username" />
      </el-select>
      <el-select v-model="filters.sortOrder" class="filter-order" @change="applyFilters">
        <el-option label="降序" value="desc" />
        <el-option label="升序" value="asc" />
      </el-select>
      <el-button class="filter-reset" @click="resetFilters">重置</el-button>
    </div>

    <!-- Table -->
    <div class="table-wrap">
      <el-table
        :data="users"
        v-loading="loading"
        row-key="id"
        @selection-change="onSelectionChange"
        empty-text="没有符合条件的用户"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column prop="id" label="ID" min-width="70" />
        <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column label="角色" min-width="96">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" size="small" effect="light">
              {{ roleName(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="80">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :loading="statusUpdatingId === row.id"
              @change="(val: string | number | boolean) => handleToggleStatus(row as UserInfoResponse, val === true)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="points" label="积分" min-width="80" />
        <el-table-column label="注册时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="最后登录" min-width="150">
          <template #default="{ row }">{{ row.lastLoginAt ? formatDateTime(row.lastLoginAt) : '—' }}</template>
        </el-table-column>
        <!-- 固定宽度 + nowrap：无论表格被挤到多窄，两个按钮始终同一排 -->
        <el-table-column label="操作" width="140" fixed="right" class-name="op-column">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button size="small" @click="openEditDialog(row as UserInfoResponse)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDelete(row as UserInfoResponse)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Footer: batch + pagination -->
    <div class="table-footer">
      <div class="footer-left">
        <span v-if="selectedIds.length" class="selected-hint">已选 {{ selectedIds.length }} 项</span>
        <el-button
          v-if="selectedIds.length"
          type="danger"
          size="small"
          @click="handleBatchDelete"
        >
          批量删除
        </el-button>
      </div>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadUsers"
        @size-change="onSizeChange"
      />
    </div>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="formDialogVisible"
      :title="isEditing ? '编辑用户' : '新建用户'"
      width="480px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            :disabled="isEditing"
            placeholder="字母、数字、下划线，3-50 字符"
          />
          <div v-if="isEditing" class="form-hint">用户名是登录凭据，不支持修改</div>
        </el-form-item>
        <el-form-item :label="isEditing ? '新密码（留空则不修改）' : '密码'" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="至少8位，含大小写字母和数字" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="user@example.com" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-radio-group v-model="form.role">
            <el-radio-button value="member">普通成员</el-radio-button>
            <el-radio-button value="super_member">超级会员</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">禁用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="积分" prop="points">
          <el-input-number v-model="form.points" :min="0" :step="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">
          {{ isEditing ? '保存修改' : '创建用户' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, UserFilled } from '@element-plus/icons-vue'
import {
  getAdminUsers, getAdminUserStats, createAdminUser, updateAdminUser,
  updateAdminUserStatus, deleteAdminUser, deleteAdminUsers,
} from '@/api/admin'
import type {
  AdminUserListParams, AssignableRole, UserInfoResponse, UserRole, UserStats,
} from '@/types/user'
import { UserRoleColor, UserRoleMap } from '@/types/user'

const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/

const users = ref<UserInfoResponse[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const submitting = ref(false)
const statusUpdatingId = ref<number | null>(null)
const stats = ref<UserStats | null>(null)
const selectedIds = ref<number[]>([])

const dateRange = ref<[string, string] | null>(null)

const filters = reactive({
  keyword: '',
  role: '' as AssignableRole | '',
  status: null as number | null,
  sortField: 'createdAt' as NonNullable<AdminUserListParams['sortField']>,
  sortOrder: 'desc' as NonNullable<AdminUserListParams['sortOrder']>,
})

const statItems = computed(() => [
  { label: '总用户', value: stats.value?.total ?? '—' },
  { label: '普通成员', value: stats.value?.members ?? '—' },
  { label: '超级会员', value: stats.value?.superMembers ?? '—' },
  { label: '已禁用', value: stats.value?.disabled ?? '—' },
])

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const roleName = (role: UserRole) => UserRoleMap[role] ?? role
const roleTagType = (role: UserRole): TagType => (UserRoleColor[role] as TagType) ?? 'info'

const formatDateTime = (value: string): string => {
  if (!value) return '—'
  const d = new Date(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const loadUsers = async () => {
  loading.value = true
  try {
    const res = await getAdminUsers({
      page: currentPage.value,
      size: pageSize.value,
      keyword: filters.keyword.trim() || undefined,
      role: filters.role || undefined,
      status: filters.status,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      sortField: filters.sortField,
      sortOrder: filters.sortOrder,
    })
    users.value = res.data?.records || []
    total.value = res.data?.total || 0

    // 删完最后一页时自动回退，避免停在空页
    if (users.value.length === 0 && currentPage.value > 1) {
      currentPage.value -= 1
      await loadUsers()
    }
  } catch { /* 请求层已提示 */ }
  finally { loading.value = false }
}

const loadStats = async () => {
  try {
    const res = await getAdminUserStats()
    stats.value = res.data ?? null
  } catch {
    stats.value = null
  }
}

const refresh = async () => {
  await Promise.all([loadUsers(), loadStats()])
}

const applyFilters = () => {
  currentPage.value = 1
  loadUsers()
}

const onSizeChange = () => {
  currentPage.value = 1
  loadUsers()
}

// 关键词防抖：避免每敲一个字符打一次接口
let keywordTimer: ReturnType<typeof setTimeout> | null = null
const onKeywordInput = () => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(applyFilters, 300)
}

const resetFilters = () => {
  filters.keyword = ''
  filters.role = ''
  filters.status = null
  filters.sortField = 'createdAt'
  filters.sortOrder = 'desc'
  dateRange.value = null
  applyFilters()
}

const onSelectionChange = (rows: UserInfoResponse[]) => {
  selectedIds.value = rows.map((row) => row.id)
}

onMounted(refresh)
onUnmounted(() => {
  if (keywordTimer) clearTimeout(keywordTimer)
})

// ==================== 状态切换 ====================

const handleToggleStatus = async (row: UserInfoResponse, next: boolean) => {
  const target = next ? 1 : 0
  statusUpdatingId.value = row.id
  try {
    await updateAdminUserStatus(row.id, target)
    row.status = target
    ElMessage.success(target === 1 ? '账号已启用' : '账号已禁用')
    loadStats()
  } catch {
    // 失败时不改动 row.status，开关会因受控绑定自动回弹
  } finally {
    statusUpdatingId.value = null
  }
}

// ==================== 新建 / 编辑 ====================

const formDialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  password: '',
  email: '',
  role: 'member' as AssignableRole,
  status: 1,
  points: 0,
})

const formRules = computed<FormRules>(() => ({
  username: isEditing.value ? [] : [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '长度为 3-50 字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '只能包含字母、数字和下划线', trigger: 'blur' },
  ],
  password: [
    {
      required: !isEditing.value,
      validator: (_rule, value: string, callback) => {
        if (!value) {
          // 编辑时留空表示不修改
          isEditing.value ? callback() : callback(new Error('请输入密码'))
          return
        }
        if (value.length < 8) return callback(new Error('密码至少 8 位'))
        if (!PASSWORD_PATTERN.test(value)) return callback(new Error('需包含大小写字母和数字'))
        callback()
      },
      trigger: 'blur',
    },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}))

const resetForm = () => {
  form.username = ''
  form.password = ''
  form.email = ''
  form.role = 'member'
  form.status = 1
  form.points = 0
  formRef.value?.clearValidate()
}

const openCreateDialog = () => {
  isEditing.value = false
  editingId.value = null
  resetForm()
  formDialogVisible.value = true
}

const openEditDialog = (row: UserInfoResponse) => {
  isEditing.value = true
  editingId.value = row.id
  form.username = row.username
  form.password = ''
  form.email = row.email
  form.role = (row.role === 'super_member' ? 'super_member' : 'member') as AssignableRole
  form.status = row.status
  form.points = row.points
  formRef.value?.clearValidate()
  formDialogVisible.value = true
}

const submitForm = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEditing.value && editingId.value !== null) {
      await updateAdminUser(editingId.value, {
        email: form.email.trim(),
        role: form.role,
        status: form.status,
        points: form.points,
        // 留空时必须整字段省略：空串会触发后端 @Size 校验
        password: form.password ? form.password : undefined,
      })
      ElMessage.success('用户已更新')
    } else {
      await createAdminUser({
        username: form.username.trim(),
        password: form.password,
        email: form.email.trim(),
        role: form.role,
        status: form.status,
        points: form.points,
      })
      ElMessage.success('用户已创建')
    }
    formDialogVisible.value = false
    await refresh()
  } catch { /* 请求层已提示 */ }
  finally { submitting.value = false }
}

// ==================== 删除 ====================

const DELETE_WARNING = '此操作不可恢复，该账号名下的所有图片、相册及磁盘文件将一并删除。若只是想停用账号，请改用「禁用」。'

const handleDelete = async (row: UserInfoResponse) => {
  try {
    // 要求输入用户名才能执行，避免误删
    await ElMessageBox.prompt(
      `${DELETE_WARNING}\n\n请输入用户名「${row.username}」以确认删除：`,
      '删除用户',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        inputValidator: (value: string) =>
          value === row.username || '用户名不匹配',
      }
    )
  } catch {
    return
  }

  try {
    await deleteAdminUser(row.id)
    ElMessage.success('用户已删除')
    await refresh()
  } catch { /* 请求层已提示 */ }
}

const handleBatchDelete = async () => {
  const count = selectedIds.value.length
  if (!count) return

  try {
    await ElMessageBox.prompt(
      `${DELETE_WARNING}\n\n即将删除 ${count} 个账号，请输入 DELETE 以确认：`,
      '批量删除用户',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        inputValidator: (value: string) => value === 'DELETE' || '请输入 DELETE',
      }
    )
  } catch {
    return
  }

  try {
    await deleteAdminUsers(selectedIds.value)
    ElMessage.success(`已删除 ${count} 个账号`)
    selectedIds.value = []
    await refresh()
  } catch { /* 请求层已提示 */ }
}
</script>

<style scoped lang="scss">
.admin-users {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-header {
  position: relative;
  padding: 28px 36px;
  border-radius: var(--ev-radius-xl);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: var(--ev-blur-md);
  -webkit-backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
  overflow: hidden;

  .header-aurora {
    position: absolute;
    inset: 0;
    background: var(--ev-grad-aurora-soft);
  }

  .header-content {
    position: relative;
    z-index: 1;
    display: flex;
    justify-content: space-between;
    align-items: center;

    h1 {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 22px;
      font-weight: 700;
      color: var(--ev-text-primary);
      margin-bottom: 4px;
    }

    p {
      font-size: 13px;
      color: var(--ev-text-secondary);
    }
  }
}

.stats-bar,
.filter-bar,
.table-wrap,
.table-footer {
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: 16px;
  backdrop-filter: var(--ev-blur-md);
  -webkit-backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
}

.stats-bar {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  padding: 16px 20px;
  gap: 12px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .stat-value {
    font-size: 22px;
    font-weight: 800;
    color: var(--ev-text-primary);
    font-variant-numeric: tabular-nums;
  }

  .stat-label {
    font-size: 12px;
    color: var(--ev-text-secondary);
  }
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;

  /* 各控件按权重自适应剩余宽度，窄屏才换行 */
  .filter-keyword { flex: 3 1 190px; min-width: 0; }
  .filter-select { flex: 1 1 120px; min-width: 0; max-width: 170px; }
  .filter-order { flex: 1 1 88px; min-width: 0; max-width: 110px; }
  .filter-date {
    flex: 2 1 200px;
    min-width: 0;
    /* 覆盖 el-date-editor--daterange 的固定 350px */
    width: auto !important;
    max-width: 250px;
  }
  .filter-reset { flex: 0 0 auto; }
}

.table-wrap {
  padding: 8px 12px;
  overflow: hidden;

  :deep(.el-table) {
    background: transparent;
  }

  :deep(.el-table tr),
  :deep(.el-table th.el-table__cell) {
    background: transparent;
  }

  :deep(.op-column .cell) {
    padding-left: 8px;
    padding-right: 8px;
  }

  .op-cell {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: nowrap;
    white-space: nowrap;

    :deep(.el-button) {
      margin-left: 0;
    }
  }
}

.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 20px;
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

.form-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ev-text-muted);
}
</style>
