<template>
  <el-drawer v-model="visible" title="主题设置" size="300px" class="theme-drawer">
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
          <span class="theme-name">{{ opt.label }}</span>
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

const options: { name: ThemeName; label: string }[] = [
  { name: 'light', label: '浅色' },
  { name: 'dark', label: '深色' },
]
</script>

<style scoped lang="scss">
.theme-options {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.theme-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border-radius: var(--ev-radius-md);
  border: 1px solid var(--ev-border-default);
  background: var(--ev-bg-glass);
  cursor: pointer;
  transition: all 0.2s var(--ev-ease-out);

  &:hover {
    border-color: var(--ev-border-hover);
  }

  &.is-active {
    border-color: var(--ev-primary);
    box-shadow: 0 0 0 2px rgba(47, 124, 246, 0.16);
  }
}

.theme-preview {
  height: 92px;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  &.light {
    background: #eef5fd;
    .preview-side { background: #ffffff; border-right: 1px solid #e3ecf7; }
    .preview-body { background: #ffffff; }
    .preview-line { background: #dfe8f3; }
  }

  &.dark {
    background: #0f1621;
    .preview-side { background: #131c2a; border-right: 1px solid rgba(255, 255, 255, 0.08); }
    .preview-body { background: #1a2434; }
    .preview-line { background: rgba(255, 255, 255, 0.12); }
  }

  .preview-bar {
    flex: 0 0 22px;
    display: flex;

    .preview-side { width: 26%; flex-shrink: 0; }
    .preview-body { flex: 1; }
  }

  .preview-lines {
    flex: 1;
    padding: 12px;
    display: flex;
    flex-direction: column;
    gap: 8px;

    .preview-line {
      height: 8px;
      border-radius: 4px;
      &.w60 { width: 60%; }
      &.w80 { width: 80%; }
      &.w40 { width: 40%; }
    }
  }
}

.theme-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: var(--ev-text-primary);

  .theme-check {
    color: var(--ev-primary);
  }
}
</style>
