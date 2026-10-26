<template>
  <div class="auth-form-body">
    <div class="auth-form-header">
      <h3>找回密码</h3>
      <p>请输入注册邮箱，接收验证码后设置新密码</p>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      class="auth-form forgot-form"
      @submit.prevent="handleReset"
    >
      <el-form-item prop="email">
        <label class="auth-field-label">注册邮箱</label>
        <div class="email-row">
          <el-input
            v-model="form.email"
            placeholder="your@email.com"
            prefix-icon="Message"
            size="large"
            class="email-input"
          />
          <el-button
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
        <label class="auth-field-label">验证码</label>
        <el-input
          v-model="form.code"
          placeholder="请输入 6 位数字验证码"
          prefix-icon="Key"
          size="large"
          maxlength="6"
        />
      </el-form-item>

      <el-form-item prop="newPassword">
        <label class="auth-field-label">新密码</label>
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
        <label class="auth-field-label">确认密码</label>
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
        class="auth-submit-btn"
      >
        重置密码
      </el-button>

      <div class="auth-footer">
        <span>想起密码了？</span>
        <a class="auth-link" @click.prevent="emit('login')">返回登录</a>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { sendResetCode, resetPassword } from '@/api/auth'

const emit = defineEmits<{
  success: [email: string]
  login: []
}>()

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
  if (sending.value || countdown.value > 0 || !formRef.value) return

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
      emit('success', form.email)
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
@use './auth-form';

.forgot-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
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
}
</style>
