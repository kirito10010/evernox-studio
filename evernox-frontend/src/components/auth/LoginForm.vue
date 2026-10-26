<template>
  <div class="auth-form-body">
    <div class="auth-form-header">
      <h3>欢迎回来</h3>
      <p>{{ subtitle }}</p>
    </div>

    <el-form
      ref="formRef"
      :model="loginForm"
      :rules="rules"
      class="auth-form"
      @submit.prevent="handleLogin"
    >
      <el-form-item prop="username">
        <label class="auth-field-label">账号 / 用户名</label>
        <el-input
          v-model="loginForm.username"
          placeholder="请输入您的用户名"
          prefix-icon="User"
          size="large"
        />
      </el-form-item>

      <el-form-item prop="password">
        <label class="auth-field-label">访问密码</label>
        <el-input
          v-model="loginForm.password"
          type="password"
          placeholder="请输入密码"
          prefix-icon="Lock"
          size="large"
          show-password
        />
      </el-form-item>

      <el-button
        type="primary"
        native-type="submit"
        size="large"
        :loading="loading"
        class="auth-submit-btn"
      >
        登录账号
      </el-button>

      <div class="forgot-row">
        <a class="auth-link" @click.prevent="emit('forgot')">忘记密码？</a>
      </div>

      <div class="auth-footer">
        <span>还未拥有账号？</span>
        <a class="auth-link" @click.prevent="emit('register')">立即注册</a>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'

const props = withDefaults(
  defineProps<{
    initialUsername?: string
    subtitle?: string
  }>(),
  {
    initialUsername: '',
    subtitle: '请登录您的 EverNox 账号以继续操作',
  }
)

const emit = defineEmits<{
  success: []
  forgot: []
  register: []
}>()

const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const loginForm = reactive({
  username: props.initialUsername,
  password: '',
})

watch(
  () => props.initialUsername,
  (value) => {
    loginForm.username = value
  }
)

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度为3-50个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 100, message: '密码长度为8-100个字符', trigger: 'blur' },
  ],
}

const handleLogin = async () => {
  if (!formRef.value || loading.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const success = await userStore.login({ ...loginForm })
      if (success) {
        emit('success')
      }
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped lang="scss">
@use './auth-form';

.forgot-row {
  text-align: right;
  margin-top: 12px;
  font-size: 13px;
}
</style>
