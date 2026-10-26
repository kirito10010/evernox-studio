<template>
  <div class="brand-panel" :class="`variant-${variant}`">
    <div class="brand-panel-inner">
      <div class="aurora-line line-1"></div>
      <div class="aurora-line line-2"></div>
      <div class="aurora-line line-3"></div>

      <div class="brand-top">
        <div class="brand-logo">
          <img src="@/assets/logo.svg" alt="EverNox" class="logo-img" />
        </div>
        <div class="brand-badge">
          <span class="badge-dot"></span>
          {{ badge }}
        </div>
      </div>

      <div class="brand-content">
        <h2>
          {{ title }}<br />
          <span class="aurora-text">{{ accent }}</span>
        </h2>
        <p>{{ description }}</p>
      </div>

      <div class="brand-features">
        <div v-for="feature in features" :key="feature.label" class="brand-feature">
          <span class="feature-icon">
            <el-icon :size="14"><component :is="iconMap[feature.icon]" /></el-icon>
          </span>
          {{ feature.label }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Cloudy, Key, Lock, Medal, PictureFilled, UploadFilled } from '@element-plus/icons-vue'

export interface AuthFeature {
  icon: 'lock' | 'upload' | 'cloud' | 'picture' | 'medal' | 'key'
  label: string
}

withDefaults(
  defineProps<{
    badge?: string
    title: string
    accent: string
    description: string
    features: AuthFeature[]
    variant?: 'blue' | 'aqua'
  }>(),
  {
    badge: 'EVERNOX STUDIO',
    variant: 'blue',
  }
)

const iconMap = {
  lock: Lock,
  upload: UploadFilled,
  cloud: Cloudy,
  picture: PictureFilled,
  medal: Medal,
  key: Key,
}
</script>

<style scoped lang="scss">
.brand-panel {
  flex: 1.15;
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
    background: linear-gradient(
      160deg,
      rgba(47, 124, 246, 0.1) 0%,
      rgba(79, 195, 232, 0.07) 50%,
      rgba(165, 228, 242, 0.12) 100%
    );
    z-index: 1;
  }

  &::after {
    content: '';
    position: absolute;
    right: 0;
    top: 0;
    bottom: 0;
    width: 1px;
    background: linear-gradient(
      180deg,
      transparent,
      rgba(47, 124, 246, 0.22),
      rgba(79, 195, 232, 0.22),
      transparent
    );
  }

  &.variant-aqua::before {
    background: linear-gradient(
      160deg,
      rgba(79, 195, 232, 0.1) 0%,
      rgba(47, 124, 246, 0.07) 50%,
      rgba(165, 228, 242, 0.12) 100%
    );
  }
}

.aurora-line {
  position: absolute;
  border-radius: 99px;
  opacity: 0.35;
  pointer-events: none;

  /* 装饰线只放在左右留白里，避免压到标题与正文 */
  &.line-1 {
    width: 92px;
    height: 2px;
    background: linear-gradient(90deg, transparent, #2f7cf6, transparent);
    top: 32%;
    left: -56px;
  }

  &.line-2 {
    width: 92px;
    height: 2px;
    background: linear-gradient(90deg, transparent, #4fc3e8, transparent);
    top: 46%;
    right: -56px;
  }

  &.line-3 {
    width: 76px;
    height: 2px;
    background: linear-gradient(90deg, transparent, #a5e4f2, transparent);
    bottom: 20%;
    left: -44px;
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

@media (max-width: 768px) {
  .brand-panel {
    display: none;
  }
}
</style>
