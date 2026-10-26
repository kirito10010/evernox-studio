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
      <AuthBrandPanel
        title="定格数字世界的"
        accent="永恒光影"
        description="新一代智能云端相册管理平台，赋予每一张图片极致的艺术价值与安全保障。"
        :features="brandFeatures"
      />

      <div class="form-panel">
        <div class="form-panel-inner">
          <LoginForm
            :initial-username="initialUsername"
            subtitle="请登录您的 EverNox 账号以继续操作"
            @success="router.push('/')"
            @forgot="router.push('/forgot-password')"
            @register="router.push('/register')"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import AuthBrandPanel from '@/components/auth/AuthBrandPanel.vue'
import LoginForm from '@/components/auth/LoginForm.vue'
import type { AuthFeature } from '@/components/auth/AuthBrandPanel.vue'

const router = useRouter()
const route = useRoute()

const initialUsername = typeof route.query.username === 'string' ? route.query.username : ''

const brandFeatures: AuthFeature[] = [
  { icon: 'lock', label: '顶级加密防护' },
  { icon: 'upload', label: '极速无损传输' },
  { icon: 'cloud', label: '安全云端存储' },
]
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
  /* blob 本身是 radial-gradient，边缘已足够柔和，不加 blur，避免高 DPR 下的整块滤镜开销 */
}

.blob-1 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(47, 124, 246, 0.28) 0%, transparent 70%);
  top: -180px;
  left: -120px;
}

.blob-2 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(79, 195, 232, 0.26) 0%, transparent 70%);
  bottom: -150px;
  right: -100px;
}

.blob-3 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(165, 228, 242, 0.3) 0%, transparent 70%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.blob-4 {
  width: 350px;
  height: 350px;
  background: radial-gradient(circle, rgba(207, 230, 255, 0.42) 0%, transparent 70%);
  top: 20%;
  right: 15%;
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

  &.particle-1 { left: 12%; top: 25%; width: 5px; height: 5px; background: rgba(47,124,246,0.45); box-shadow: 0 0 10px rgba(47,124,246,0.35); }
  &.particle-2 { left: 78%; top: 15%; width: 4px; height: 4px; background: rgba(79,195,232,0.45); box-shadow: 0 0 10px rgba(79,195,232,0.35); }
  &.particle-3 { left: 45%; top: 70%; width: 6px; height: 6px; background: rgba(127,178,251,0.40); box-shadow: 0 0 10px rgba(127,178,251,0.30); }
  &.particle-4 { left: 88%; top: 60%; width: 3px; height: 3px; background: rgba(165,228,242,0.55); box-shadow: 0 0 10px rgba(165,228,242,0.4); }
  &.particle-5 { left: 30%; top: 45%; width: 5px; height: 5px; background: rgba(47,124,246,0.40); box-shadow: 0 0 10px rgba(47,124,246,0.30); }
  &.particle-6 { left: 65%; top: 85%; width: 4px; height: 4px; background: rgba(79,195,232,0.45); box-shadow: 0 0 10px rgba(79,195,232,0.35); }
  &.particle-7 { left: 20%; top: 80%; width: 5px; height: 5px; background: rgba(127,178,251,0.40); box-shadow: 0 0 10px rgba(127,178,251,0.30); }
  &.particle-8 { left: 55%; top: 30%; width: 4px; height: 4px; background: rgba(165,228,242,0.55); box-shadow: 0 0 10px rgba(165,228,242,0.4); }
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
  box-shadow:
    var(--ev-shadow-float),
    0 2px 6px rgba(18, 48, 79, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  overflow: hidden;
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

@media (max-width: 768px) {
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
