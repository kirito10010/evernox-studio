<template>
  <el-dialog
    :model-value="store.visible"
    class="auth-dialog"
    width="900px"
    align-center
    :show-close="false"
    :close-on-click-modal="true"
    append-to-body
    destroy-on-close
    @update:model-value="handleVisibleChange"
  >
    <div class="auth-dialog-body">
      <AuthBrandPanel
        :variant="brand.variant"
        :title="brand.title"
        :accent="brand.accent"
        :description="brand.description"
        :features="brand.features"
      />

      <div class="auth-dialog-form">
        <button type="button" class="dialog-close" aria-label="关闭" @click="store.close()">
          <el-icon :size="18"><Close /></el-icon>
        </button>

        <LoginForm
          v-if="store.mode === 'login'"
          :initial-username="store.prefilledUsername"
          :subtitle="loginSubtitle"
          @success="handleLoginSuccess"
          @forgot="store.switchMode('forgot')"
          @register="store.switchMode('register')"
        />
        <RegisterForm
          v-else-if="store.mode === 'register'"
          @success="handleRegisterSuccess"
          @login="store.switchMode('login')"
        />
        <ForgotPasswordForm
          v-else
          @success="handleForgotSuccess"
          @login="store.switchMode('login')"
        />
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Close } from '@element-plus/icons-vue'
import { useAuthDialogStore } from '@/stores/authDialog'
import AuthBrandPanel from './AuthBrandPanel.vue'
import type { AuthFeature } from './AuthBrandPanel.vue'
import LoginForm from './LoginForm.vue'
import RegisterForm from './RegisterForm.vue'
import ForgotPasswordForm from './ForgotPasswordForm.vue'

interface BrandConfig {
  variant: 'blue' | 'aqua'
  title: string
  accent: string
  description: string
  features: AuthFeature[]
}

const store = useAuthDialogStore()
const router = useRouter()

const brandMap: Record<'login' | 'register' | 'forgot', BrandConfig> = {
  login: {
    variant: 'blue',
    title: '定格数字世界的',
    accent: '永恒光影',
    description: '新一代智能云端相册管理平台，赋予每一张图片极致的艺术价值与安全保障。',
    features: [
      { icon: 'lock', label: '顶级加密防护' },
      { icon: 'upload', label: '极速无损传输' },
      { icon: 'cloud', label: '安全云端存储' },
    ],
  },
  register: {
    variant: 'aqua',
    title: '开启您的数字',
    accent: '光影之旅',
    description: '加入 EverNox 社区，体验下一代云端图片管理、智能分类与隐私保护服务。',
    features: [
      { icon: 'cloud', label: '免费云端空间' },
      { icon: 'picture', label: '无损备份还原' },
      { icon: 'medal', label: '尊享会员特权' },
    ],
  },
  forgot: {
    variant: 'blue',
    title: '找回您的',
    accent: '数字光影',
    description: '通过注册邮箱验证身份，即可重新设置 EverNox 账号密码。',
    features: [
      { icon: 'key', label: '邮箱验证码验证' },
      { icon: 'lock', label: '密码安全重置' },
      { icon: 'cloud', label: '数据仍然完好' },
    ],
  },
}

const brand = computed(() => brandMap[store.mode])

const loginSubtitle = computed(() =>
  store.mode === 'login' && store.visible
    ? '登录后即可继续当前操作'
    : '请登录您的 EverNox 账号以继续操作'
)

const handleVisibleChange = (value: boolean) => {
  if (!value) {
    store.close()
  }
}

const handleLoginSuccess = () => {
  const { action, redirect } = store.consumePending()
  if (action) {
    action()
  } else if (redirect) {
    router.push(redirect)
  }
}

const handleRegisterSuccess = (username: string) => {
  store.setPrefilledUsername(username)
  store.switchMode('login')
}

const handleForgotSuccess = () => {
  store.switchMode('login')
}
</script>

<style lang="scss">
/* el-dialog 内容被 teleport 到 body，无法用 scoped 命中内部结构。
   这里必须用 .el-dialog.auth-dialog 提高优先级：main.scss 里全局的
   .el-dialog 带 !important 的背景/边框/阴影/圆角，只写 .auth-dialog 会被它覆盖。 */
.el-dialog.auth-dialog {
  max-width: 94vw;
  /* 去掉 el-dialog 自身的外观（背景/边框/阴影/内边距），
     只保留内部 .auth-dialog-body 这一层边框，避免出现双层边框与色差 */
  padding: 0 !important;
  background: transparent !important;
  border: none !important;
  border-radius: 0 !important;
  box-shadow: none !important;
  overflow: visible;

  .el-dialog__header {
    display: none;
  }

  .el-dialog__body {
    padding: 0;
    background: transparent;
  }
}

.auth-dialog-body {
  display: flex;
  min-height: 520px;
  background: #ffffff;
  border: 1px solid var(--ev-border-subtle);
  border-radius: 24px;
  box-shadow: var(--ev-shadow-float);
  overflow: hidden;
}

.auth-dialog-form {
  position: relative;
  width: 440px;
  flex-shrink: 0;
  padding: 36px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow-y: auto;
  max-height: 84vh;
  background: #ffffff;
}

.dialog-close {
  position: absolute;
  top: 14px;
  right: 16px;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--ev-text-muted);
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;

  &:hover {
    background: rgba(18, 48, 79, 0.06);
    color: var(--ev-text-primary);
  }
}

@media (max-width: 760px) {
  .el-dialog.auth-dialog {
    width: 94vw !important;
  }

  .auth-dialog-form {
    width: 100%;
    padding: 28px 22px;
    max-height: 86vh;
  }
}
</style>
