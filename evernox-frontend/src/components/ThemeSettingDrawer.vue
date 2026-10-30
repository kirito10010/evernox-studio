<template>
  <el-drawer v-model="visible" title="主题设置" size="320px" class="theme-drawer">
    <p class="theme-intro">选一套顺眼的配色，切换后立即生效，并记住你的选择。</p>

    <div class="theme-options">
      <div
        v-for="opt in options"
        :key="opt.name"
        class="theme-card"
        :class="{ 'is-active': themeStore.theme === opt.name }"
        @click="themeStore.setTheme(opt.name)"
      >
        <div class="theme-preview" :class="opt.name">
          <div class="preview-bar">
            <span class="preview-side"></span>
            <span class="preview-body"></span>
          </div>
          <div class="preview-lines">
            <span class="preview-line w60"></span>
            <span class="preview-line w80"></span>
            <span class="preview-line w40"></span>
          </div>
        </div>

        <div class="theme-info">
          <div class="theme-text">
            <span class="theme-name">{{ opt.label }}</span>
            <span class="theme-desc">{{ opt.desc }}</span>
          </div>
          <span class="theme-swatches">
            <i v-for="(color, i) in opt.swatches" :key="i" :style="{ background: color }"></i>
          </span>
          <el-icon v-if="themeStore.theme === opt.name" class="theme-check"><Check /></el-icon>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { useThemeStore, type ThemeName } from '@/stores/theme'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const themeStore = useThemeStore()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const options: {
  name: ThemeName
  label: string
  desc: string
  swatches: [string, string, string]
}[] = [
  { name: 'light', label: '晴空', desc: '碧蓝清透', swatches: ['#2f7cf6', '#4fc3e8', '#eef5fd'] },
  { name: 'dark', label: '墨夜', desc: '沉静深邃', swatches: ['#4a8df8', '#1a2434', '#0f1621'] },
  { name: 'celadon', label: '青瓷', desc: '茶青温润', swatches: ['#3d9c8a', '#6fb8ab', '#edf7f3'] },
  { name: 'lotus', label: '藕荷', desc: '淡紫轻柔', swatches: ['#9677ad', '#b9a3c9', '#f8f3f9'] },
]
</script>

<style scoped lang="scss">
.theme-intro {
  margin: 0 0 16px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ev-text-muted);
}

.theme-options {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.theme-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border-radius: var(--ev-radius-lg);
  border: 1px solid var(--ev-border-default);
  background: var(--ev-bg-glass);
  cursor: pointer;
  transition: border-color 0.2s var(--ev-ease-out), box-shadow 0.2s var(--ev-ease-out),
    transform 0.2s var(--ev-ease-out);

  &:hover {
    border-color: var(--ev-border-hover);
    transform: translateY(-1px);
    box-shadow: var(--ev-shadow-sm);
  }

  &.is-active {
    border-color: var(--ev-primary);
    box-shadow: 0 0 0 2px rgba(var(--ev-primary-rgb), 0.14), var(--ev-shadow-sm);

    .theme-name {
      color: var(--ev-primary);
    }
  }
}

.theme-preview {
  --pv-accent: #2f7cf6;
  height: 76px;
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  .preview-bar {
    flex: 0 0 20px;
    display: flex;

    .preview-side {
      width: 26%;
      flex-shrink: 0;
    }

    .preview-body {
      flex: 1;
    }
  }

  .preview-lines {
    flex: 1;
    padding: 10px 12px;
    display: flex;
    flex-direction: column;
    gap: 7px;

    .preview-line {
      height: 7px;
      border-radius: 4px;

      &.w60 {
        width: 60%;
        background: var(--pv-accent) !important;
        opacity: 0.85;
      }

      &.w80 {
        width: 80%;
      }

      &.w40 {
        width: 40%;
      }
    }
  }

  &.light {
    --pv-accent: #2f7cf6;
    background: #eef5fd;
    .preview-side { background: #ffffff; border-right: 1px solid #e3ecf7; }
    .preview-body { background: #ffffff; }
    .preview-line:not(.w60) { background: #dfe8f3; }
  }

  &.dark {
    --pv-accent: #4a8df8;
    background: #0f1621;
    .preview-side { background: #131c2a; border-right: 1px solid rgba(255, 255, 255, 0.08); }
    .preview-body { background: #1a2434; }
    .preview-line:not(.w60) { background: rgba(255, 255, 255, 0.12); }
  }

  &.celadon {
    --pv-accent: #3d9c8a;
    background: #edf7f3;
    .preview-side { background: #f6fcfa; border-right: 1px solid #dcefe9; }
    .preview-body { background: #ffffff; }
    .preview-line:not(.w60) { background: #d9ebe5; }
  }

  &.lotus {
    --pv-accent: #9677ad;
    background: #f8f3f9;
    .preview-side { background: #fcf8fd; border-right: 1px solid #ece1f0; }
    .preview-body { background: #ffffff; }
    .preview-line:not(.w60) { background: #e7dcec; }
  }
}

.theme-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.theme-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  flex: 1;

  .theme-name {
    font-size: 14px;
    font-weight: 600;
    color: var(--ev-text-primary);
  }

  .theme-desc {
    font-size: 11.5px;
    color: var(--ev-text-muted);
  }
}

.theme-swatches {
  display: inline-flex;
  gap: 4px;
  flex-shrink: 0;

  i {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    border: 1px solid rgba(255, 255, 255, 0.7);
    box-shadow: 0 0 0 1px var(--ev-border-subtle);
  }
}

.theme-check {
  flex-shrink: 0;
  color: var(--ev-primary);
}
</style>
