<template>
  <div class="login-wrapper">
    <!-- Aurora mesh background -->
    <div class="aurora-bg">
      <div class="aurora-blob blob-1"></div>
      <div class="aurora-blob blob-2"></div>
      <div class="aurora-blob blob-3"></div>
      <div class="aurora-blob blob-4"></div>
    </div>

    <!-- Floating particles -->
    <div class="particles">
      <span v-for="i in 8" :key="i" class="particle" :class="`particle-${i}`"></span>
    </div>

    <!-- Grid overlay -->
    <div class="grid-overlay"></div>

    <!-- Main card -->
    <div class="login-card">
      <!-- Left brand panel -->
      <div class="brand-panel">
        <div class="brand-panel-inner">
          <!-- Decorative aurora lines -->
          <div class="aurora-line line-1"></div>
          <div class="aurora-line line-2"></div>
          <div class="aurora-line line-3"></div>

          <div class="brand-top">
            <div class="brand-logo">
              <img src="@/assets/logo.svg" alt="EverNox" class="logo-img" />
            </div>
            <div class="brand-badge">
              <span class="badge-dot"></span>
              EVERNOX STUDIO
            </div>
          </div>

          <div class="brand-content">
            <h2>定格数字世界的<br /><span class="aurora-text">永恒光影</span></h2>
            <p>新一代智能云端相册管理平台，赋予每一张图片极致的艺术价值与安全保障。</p>
          </div>

          <div class="brand-features">
            <div class="brand-feature">
              <span class="feature-icon">
                <el-icon :size="14"><Lock /></el-icon>
              </span>
              顶级加密防护
            </div>
            <div class="brand-feature">
              <span class="feature-icon">
                <el-icon :size="14"><UploadFilled /></el-icon>
              </span>
              极速无损传输
            </div>
            <div class="brand-feature">
              <span class="feature-icon">
                <el-icon :size="14"><Cloudy /></el-icon>
              </span>
              安全云端存储
            </div>
          </div>
        </div>
      </div>

      <!-- Right form panel -->
      <div class="form-panel">
        <div class="form-panel-inner">
          <div class="form-header">
            <h3>欢迎回来</h3>
            <p>请登录您的 EverNox 账号以继续操作</p>
          </div>

          <el-form
            ref="formRef"
            :model="loginForm"
            :rules="rules"
            class="login-form"
            @submit.prevent="handleLogin"
          >
            <el-form-item prop="username">
              <label class="field-label">账号 / 用户名</label>
              <el-input
                v-model="loginForm.username"
                placeholder="请输入您的用户名"
                prefix-icon="User"
                size="large"
              />
            </el-form-item>

            <el-form-item prop="password">
              <label class="field-label">访问密码</label>
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                prefix-icon="Lock"
                size="large"
                show-password
              />
            </el-form-item>

            <div class="form-actions">
              <el-button
                type="primary"
                native-type="submit"
                size="large"
                :loading="loading"
                class="submit-btn"
              >
                登录账号
              </el-button>
            </div>

            <div class="forgot-row">
              <router-link to="/forgot-password" class="link-accent">忘记密码？</router-link>
            </div>

            <div class="form-footer">
              <span>还未拥有账号？</span>
              <router-link to="/register" class="link-accent">立即注册</router-link>
            </div>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
})

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
  if (!formRef.value) return
  if (loading.value) return


  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const success = await userStore.login(loginForm)
        if (success) {
          router.push('/')
        }
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped lang="scss">
.login-wrapper {
  position: absolute;
  inset: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(160deg, #f7fbff 0%, #eef5fd 55%, #e6f2fb 100%);
  overflow: hidden;
}

/* ---- Misty Background ---- */
.aurora-bg {
  position: absolute;
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
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(47, 124, 246, 0.28) 0%, transparent 70%);
  top: -180px;
  left: -120px;
  animation: aurora-drift 22s ease-in-out infinite;
}

.blob-2 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(79, 195, 232, 0.26) 0%, transparent 70%);
  bottom: -150px;
  right: -100px;
  animation: aurora-drift 26s ease-in-out infinite reverse;
}

