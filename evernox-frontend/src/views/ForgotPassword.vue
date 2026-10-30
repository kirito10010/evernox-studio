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
      <div class="brand-badge">
        <span class="badge-dot"></span>
        EVERNOX STUDIO
      </div>
      <ForgotPasswordForm @success="handleSuccess" @login="router.push('/login')" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import ForgotPasswordForm from '@/components/auth/ForgotPasswordForm.vue'

const router = useRouter()

const handleSuccess = (_email: string) => {
  router.push('/login')
}
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
}

.blob-1 {
  width: 560px;
  height: 560px;
  background: radial-gradient(circle, rgba(var(--ev-primary-rgb), 0.26) 0%, transparent 70%);
  top: -160px;
  left: -120px;
}

.blob-2 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(var(--ev-aqua-rgb), 0.24) 0%, transparent 70%);
  bottom: -140px;
  right: -100px;
}

.blob-3 {
  width: 380px;
  height: 380px;
  background: radial-gradient(circle, rgba(165, 228, 242, 0.3) 0%, transparent 70%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
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

  &.particle-1 { left: 15%; top: 30%; width: 5px; height: 5px; background: rgba(var(--ev-primary-rgb), 0.45); }
  &.particle-2 { left: 75%; top: 20%; width: 4px; height: 4px; background: rgba(var(--ev-aqua-rgb), 0.45); }
  &.particle-3 { left: 40%; top: 70%; width: 6px; height: 6px; background: rgba(var(--ev-primary-soft-rgb), 0.40); }
  &.particle-4 { left: 85%; top: 60%; width: 3px; height: 3px; background: rgba(var(--ev-aqua-soft-rgb), 0.55); }
  &.particle-5 { left: 25%; top: 50%; width: 5px; height: 5px; background: rgba(var(--ev-aqua-rgb), 0.45); }
  &.particle-6 { left: 60%; top: 82%; width: 4px; height: 4px; background: rgba(var(--ev-primary-rgb), 0.42); }
}

.grid-overlay {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(var(--ev-primary-rgb), 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(var(--ev-primary-rgb), 0.05) 1px, transparent 1px);
  background-size: 60px 60px;
  mask-image: radial-gradient(ellipse 60% 60% at 50% 50%, black 20%, transparent 70%);
  -webkit-mask-image: radial-gradient(ellipse 60% 60% at 50% 50%, black 20%, transparent 70%);
}

.forgot-card {
  position: relative;
  z-index: 10;
  width: 480px;
  max-width: 94vw;
  padding: 40px 44px;
  background: var(--ev-bg-glass-strong);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: 24px;
  box-shadow: var(--ev-shadow-float), inset 0 1px 0 rgba(255, 255, 255, 0.9);

  .brand-badge {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 2px;
    color: var(--ev-primary);
    margin-bottom: 18px;

    .badge-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--ev-aqua);
      box-shadow: 0 0 10px rgba(var(--ev-aqua-rgb), 0.7);
    }
  }
}

@media (max-width: 768px) {
  .forgot-card {
    padding: 32px 22px;
  }
}
</style>
