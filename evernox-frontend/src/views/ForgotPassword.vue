<template>
  <div class="forgot-wrapper">
    <!-- Aurora mesh background -->
    <div class="aurora-bg">
      <div class="aurora-blob blob-1"></div>
      <div class="aurora-blob blob-2"></div>
      <div class="aurora-blob blob-3"></div>
    </div>

    <!-- Floating particles -->
    <div class="particles">
      <span v-for="i in 6" :key="i" class="particle" :class="`particle-${i}`"></span>
    </div>

    <!-- Grid overlay -->
    <div class="grid-overlay"></div>

    <!-- Main card -->
    <div class="forgot-card">
      <div class="form-header">
        <div class="brand-badge">
          <span class="badge-dot"></span>
          EVERNOX STUDIO
        </div>
        <h3>找回密码</h3>
        <p>请输入注册邮箱，接收验证码后设置新密码</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="forgot-form"
        @submit.prevent="handleReset"
      >
        <el-form-item prop="email">
          <label class="field-label">注册邮箱</label>
          <div class="email-row">
            <el-input
              v-model="form.email"
              placeholder="your@email.com"
              prefix-icon="Message"
              size="large"
              class="email-input"
            />
            <el-button
              size="large"
              class="send-btn"
              :disabled="countdown > 0"
              :loading="sending"
              @click="handleSendCode"
            >
              {{ countdown > 0 ? `${countdown}s 后重发` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item prop="code">
          <label class="field-label">验证码</label>
          <el-input
            v-model="form.code"
            placeholder="请输入 6 位数字验证码"
            prefix-icon="Key"
            size="large"
            maxlength="6"
          />
        </el-form-item>

        <el-form-item prop="newPassword">
          <label class="field-label">新密码</label>
          <el-input
            v-model="form.newPassword"
            type="password"
            placeholder="至少8位，包含大小写字母和数字"
            prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <label class="field-label">确认密码</label>
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="再次输入新密码"
            prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          native-type="submit"
          :loading="submitting"
          class="submit-btn"
        >
          重置密码
        </el-button>

        <div class="form-footer">
          <span>想起密码了？</span>
          <router-link to="/login" class="link-accent">返回登录</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { sendResetCode, resetPassword } from '@/api/auth'

const router = useRouter()

const formRef = ref<FormInstance>()
const sending = ref(false)
const submitting = ref(false)
const countdown = ref(0)

let timer: ReturnType<typeof setInterval> | null = null

const form = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
})

const validateConfirm = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value === '') {
    callback(new Error('请再次输入新密码'))
  } else if (value !== form.newPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 100, message: '密码长度为8-100个字符', trigger: 'blur' },
    { pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/, message: '必须包含大小写字母和数字', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

const startCountdown = () => {
  countdown.value = 60
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      if (timer) clearInterval(timer)
      timer = null
    }
  }, 1000)
}

const handleSendCode = async () => {
  if (sending.value || countdown.value > 0) return
  if (!formRef.value) return

  await formRef.value.validateField('email', async (valid) => {
    if (!valid) return
    sending.value = true
    try {
      await sendResetCode({ email: form.email })
      ElMessage.success('验证码已发送（若该邮箱已注册）')
      startCountdown()
    } finally {
      sending.value = false
    }
  })
}

const handleReset = async () => {
  if (!formRef.value || submitting.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await resetPassword({
        email: form.email,
        code: form.code,
        newPassword: form.newPassword,
      })
      ElMessage.success('密码重置成功，请用新密码登录')
      router.push('/login')
    } finally {
      submitting.value = false
    }
  })
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped lang="scss">
.forgot-wrapper {
  position: absolute;
  inset: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(160deg, #f7fbff 0%, #eef5fd 55%, #e6f2fb 100%);
  overflow-y: auto;
  overflow-x: hidden;
  padding: 24px 0;
}

.aurora-bg {
  position: fixed;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.aurora-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  will-change: transform;
}

.blob-1 {
  width: 560px;
  height: 560px;
  background: radial-gradient(circle, rgba(47, 124, 246, 0.26) 0%, transparent 70%);
  top: -160px;
  left: -120px;
  animation: aurora-drift 22s ease-in-out infinite;
}

.blob-2 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(79, 195, 232, 0.24) 0%, transparent 70%);
  bottom: -140px;
  right: -100px;
  animation: aurora-drift 26s ease-in-out infinite reverse;
}

