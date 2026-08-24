<template>
  <Teleport to="body">
    <Transition name="lightbox-fade">
      <div v-if="modelValue && image" class="lightbox-overlay" @click.self="close">
        <div class="lightbox-topbar">
          <div class="topbar-info">
            <h3 class="lightbox-title" :title="image.originalName">{{ image.originalName }}</h3>
            <div class="lightbox-meta">
              <span class="meta-item">
                <el-icon><User /></el-icon>{{ image.uploaderName || '未知' }}
              </span>
              <span class="meta-item">
                <el-icon><Clock /></el-icon>{{ formatDate(image.createdAt) }}
              </span>
              <span class="meta-item">
                <el-icon><Document /></el-icon>{{ formatSize(image.fileSize) }}
              </span>
              <span v-if="image.width" class="meta-item">
                <el-icon><FullScreen /></el-icon>{{ image.width }} × {{ image.height }}
              </span>
              <span class="visibility-tag" :class="image.visibility === 1 ? 'public' : 'private'">
                {{ image.visibility === 1 ? '公开' : '私密' }}
              </span>
            </div>
          </div>

          <div class="lightbox-actions">
            <el-button
              :icon="DocumentCopy"
              circle
              size="small"
              title="复制URL"
              @click="handleCopyUrl"
            />
            <el-button :icon="Close" circle size="small" title="关闭" @click="close" />
          </div>
        </div>

        <div class="lightbox-stage" @click.self="close">
          <img v-if="src" :src="src" :alt="image.originalName" class="lightbox-img" />
          <div v-else class="lightbox-placeholder">
            <el-icon :size="40"><Picture /></el-icon>
            <span>图片加载中...</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { watch, onUnmounted } from 'vue'
import {
  Close,
  Clock,
  Document,
  DocumentCopy,
  FullScreen,
  Picture,
  User,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { ImageResponse } from '@/api/image'

const props = defineProps<{
  modelValue: boolean
  image: ImageResponse | null
  src?: string | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const close = () => emit('update:modelValue', false)

const onKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') close()
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      document.addEventListener('keydown', onKeydown)
      document.body.style.overflow = 'hidden'
    } else {
      document.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = ''
    }
  }
)

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})

const handleCopyUrl = async () => {
  if (!props.image) return
  const url = `${window.location.origin}/api/image/${props.image.id}/file`
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('图片URL已复制')
  } catch {
    // 非 HTTPS 或无剪贴板权限时降级
    const input = document.createElement('input')
    input.value = url
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
    ElMessage.success('图片URL已复制')
  }
}

const formatDate = (dateStr?: string): string => {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate()
  ).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(
    2,
    '0'
  )}`
}

const formatSize = (bytes?: number): string => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}
</script>

<style scoped lang="scss">
.lightbox-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  background: rgba(18, 48, 79, 0.42);
  backdrop-filter: blur(14px) saturate(150%);
  -webkit-backdrop-filter: blur(14px) saturate(150%);

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    background: rgba(18, 48, 79, 0.66);
  }
}

.lightbox-topbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 18px;
  background: rgba(255, 255, 255, 0.7);
  border-bottom: 1px solid var(--ev-border-subtle);
  backdrop-filter: var(--ev-blur-md);
  -webkit-backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-inset-gloss);

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    background: rgba(255, 255, 255, 0.95);
  }
}

.topbar-info {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 16px;
  min-width: 0;
}

.lightbox-actions {
  flex-shrink: 0;
  display: flex;
  gap: 10px;

  :deep(.el-button.is-circle) {
    background: rgba(255, 255, 255, 0.82) !important;
    border: 1px solid var(--ev-border-default) !important;
    color: var(--ev-text-secondary) !important;
    box-shadow: var(--ev-shadow-sm), inset 0 1px 0 rgba(255, 255, 255, 0.9) !important;

    &:hover {
      color: var(--ev-primary) !important;
      border-color: var(--ev-border-hover) !important;
      background: #ffffff !important;
    }
  }
}

.lightbox-stage {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  padding: 12px 20px 20px;
}

.lightbox-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: var(--ev-radius-md);
  box-shadow: 0 24px 60px -20px rgba(18, 48, 79, 0.45);
  background: linear-gradient(135deg, var(--ev-mist) 0%, #ffffff 100%);
}

.lightbox-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 80px;
  border-radius: var(--ev-radius-lg);
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid var(--ev-border-subtle);
  color: var(--ev-text-muted);
  font-size: 13px;
}

.lightbox-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--ev-text-primary);
  margin: 0;
  max-width: 34vw;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lightbox-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 16px;
  font-size: 12px;
  color: var(--ev-text-secondary);

  .meta-item {
    display: inline-flex;
    align-items: center;
    gap: 5px;
  }
}

.visibility-tag {
  padding: 2px 12px;
  border-radius: var(--ev-radius-pill);
  font-size: 11px;
  font-weight: 600;
  border: 1px solid var(--ev-border-default);

  &.public {
    background: var(--ev-bg-tint-strong);
    color: var(--ev-primary);
  }

  &.private {
    background: rgba(242, 99, 127, 0.12);
    color: var(--ev-danger);
    border-color: rgba(242, 99, 127, 0.24);
  }
}

.lightbox-fade-enter-active,
.lightbox-fade-leave-active {
  transition: opacity 0.25s var(--ev-ease-out);
}

.lightbox-fade-enter-from,
.lightbox-fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .lightbox-topbar {
    padding: 8px 12px;
    gap: 10px;
  }

  .lightbox-title {
    max-width: 60vw;
    font-size: 13px;
  }

  .lightbox-meta {
    font-size: 11px;
    gap: 4px 10px;
  }

  .lightbox-stage {
    padding: 10px 12px 14px;
  }
}
</style>