.blob-3 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(165, 228, 242, 0.30) 0%, transparent 70%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation: aurora-drift 30s ease-in-out infinite 3s;
}

.blob-4 {
  width: 350px;
  height: 350px;
  background: radial-gradient(circle, rgba(207, 230, 255, 0.42) 0%, transparent 70%);
  top: 20%;
  right: 15%;
  animation: aurora-drift 34s ease-in-out infinite 5s reverse;
}

/* ---- Floating Particles ---- */
.particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.particle {
  position: absolute;
  border-radius: 50%;

  &.particle-1 { left: 12%; top: 25%; width: 5px; height: 5px; background: rgba(47,124,246,0.45); box-shadow: 0 0 10px rgba(47,124,246,0.35); animation: float-particle 16s ease-in-out infinite 1s; }
  &.particle-2 { left: 78%; top: 15%; width: 4px; height: 4px; background: rgba(79,195,232,0.45); box-shadow: 0 0 10px rgba(79,195,232,0.35); animation: float-particle 18s ease-in-out infinite 3s; }
  &.particle-3 { left: 45%; top: 70%; width: 6px; height: 6px; background: rgba(127,178,251,0.40); box-shadow: 0 0 10px rgba(127,178,251,0.30); animation: float-particle 15s ease-in-out infinite 0s; }
  &.particle-4 { left: 88%; top: 60%; width: 3px; height: 3px; background: rgba(165,228,242,0.55); box-shadow: 0 0 10px rgba(165,228,242,0.4); animation: float-particle 20s ease-in-out infinite 5s; }
  &.particle-5 { left: 30%; top: 45%; width: 5px; height: 5px; background: rgba(47,124,246,0.40); box-shadow: 0 0 10px rgba(47,124,246,0.30); animation: float-particle 17s ease-in-out infinite 2s; }
  &.particle-6 { left: 65%; top: 85%; width: 4px; height: 4px; background: rgba(79,195,232,0.45); box-shadow: 0 0 10px rgba(79,195,232,0.35); animation: float-particle 19s ease-in-out infinite 4s; }
  &.particle-7 { left: 20%; top: 80%; width: 5px; height: 5px; background: rgba(127,178,251,0.40); box-shadow: 0 0 10px rgba(127,178,251,0.30); animation: float-particle 16s ease-in-out infinite 6s; }
  &.particle-8 { left: 55%; top: 30%; width: 4px; height: 4px; background: rgba(165,228,242,0.55); box-shadow: 0 0 10px rgba(165,228,242,0.4); animation: float-particle 21s ease-in-out infinite 1s; }
}

/* ---- Grid Overlay ---- */
.grid-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(47, 124, 246, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(47, 124, 246, 0.05) 1px, transparent 1px);
  background-size: 60px 60px;
  mask-image: radial-gradient(ellipse 60% 60% at 50% 50%, black 20%, transparent 70%);
  -webkit-mask-image: radial-gradient(ellipse 60% 60% at 50% 50%, black 20%, transparent 70%);
}

/* ---- Login Card ---- */
.login-card {
  position: relative;
  z-index: 10;
  width: 940px;
  max-width: 94vw;
  display: flex;
  background: rgba(255, 255, 255, 0.68);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: 24px;
  backdrop-filter: var(--ev-blur-lg);
  -webkit-backdrop-filter: var(--ev-blur-lg);
  box-shadow:
    var(--ev-shadow-float),
    0 2px 6px rgba(18, 48, 79, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  overflow: hidden;

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    background: rgba(255, 255, 255, 0.94);
  }
}

/* ---- Brand Panel (Left) ---- */
.brand-panel {
  flex: 1.15;
  position: relative;
  overflow: hidden;

  .brand-panel-inner {
    position: relative;
    z-index: 2;
    height: 100%;
    padding: 48px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(160deg, rgba(47, 124, 246, 0.10) 0%, rgba(79, 195, 232, 0.07) 50%, rgba(165, 228, 242, 0.12) 100%);
    z-index: 1;
  }

  &::after {
    content: '';
    position: absolute;
    right: 0;
    top: 0;
    bottom: 0;
    width: 1px;
    background: linear-gradient(180deg, transparent, rgba(47, 124, 246, 0.22), rgba(79, 195, 232, 0.22), transparent);
  }
}

