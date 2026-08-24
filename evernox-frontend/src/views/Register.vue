<template>
  <div class="register-wrapper">
    <!-- Aurora mesh background — cyan/emerald palette -->
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
    <div class="register-card">
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
            <h2>开启您的数字<br /><span class="aurora-text">光影之旅</span></h2>
            <p>加入 EverNox 社区，体验下一代云端图片管理、智能分类与隐私保护服务。</p>
          </div>

          <div class="brand-features">
            <div class="brand-feature">
              <span class="feature-icon">
                <el-icon :size="14"><Cloudy /></el-icon>
              </span>
              免费云端空间
            </div>
            <div class="brand-feature">
              <span class="feature-icon">
                <el-icon :size="14"><PictureFilled /></el-icon>
              </span>
              无损备份还原
            </div>
            <div class="brand-feature">
              <span class="feature-icon">
                <el-icon :size="14"><Medal /></el-icon>
              </span>
              尊享会员特权
            </div>
          </div>
        </div>
      </div>

      <!-- Right form panel -->
      <div class="form-panel">
        <div class="form-panel-inner">
          <div class="form-header">
            <h3>创建账号</h3>
            <p>请填写以下信息完成注册</p>
          </div>

          <el-form
            ref="formRef"
            :model="registerForm"
            :rules="rules"
            class="register-form"
            @submit.prevent="handleRegister"
          >
            <el-form-item prop="username">
              <label class="field-label">用户名</label>
              <el-input
                v-model="registerForm.username"
                placeholder="3-50个字符，包含字母数字下划线"
                prefix-icon="User"
                size="large"
              />
            </el-form-item>

            <el-form-item prop="email">
              <label class="field-label">电子邮箱</label>
              <el-input
                v-model="registerForm.email"
                placeholder="your@email.com"
                prefix-icon="Message"
                size="large"
              />
            </el-form-item>

            <el-form-item prop="password">
              <label class="field-label">密码</label>
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
              <label class="field-label">确认密码</label>
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="再次输入密码"
                prefix-icon="Lock"
                size="large"
                show-password
              />
            </el-form-item>

            <div class="form-actions">
              <el-button
                type="primary"
                size="large"
                native-type="submit"
                :loading="loading"
                class="submit-btn"
              >
                注册账号
              </el-button>
            </div>

            <div class="form-footer">
              <span>已有账号？</span>
              <router-link to="/login" class="link-accent">立即登录</router-link>
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
  if (!formRef.value) return
  if (loading.value) return


  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const success = await userStore.register({
          username: registerForm.username,
          email: registerForm.email,
          password: registerForm.password,
        })
        if (success) {
          router.push('/login')
        }
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped lang="scss">
.register-wrapper {
  position: absolute;
  inset: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(160deg, #f7fbff 0%, #eef5fd 55%, #e6f2fb 100%);
  overflow-y: auto;
  overflow-x: hidden;
  padding: 20px 0;
}

/* ---- Misty Background — aqua palette ---- */
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
  width: 550px;
  height: 550px;
  background: radial-gradient(circle, rgba(79, 195, 232, 0.28) 0%, transparent 70%);
  top: -160px;
  right: -100px;
  animation: aurora-drift 24s ease-in-out infinite;
}

.blob-2 {
  width: 480px;
  height: 480px;
  background: radial-gradient(circle, rgba(47, 124, 246, 0.24) 0%, transparent 70%);
  bottom: -140px;
  left: -80px;
  animation: aurora-drift 28s ease-in-out infinite reverse;
}

.blob-3 {
  width: 380px;
  height: 380px;
  background: radial-gradient(circle, rgba(165, 228, 242, 0.32) 0%, transparent 70%);
  top: 40%;
  left: 45%;
  transform: translate(-50%, -50%);
  animation: aurora-drift 30s ease-in-out infinite 2s;
}

.blob-4 {
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(207, 230, 255, 0.42) 0%, transparent 70%);
  top: 15%;
  left: 20%;
  animation: aurora-drift 34s ease-in-out infinite 4s reverse;
}

