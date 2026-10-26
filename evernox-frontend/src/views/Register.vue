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
      <AuthBrandPanel
        variant="aqua"
        title="开启您的数字"
        accent="光影之旅"
        description="加入 EverNox 社区，体验下一代云端图片管理、智能分类与隐私保护服务。"
        :features="brandFeatures"
      />

      <div class="form-panel">
        <div class="form-panel-inner">
          <RegisterForm @success="handleSuccess" @login="router.push('/login')" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import AuthBrandPanel from '@/components/auth/AuthBrandPanel.vue'
import RegisterForm from '@/components/auth/RegisterForm.vue'
import type { AuthFeature } from '@/components/auth/AuthBrandPanel.vue'

const router = useRouter()

const brandFeatures: AuthFeature[] = [
  { icon: 'cloud', label: '免费云端空间' },
  { icon: 'picture', label: '无损备份还原' },
  { icon: 'medal', label: '尊享会员特权' },
]

const handleSuccess = (username: string) => {
  router.push({ path: '/login', query: { username } })
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
}

.blob-1 {
  width: 550px;
  height: 550px;
  background: radial-gradient(circle, rgba(79, 195, 232, 0.28) 0%, transparent 70%);
  top: -160px;
  right: -100px;
}

.blob-2 {
  width: 480px;
  height: 480px;
  background: radial-gradient(circle, rgba(47, 124, 246, 0.24) 0%, transparent 70%);
  bottom: -140px;
  left: -80px;
}

.blob-3 {
  width: 380px;
  height: 380px;
  background: radial-gradient(circle, rgba(165, 228, 242, 0.32) 0%, transparent 70%);
  top: 40%;
  left: 45%;
  transform: translate(-50%, -50%);
}

.blob-4 {
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(207, 230, 255, 0.42) 0%, transparent 70%);
  top: 15%;
  left: 20%;
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

  &.particle-1 { left: 18%; top: 30%; width: 5px; height: 5px; background: rgba(79,195,232,0.45); box-shadow: 0 0 10px rgba(79,195,232,0.35); }
  &.particle-2 { left: 72%; top: 20%; width: 4px; height: 4px; background: rgba(47,124,246,0.42); box-shadow: 0 0 10px rgba(47,124,246,0.32); }
  &.particle-3 { left: 40%; top: 65%; width: 6px; height: 6px; background: rgba(165,228,242,0.55); box-shadow: 0 0 10px rgba(165,228,242,0.4); }
  &.particle-4 { left: 85%; top: 55%; width: 3px; height: 3px; background: rgba(127,178,251,0.40); box-shadow: 0 0 10px rgba(127,178,251,0.30); }
  &.particle-5 { left: 25%; top: 50%; width: 5px; height: 5px; background: rgba(79,195,232,0.45); box-shadow: 0 0 10px rgba(79,195,232,0.35); }
  &.particle-6 { left: 60%; top: 80%; width: 4px; height: 4px; background: rgba(47,124,246,0.42); box-shadow: 0 0 10px rgba(47,124,246,0.32); }
  &.particle-7 { left: 35%; top: 12%; width: 5px; height: 5px; background: rgba(165,228,242,0.55); box-shadow: 0 0 10px rgba(165,228,242,0.4); }
  &.particle-8 { left: 90%; top: 40%; width: 4px; height: 4px; background: rgba(127,178,251,0.40); box-shadow: 0 0 10px rgba(127,178,251,0.30); }
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
  box-shadow:
    var(--ev-shadow-float),
    0 2px 6px rgba(18, 48, 79, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  overflow: hidden;
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

@media (max-width: 768px) {
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