/* Decorative mist lines */
.aurora-line {
  position: absolute;
  border-radius: 99px;
  opacity: 0.35;
  pointer-events: none;

  &.line-1 {
    width: 200px;
    height: 2px;
    background: linear-gradient(90deg, transparent, #2f7cf6, transparent);
    top: 30%;
    left: -20px;
    animation: aurora-drift 16s ease-in-out infinite;
  }

  &.line-2 {
    width: 160px;
    height: 2px;
    background: linear-gradient(90deg, transparent, #4fc3e8, transparent);
    top: 55%;
    right: 10%;
    animation: aurora-drift 20s ease-in-out infinite reverse;
  }

  &.line-3 {
    width: 120px;
    height: 2px;
    background: linear-gradient(90deg, transparent, #a5e4f2, transparent);
    bottom: 25%;
    left: 20%;
    animation: aurora-drift 24s ease-in-out infinite 2s;
  }
}

.brand-top {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.brand-logo {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(47, 124, 246, 0.16), rgba(79, 195, 232, 0.16));
  border: 1px solid var(--ev-border-default);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--ev-shadow-sm), inset 0 1px 0 rgba(255, 255, 255, 0.9);

  .logo-img {
    width: 32px;
    height: 32px;
  }
}

.brand-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--ev-primary);

  .badge-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--ev-aqua);
    box-shadow: 0 0 10px rgba(79, 195, 232, 0.7);
    animation: glow-pulse 3s ease-in-out infinite;
  }
}

.brand-content {
  margin: 32px 0;

  h2 {
    font-size: 32px;
    font-weight: 800;
    line-height: 1.3;
    color: var(--ev-text-primary);
    margin-bottom: 16px;
    letter-spacing: -0.5px;
  }

  p {
    font-size: 14px;
    line-height: 1.7;
    color: var(--ev-text-secondary);
    max-width: 360px;
  }
}

.aurora-text {
  background: linear-gradient(135deg, #2f7cf6 0%, #4fc3e8 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.brand-feature {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--ev-text-secondary);
  padding: 8px 14px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: 10px;
  box-shadow: var(--ev-shadow-xs);
  transition: all 0.3s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.85);
    border-color: var(--ev-border-hover);
    color: var(--ev-text-primary);
  }

  .feature-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border-radius: 8px;
    background: linear-gradient(135deg, rgba(47, 124, 246, 0.14), rgba(79, 195, 232, 0.14));
    color: var(--ev-primary);
  }
}

/* ---- Form Panel (Right) ---- */
.form-panel {
  width: 420px;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.3);

  .form-panel-inner {
    height: 100%;
    padding: 48px;
    display: flex;
    flex-direction: column;
    justify-content: center;
  }
}

.form-header {
  margin-bottom: 36px;

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

.login-form {
  .field-label {
    display: block;
    font-size: 12px;
    font-weight: 600;
    color: var(--ev-text-secondary);
    margin-bottom: 6px;
    letter-spacing: 0.3px;
  }

  :deep(.el-form-item) {
    margin-bottom: 22px;
  }

  .submit-btn {
    width: 100%;
    height: 48px;
    font-size: 15px;
    margin-top: 8px;
    border-radius: 12px !important;
  }

  .forgot-row {
    text-align: right;
    margin-top: 14px;
    font-size: 13px;

    .link-accent {
      margin-left: 0;
    }
  }

  .form-footer {
    text-align: center;
    margin-top: 28px;
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

/* ---- Responsive ---- */
@media (max-width: 768px) {
  .brand-panel {
    display: none;
  }

  .form-panel {
    width: 100%;

    .form-panel-inner {
      padding: 36px 24px;
    }
  }

  .login-card {
    max-width: 94vw;
  }
}
</style>
