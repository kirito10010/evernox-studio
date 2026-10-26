<template>
  <div class="auth-required">
    <div class="auth-required-icon">
      <el-icon :size="30"><Lock /></el-icon>
    </div>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <div class="auth-required-actions">
      <el-button type="primary" size="large" @click="store.open('login')">登录</el-button>
      <el-button size="large" @click="store.open('register')">注册账号</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Lock } from '@element-plus/icons-vue'
import { useAuthDialogStore } from '@/stores/authDialog'

withDefaults(
  defineProps<{
    title?: string
    description?: string
  }>(),
  {
    title: '登录后查看',
    description: '这里展示的是你的个人数据，登录后即可查看和管理。',
  }
)

const store = useAuthDialogStore()
</script>

<style scoped lang="scss">
.auth-required {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 64px 24px;
  min-height: 320px;
}

.auth-required-icon {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(47, 124, 246, 0.12), rgba(79, 195, 232, 0.12));
  border: 1px solid var(--ev-border-subtle);
  color: var(--ev-primary);
  margin-bottom: 20px;
}

h3 {
  font-size: 20px;
  font-weight: 700;
  color: var(--ev-text-primary);
  margin-bottom: 8px;
}

p {
  font-size: 14px;
  line-height: 1.7;
  color: var(--ev-text-muted);
  max-width: 420px;
  margin-bottom: 24px;
}

.auth-required-actions {
  display: flex;
  gap: 12px;
}
</style>