/* ---- Floating Particles ---- */
.particles {
  position: fixed;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.particle {
  position: absolute;
  border-radius: 50%;

  &.particle-1 { left: 18%; top: 30%; width: 5px; height: 5px; background: rgba(79,195,232,0.45); box-shadow: 0 0 10px rgba(79,195,232,0.35); animation: float-particle 17s ease-in-out infinite 2s; }
  &.particle-2 { left: 72%; top: 20%; width: 4px; height: 4px; background: rgba(47,124,246,0.42); box-shadow: 0 0 10px rgba(47,124,246,0.32); animation: float-particle 19s ease-in-out infinite 0s; }
  &.particle-3 { left: 40%; top: 65%; width: 6px; height: 6px; background: rgba(165,228,242,0.55); box-shadow: 0 0 10px rgba(165,228,242,0.4); animation: float-particle 16s ease-in-out infinite 4s; }
  &.particle-4 { left: 85%; top: 55%; width: 3px; height: 3px; background: rgba(127,178,251,0.40); box-shadow: 0 0 10px rgba(127,178,251,0.30); animation: float-particle 20s ease-in-out infinite 1s; }
  &.particle-5 { left: 25%; top: 50%; width: 5px; height: 5px; background: rgba(79,195,232,0.45); box-shadow: 0 0 10px rgba(79,195,232,0.35); animation: float-particle 18s ease-in-out infinite 3s; }
  &.particle-6 { left: 60%; top: 80%; width: 4px; height: 4px; background: rgba(47,124,246,0.42); box-shadow: 0 0 10px rgba(47,124,246,0.32); animation: float-particle 15s ease-in-out infinite 5s; }
  &.particle-7 { left: 35%; top: 12%; width: 5px; height: 5px; background: rgba(165,228,242,0.55); box-shadow: 0 0 10px rgba(165,228,242,0.4); animation: float-particle 21s ease-in-out infinite 2s; }
  &.particle-8 { left: 90%; top: 40%; width: 4px; height: 4px; background: rgba(127,178,251,0.40); box-shadow: 0 0 10px rgba(127,178,251,0.30); animation: float-particle 17s ease-in-out infinite 6s; }
}

/* ---- Grid Overlay ---- */
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

/* ---- Register Card ---- */
.register-card {
  position: relative;
  z-index: 10;
  width: 980px;
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
  flex: 1.1;
  position: relative;
  overflow: hidden;

  .brand-panel-inner {
    position: relative;
    z-index: 2;
    height: 100%;
    padding: 44px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(160deg, rgba(79, 195, 232, 0.10) 0%, rgba(47, 124, 246, 0.07) 50%, rgba(165, 228, 242, 0.12) 100%);
    z-index: 1;
  }

  &::after {
    content: '';
    position: absolute;
    right: 0;
    top: 0;
    bottom: 0;
    width: 1px;
    background: linear-gradient(180deg, transparent, rgba(79, 195, 232, 0.22), rgba(47, 124, 246, 0.22), transparent);
  }
}

/* Decorative mist lines */
.aurora-line {
  position: absolute;
  border-radius: 99px;
  opacity: 0.35;
  pointer-events: none;

  &.line-1 {
    width: 180px;
    height: 2px;
    background: linear-gradient(90deg, transparent, #4fc3e8, transparent);
    top: 25%;
    left: 10%;
    animation: aurora-drift 18s ease-in-out infinite;
  }

  &.line-2 {
    width: 150px;
    height: 2px;
    background: linear-gradient(90deg, transparent, #2f7cf6, transparent);
    top: 50%;
    right: 5%;
    animation: aurora-drift 22s ease-in-out infinite reverse;
  }

  &.line-3 {
    width: 130px;
    height: 2px;
    background: linear-gradient(90deg, transparent, #a5e4f2, transparent);
    bottom: 30%;
    left: 15%;
    animation: aurora-drift 26s ease-in-out infinite 3s;
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
  background: linear-gradient(135deg, rgba(79, 195, 232, 0.16), rgba(47, 124, 246, 0.16));
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
  margin: 28px 0;

  h2 {
    font-size: 30px;
    font-weight: 800;
    line-height: 1.3;
    color: var(--ev-text-primary);
    margin-bottom: 14px;
    letter-spacing: -0.5px;
  }

  p {
    font-size: 14px;
    line-height: 1.7;
    color: var(--ev-text-secondary);
    max-width: 340px;
  }
}

.aurora-text {
  background: linear-gradient(135deg, #4fc3e8 0%, #2f7cf6 100%);
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
    background: linear-gradient(135deg, rgba(79, 195, 232, 0.14), rgba(47, 124, 246, 0.14));
    color: var(--ev-aqua);
  }
}

/* ---- Form Panel (Right) ---- */
.form-panel {
  width: 460px;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.3);

  .form-panel-inner {
    height: 100%;
    padding: 40px 48px;
    display: flex;
    flex-direction: column;
    justify-content: center;
  }
}

.form-header {
  margin-bottom: 28px;

  h3 {
    font-size: 24px;
    font-weight: 700;
    color: var(--ev-text-primary);
    margin-bottom: 6px;
    letter-spacing: -0.3px;
  }

  p {
    font-size: 13px;
    color: var(--ev-text-muted);
  }
}

.register-form {
  .field-label {
    display: block;
    font-size: 12px;
    font-weight: 600;
    color: var(--ev-text-secondary);
    margin-bottom: 4px;
    letter-spacing: 0.3px;
  }

  :deep(.el-form-item) {
    margin-bottom: 16px;
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

/* ---- Responsive ---- */
@media (max-width: 768px) {
  .brand-panel {
    display: none;
  }

  .form-panel {
    width: 100%;

    .form-panel-inner {
      padding: 32px 20px;
    }
  }

  .register-card {
    max-width: 94vw;
  }
}
</style>
