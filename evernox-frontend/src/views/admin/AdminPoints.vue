<template>
  <div class="admin-points">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按用户名 / 邮箱搜索用户"
        clearable
        style="width: 280px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" :loading="loading" @click="search">搜索</el-button>
    </div>

    <div
      class="user-cards"
      v-infinite-scroll="loadMore"
      :infinite-scroll-disabled="loading || !hasMore"
      infinite-scroll-distance="80"
    >
      <div class="user-card" v-for="u in users" :key="u.id">
        <div class="card-head">
          <el-avatar :size="40" icon="UserFilled" />
          <div class="head-meta">
            <div class="uname">{{ u.username }}</div>
            <div class="uemail">{{ u.email }}</div>
          </div>
          <el-tag :type="UserRoleColor[u.role as UserRole]" size="small">{{ roleName(u) }}</el-tag>
        </div>
        <div class="card-body">
          <div class="info-row">
            <span class="label">积分</span>
            <span class="value points">{{ u.points }}</span>
          </div>
          <div class="info-row">
            <span class="label">会员到期</span>
            <span class="value">{{ fmtTime(u.superMemberExpiresAt) }}</span>
          </div>
        </div>
        <div class="card-actions">
          <el-button size="small" type="primary" @click="openRecharge(u)">充值</el-button>
          <el-button size="small" type="warning" @click="openSetMember(u)">设会员</el-button>
        </div>
      </div>

      <p v-if="loading" class="load-hint">加载中...</p>
      <p v-else-if="!hasMore && users.length > 0" class="load-hint">没有更多了</p>
      <el-empty
        v-if="!loading && users.length === 0"
        description="暂无用户"
        :image-size="80"
        style="grid-column: 1 / -1"
      />
    </div>

    <!-- 充值弹窗 -->
    <el-dialog v-model="rechargeVisible" title="充值积分" width="440px">
      <el-form label-width="80px">
        <el-form-item label="用户">
          <el-input :model-value="rechargeUsername" disabled />
        </el-form-item>
        <el-form-item label="充值积分">
          <el-input-number v-model="rechargePointsValue" :min="1" :precision="0" style="width: 180px" />
        </el-form-item>
        <el-form-item label="快捷档位">
          <el-button-group>
            <el-button v-for="p in [10, 50, 100, 500, 1000]" :key="p" @click="rechargePointsValue = p">{{ p }}</el-button>
          </el-button-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="rechargeDescription" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" :loading="recharging" @click="submitRecharge">确认充值</el-button>
      </template>
    </el-dialog>

    <!-- 设超级会员弹窗 -->
    <el-dialog v-model="memberVisible" title="设置超级会员" width="440px">
      <el-form label-width="80px">
        <el-form-item label="用户">
          <el-input :model-value="memberUsername" disabled />
        </el-form-item>
        <el-form-item label="时长">
          <el-radio-group v-model="memberDaysChoice">
            <el-radio-button :value="7">一周</el-radio-button>
            <el-radio-button :value="30">一个月</el-radio-button>
            <el-radio-button :value="90">三个月</el-radio-button>
            <el-radio-button :value="365">一年</el-radio-button>
            <el-radio-button :value="0">自定义</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="memberDaysChoice === 0" label="自定义天数">
          <el-input-number v-model="memberCustomDays" :min="1" :precision="0" style="width: 180px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberVisible = false">取消</el-button>
        <el-button type="primary" :loading="settingMember" @click="submitSetMember">确认设置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { getAdminUsers, rechargePoints, setSuperMember } from '@/api/admin'
import { UserRoleColor, UserRoleMap, type UserInfoResponse, type UserRole } from '@/types/user'

const keyword = ref('')
const users = ref<UserInfoResponse[]>([])
const page = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(false)

const hasMore = computed(() => users.value.length < total.value)