.blob-3 {
  width: 380px;
  height: 380px;
  background: radial-gradient(circle, rgba(165, 228, 242, 0.30) 0%, transparent 70%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation: aurora-drift 30s ease-in-out infinite 3s;
}

@keyframes aurora-drift {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(30px, -20px) scale(1.06); }
}

.particles {
  position: fixed;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.particle {
  position: absolute;
  border-radius: 50%;

  &.particle-1 { left: 15%; top: 30%; width: 5px; height: 5px; background: rgba(47,124,246,0.45); }
  &.particle-2 { left: 75%; top: 20%; width: 4px; height: 4px; background: rgba(79,195,232,0.45); }
  &.particle-3 { left: 40%; top: 70%; width: 6px; height: 6px; background: rgba(127,178,251,0.40); }
  &.particle-4 { left: 85%; top: 60%; width: 3px; height: 3px; background: rgba(165,228,242,0.55); }
  &.particle-5 { left: 25%; top: 50%; width: 5px; height: 5px; background: rgba(79,195,232,0.45); }
  &.particle-6 { left: 60%; top: 82%; width: 4px; height: 4px; background: rgba(47,124,246,0.42); }
}

.grid-overlay {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(47, 124, 246, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(47, 124, 246, 0.05) 1px, transparent 1px);
  background-size: 60px 60px;
  mask-image: radial-gradient(ellipse 60% 60% at 50% 50%, black 20%, transparent 70%);
  -webkit-mask-image: radial-gradient(ellipse 60% 60% at 50% 50%, black 20%, transparent 70%);
}

.forgot-card {
  position: relative;
  z-index: 10;
  width: 460px;
  max-width: 94vw;
  padding: 40px 44px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: 24px;
  backdrop-filter: var(--ev-blur-lg);
  -webkit-backdrop-filter: var(--ev-blur-lg);
  box-shadow: var(--ev-shadow-float), inset 0 1px 0 rgba(255, 255, 255, 0.9);

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    background: rgba(255, 255, 255, 0.95);
  }
}

.form-header {
  margin-bottom: 28px;

  .brand-badge {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 2px;
    color: var(--ev-primary);
    margin-bottom: 16px;

    .badge-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--ev-aqua);
      box-shadow: 0 0 10px rgba(79, 195, 232, 0.7);
    }
  }

  h3 {
    font-size: 26px;
    font-weight: 700;
    color: var(--ev-text-primary);
    margin-bottom: 8px;
    letter-spacing: -0.3px;
  }

  p {
    font-size: 13px;
    color: var(--ev-text-muted);
  }
}

.forgot-form {
  .field-label {
    display: block;
    font-size: 12px;
    font-weight: 600;
    color: var(--ev-text-secondary);
    margin-bottom: 6px;
    letter-spacing: 0.3px;
  }

  :deep(.el-form-item) {
    margin-bottom: 20px;
  }

  .email-row {
    display: flex;
    gap: 10px;
    width: 100%;

    .email-input {
      flex: 1;
    }

    .send-btn {
      flex-shrink: 0;
      height: 40px;
      border-radius: 10px;
    }
  }

  .submit-btn {
    width: 100%;
    height: 46px;
    font-size: 15px;
    margin-top: 4px;
    border-radius: 12px !important;
  }

  .form-footer {
    text-align: center;
    margin-top: 20px;
    font-size: 13px;
    color: var(--ev-text-muted);
  }
}

.link-accent {
  margin-left: 6px;
  font-weight: 600;
  color: var(--ev-primary);

  &:hover {
    color: var(--ev-aqua);
  }
}

@media (max-width: 768px) {
  .forgot-card {
    padding: 32px 22px;
  }
}
</style>
