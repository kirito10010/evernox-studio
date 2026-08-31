<template>
  <div class="profile-page">
    <div class="col">
      <div class="profile-card">
        <h2 class="section-title">个人信息</h2>
        <div class="points-banner">
          <div class="points-value">{{ userInfo?.points ?? 0 }}</div>
          <div class="points-label">当前积分</div>
          <el-button class="signin-btn" type="primary" size="small" :loading="signingIn" :disabled="signedToday" @click="doSignIn">
            {{ signedToday ? '今日已签到' : '每日签到 +10' }}
          </el-button>
        </div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户名">{{ userInfo?.username ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ userInfo?.email ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ roleName }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText }}</el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ fmtTime(userInfo?.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="最后登录">{{ fmtTime(userInfo?.lastLoginAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="isSuperMember" label="超级会员到期">{{ fmtTime(userInfo?.superMemberExpiresAt) }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </div>

    <div class="col">
      <div v-if="!isAdmin" class="profile-card">
        <h2 class="section-title">超级会员</h2>
        <p v-if="isSuperMember" class="member-status">
          当前到期时间：{{ fmtTime(userInfo?.superMemberExpiresAt) }}
        </p>
        <p v-else class="member-status muted">你当前不是超级会员，可用积分开通</p>

        <div class="upgrade-options">
          <div
            v-for="o in upgradeOptions"
            :key="o.days"
            class="upgrade-option"
            :class="{ active: upgradeDays === o.days }"
            @click="upgradeDays = o.days"
          >
            <div class="opt-label">{{ o.label }}</div>
            <div class="opt-points">{{ o.points }} 积分</div>
          </div>
        </div>
        <div class="upgrade-actions">
          <span class="balance">我的积分：{{ userInfo?.points ?? 0 }}</span>
          <el-button type="primary" :loading="upgrading" @click="doUpgrade">立即开通</el-button>
        </div>
        <div class="recharge-hint">
          <span class="hint-text">需要充值？进 QQ 群找管理员充值</span>
          <el-link type="primary" :href="qqGroupUrl" target="_blank">进群充值</el-link>
        </div>
        <div class="redeem-row">
          <el-input v-model="redeemCodeInput" placeholder="输入卡密兑换超级会员" clearable />
          <el-button type="success" :loading="redeeming" @click="doRedeem">兑换</el-button>
        </div>
      </div>

      <div class="profile-card">
        <h2 class="section-title">修改密码（邮箱验证）</h2>
        <el-form label-width="90px" class="password-form">
          <el-form-item label="邮箱">
            <el-input :model-value="userInfo?.email ?? ''" disabled />
          </el-form-item>
          <el-form-item label="验证码">
            <div class="code-row">
              <el-input v-model="code" placeholder="6位验证码" maxlength="6" />
              <el-button :disabled="!userInfo?.email || counting > 0" @click="sendCode">
                {{ counting > 0 ? `${counting}s 后重发` : '发送验证码' }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="newPassword" type="password" show-password placeholder="8-100位，含大小写和数字" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="confirmPassword" type="password" show-password placeholder="再次输入新密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="submitChange">确认修改</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { getUserInfo, sendResetCode, resetPassword } from '@/api/auth'
import { signIn, upgradeSuperMember, redeemCode } from '@/api/points'
import { useUserStore } from '@/stores/user'
import { UserRoleMap, type UserInfoResponse, type UserRole } from '@/types/user'

const userStore = useUserStore()
const userInfo = ref<UserInfoResponse | null>(null)
const code = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const signingIn = ref(false)
const counting = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const roleName = computed(() => {
  if (!userInfo.value) return '-'
  return UserRoleMap[userInfo.value.role as UserRole] || userInfo.value.role
})
const statusText = computed(() => (userInfo.value?.status === 1 ? '激活' : '禁用'))
const isSuperMember = computed(() => userInfo.value?.role === 'super_member')
const isAdmin = computed(() => userInfo.value?.role === 'admin')
const signedToday = computed(() => {
  if (!userInfo.value?.lastSigninAt) return false
  return dayjs(userInfo.value.lastSigninAt).format('YYYY-MM-DD') === dayjs().format('YYYY-MM-DD')
})

const upgradeOptions = [
  { label: '一周', days: 7, points: 150 },
  { label: '一个月', days: 30, points: 600 },
  { label: '2个月', days: 60, points: 1200 },
  { label: '3个月', days: 90, points: 1800 },
  { label: '一年', days: 365, points: 7200 },
]
const qqGroupUrl = 'https://qm.qq.com/q/2ffak4dhFK'
const upgradeDays = ref(30)
const upgrading = ref(false)

const doUpgrade = async () => {
  upgrading.value = true
  try {
    await upgradeSuperMember(upgradeDays.value)
    ElMessage.success('开通成功')
    await load()
  } finally {
    upgrading.value = false
  }
}

const redeemCodeInput = ref('')
const redeeming = ref(false)

const doRedeem = async () => {
  const code = redeemCodeInput.value.trim()
  if (!code) {
    ElMessage.warning('请输入卡密')
    return
  }
  redeeming.value = true
  try {
    await redeemCode(code)
    ElMessage.success('兑换成功')
    redeemCodeInput.value = ''
    await load()
  } finally {
    redeeming.value = false
  }
}

const fmtTime = (v?: string): string => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-')

const load = async () => {
  const res = await getUserInfo()
  userInfo.value = res.data
}

const doSignIn = async () => {
  signingIn.value = true
  try {
    await signIn()
    ElMessage.success('签到成功，+10 积分')
    await load()
  } finally {
    signingIn.value = false
  }
}

const sendCode = async () => {
  if (!userInfo.value?.email) return
  await sendResetCode({ email: userInfo.value.email })
  ElMessage.success('验证码已发送，请查收邮箱')
  counting.value = 60
  timer = setInterval(() => {
    counting.value -= 1
    if (counting.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

const submitChange = async () => {
  if (!code.value) {
    ElMessage.warning('请输入验证码')
    return
  }
  if (!newPassword.value) {
    ElMessage.warning('请输入新密码')
    return
  }
  if (newPassword.value.length < 8) {
    ElMessage.warning('密码至少 8 位')
    return
  }
  if (!/(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+/.test(newPassword.value)) {
    ElMessage.warning('密码需包含大小写字母和数字')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (!userInfo.value?.email) return
  submitting.value = true
  try {
    await resetPassword({ email: userInfo.value.email, code: code.value, newPassword: newPassword.value })
    ElMessage.success('密码修改成功，请重新登录')
    userStore.logout()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped lang="scss">
.profile-page {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  width: 100%;
  align-items: start;

  .col {
    display: flex;
    flex-direction: column;
    gap: 16px;
    min-width: 0;
  }

  .profile-card {
    background: rgba(255, 255, 255, 0.7);
    border: 1px solid var(--ev-border-subtle);
    border-radius: 16px;
    padding: 24px;

    .section-title {
      font-size: 16px;
      font-weight: 700;
      color: var(--ev-text-primary);
      margin: 0 0 16px;
    }

    .points-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 16px;
      margin-bottom: 16px;
      border-radius: 12px;
      background: linear-gradient(135deg, rgba(47, 124, 246, 0.1), rgba(79, 195, 232, 0.1));

      .points-value {
        font-size: 32px;
        font-weight: 800;
        color: var(--ev-primary);
        line-height: 1;
      }

      .points-label {
        font-size: 13px;
        color: var(--ev-text-secondary);
      }

      .signin-btn {
        margin-left: auto;
      }
    }

    .member-status {
      font-size: 13px;
      color: var(--ev-text-primary);
      margin: 0 0 12px;

      &.muted {
        color: var(--ev-text-muted);
      }
    }

    .upgrade-options {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
      gap: 10px;
      margin-bottom: 14px;

      .upgrade-option {
        padding: 10px;
        border: 1px solid var(--ev-border-subtle);
        border-radius: 10px;
        text-align: center;
        cursor: pointer;
        transition: all 0.2s;

        .opt-label {
          font-size: 14px;
          font-weight: 600;
          color: var(--ev-text-primary);
        }

        .opt-points {
          font-size: 12px;
          color: var(--ev-text-muted);
          margin-top: 2px;
        }

        &:hover {
          border-color: var(--ev-border-hover);
        }

        &.active {
          border-color: var(--ev-primary);
          background: rgba(47, 124, 246, 0.06);

          .opt-label,
          .opt-points {
            color: var(--ev-primary);
          }
        }
      }
    }

    .upgrade-actions {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .balance {
        font-size: 13px;
        color: var(--ev-text-secondary);
      }
    }

    .recharge-hint {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-top: 12px;
      font-size: 13px;

      .hint-text {
        color: var(--ev-text-muted);
      }
    }

    .redeem-row {
      display: flex;
      gap: 8px;
      margin-top: 12px;
    }

    .password-form {
      .code-row {
        display: flex;
        gap: 8px;
        width: 100%;
      }
    }
  }
}
</style>