const loadUsers = async (reset: boolean) => {
  if (loading.value) return
  if (!reset && !hasMore.value) return
  if (reset) {
    page.value = 1
    users.value = []
    total.value = 0
  }
  loading.value = true
  try {
    const res = await getAdminUsers({
      page: page.value,
      size: pageSize,
      keyword: keyword.value.trim() || undefined,
    })
    const records = res.data?.records ?? []
    total.value = res.data?.total ?? 0
    users.value = reset ? records : [...users.value, ...records]
    page.value += 1
  } finally {
    loading.value = false
  }
}

const search = () => loadUsers(true)
const loadMore = () => loadUsers(false)

onMounted(() => loadUsers(true))

const roleName = (row: UserInfoResponse): string => UserRoleMap[row.role as UserRole] || row.role
const fmtTime = (v?: string): string => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-')

// ==================== 充值 ====================
const rechargeVisible = ref(false)
const rechargeUserId = ref<number | null>(null)
const rechargeUsername = ref('')
const rechargePointsValue = ref(10)
const rechargeDescription = ref('')
const recharging = ref(false)

const openRecharge = (row: UserInfoResponse) => {
  rechargeUserId.value = row.id
  rechargeUsername.value = row.username
  rechargePointsValue.value = 10
  rechargeDescription.value = ''
  rechargeVisible.value = true
}

const submitRecharge = async () => {
  if (!rechargeUserId.value) return
  recharging.value = true
  try {
    await rechargePoints({
      userId: rechargeUserId.value,
      points: rechargePointsValue.value,
      description: rechargeDescription.value || undefined,
    })
    ElMessage.success('充值成功')
    rechargeVisible.value = false
    loadUsers(true)
  } finally {
    recharging.value = false
  }
}

// ==================== 设超级会员 ====================
const memberVisible = ref(false)
const memberUserId = ref<number | null>(null)
const memberUsername = ref('')
const memberDaysChoice = ref<number>(30)
const memberCustomDays = ref(30)
const settingMember = ref(false)

const openSetMember = (row: UserInfoResponse) => {
  memberUserId.value = row.id
  memberUsername.value = row.username
  memberDaysChoice.value = 30
  memberCustomDays.value = 30
  memberVisible.value = true
}

const submitSetMember = async () => {
  if (!memberUserId.value) return
  const days = memberDaysChoice.value === 0 ? memberCustomDays.value : memberDaysChoice.value
  if (!days || days <= 0) {
    ElMessage.warning('请输入有效天数')
    return
  }
  settingMember.value = true
  try {
    await setSuperMember({ userId: memberUserId.value, days })
    ElMessage.success('设置成功')
    memberVisible.value = false
    loadUsers(true)
  } finally {
    settingMember.value = false
  }
}
</script>

<style scoped lang="scss">
.admin-points {
  .toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;
  }

  .user-cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 16px;
    max-height: calc(100vh - 220px);
    overflow-y: auto;
    padding: 4px;

    .user-card {
      background: rgba(255, 255, 255, 0.7);
      border: 1px solid var(--ev-border-subtle);
      border-radius: 14px;
      padding: 16px;
      transition: box-shadow 0.2s;

      &:hover {
        box-shadow: var(--ev-shadow-sm);
      }

      .card-head {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-bottom: 14px;

        .head-meta {
          flex: 1;
          min-width: 0;

          .uname {
            font-size: 14px;
            font-weight: 600;
            color: var(--ev-text-primary);
          }

          .uemail {
            font-size: 12px;
            color: var(--ev-text-muted);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }
        }
      }

      .card-body {
        display: flex;
        flex-direction: column;
        gap: 8px;
        margin-bottom: 14px;

        .info-row {
          display: flex;
          justify-content: space-between;
          font-size: 13px;

          .label {
            color: var(--ev-text-secondary);
          }

          .value.points {
            font-weight: 700;
            color: var(--ev-primary);
          }
        }
      }

      .card-actions {
        display: flex;
        gap: 8px;
      }
    }

    .load-hint {
      grid-column: 1 / -1;
      text-align: center;
      font-size: 13px;
      color: var(--ev-text-muted);
    }
  }
}
</style>
