<template>
  <div class="auth-form-body">
    <div class="auth-form-header">
      <h3>创建账号</h3>
      <p>请填写以下信息完成注册</p>
    </div>

    <el-form
      ref="formRef"
      :model="registerForm"
      :rules="rules"
      class="auth-form"
      @submit.prevent="handleRegister"
    >
      <el-form-item prop="username">
        <label class="auth-field-label">用户名</label>
        <el-input
          v-model="registerForm.username"
          placeholder="3-50个字符，包含字母数字下划线"
          prefix-icon="User"
          size="large"
        />
      </el-form-item>

      <el-form-item prop="email">
        <label class="auth-field-label">电子邮箱</label>
        <el-input
          v-model="registerForm.email"
          placeholder="your@email.com"
          prefix-icon="Message"
          size="large"
        />
      </el-form-item>

      <el-form-item prop="password">
        <label class="auth-field-label">密码</label>
        <el-input
          v-model="registerForm.password"
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
          v-model="registerForm.confirmPassword"
          type="password"
          placeholder="再次输入密码"
          prefix-icon="Lock"
          size="large"
          show-password
        />
      </el-form-item>

      <el-button
        type="primary"
        size="large"
        native-type="submit"
        :loading="loading"
        class="auth-submit-btn"
      >
        注册账号
      </el-button>

      <div class="auth-footer">
        <span>已有账号？</span>
        <a class="auth-link" @click.prevent="emit('login')">立即登录</a>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'

const emit = defineEmits<{
  success: [username: string]
  login: []
}>()

const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const validatePass = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== registerForm.password) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度为3-50个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '只能包含字母、数字和下划线', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 100, message: '密码长度为8-100个字符', trigger: 'blur' },
    { pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/, message: '必须包含大小写字母和数字', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validatePass, trigger: 'blur' },
  ],
}

const handleRegister = async () => {
  if (!formRef.value || loading.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const username = registerForm.username
      const success = await userStore.register({
        username,
        email: registerForm.email,
        password: registerForm.password,
      })
      if (success) {
        emit('success', username)
      }
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped lang="scss">
@use './auth-form';
</style>
