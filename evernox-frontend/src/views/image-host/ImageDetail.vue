<template>
  <div class="image-detail">
    <!-- Loading -->
    <div v-if="loading" class="detail-loading">
      <el-icon :size="36" class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="detail-error">
      <el-icon :size="48"><WarningFilled /></el-icon>
      <h2>{{ error }}</h2>
      <p>该图片不存在或您没有权限查看</p>
      <el-button type="primary" @click="$router.push('/image-host')">
        返回首页
      </el-button>
    </div>

    <!-- Content -->
    <template v-else-if="image">
      <!-- Back Button -->
      <div class="detail-nav">
        <el-button text @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <div class="nav-actions">
          <el-button :icon="DocumentCopy" circle size="small" @click="handleCopyUrl" title="复制URL" />
          <template v-if="isOwner">
            <el-button :icon="image.visibility === 1 ? Hide : View" circle size="small" @click="handleToggleVisibility" :title="image.visibility === 1 ? '设为私密' : '设为公开'" />
            <el-button type="danger" :icon="Delete" circle size="small" @click="handleDelete" title="删除" />
          </template>
        </div>
      </div>

      <!-- Image Display -->
      <div class="detail-image-section">
        <div class="image-container" v-if="decryptedUrl">
          <img :src="decryptedUrl" :alt="image.originalName" />
        </div>
        <div class="image-container image-loading" v-else-if="decrypting">
          <el-icon :size="40" class="is-loading"><Loading /></el-icon>
          <span>正在解密图片...</span>
        </div>
        <div class="image-container image-encrypted" v-else>
          <el-icon :size="48"><Lock /></el-icon>
          <span>该图片已加密，需要登录后查看</span>
          <el-button type="primary" @click="$router.push('/login')">登录查看</el-button>
        </div>
      </div>

      <!-- Info Layout -->
      <div class="detail-bottom">
        <!-- Image Info -->
        <div class="detail-info">
          <div class="info-header">
            <h1 class="image-title">{{ image.originalName }}</h1>
            <div class="image-meta-row">
              <span class="meta-item">
                <el-icon><User /></el-icon>
                {{ image.uploaderName }}
              </span>
              <span class="meta-item">
                <el-icon><Clock /></el-icon>
                {{ formatDate(image.createdAt) }}
              </span>
              <span class="meta-item">
                <el-icon><Document /></el-icon>
                {{ formatSize(image.fileSize) }}
              </span>
              <span class="meta-item" v-if="image.width">
                <el-icon><FullScreen /></el-icon>
                {{ image.width }} × {{ image.height }}
              </span>
              <span class="visibility-tag" :class="image.visibility === 1 ? 'public' : 'private'">
                {{ image.visibility === 1 ? '公开' : '私密' }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getImageById, deleteImage, updateImageVisibility } from '@/api/image'
import type { ImageResponse } from '@/api/image'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Loading, WarningFilled, ArrowLeft, DocumentCopy, View, Hide,
  Delete, Lock, User, Clock, Document, FullScreen
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { decryptImage, clearCache } = useImageDecrypt()

const image = ref<ImageResponse | null>(null)
const decryptedUrl = ref<string | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const decrypting = ref(false)

const isOwner = computed(() => {
  return image.value && userStore.userInfo?.id === image.value.userId
})

onMounted(async () => {
  const imageId = Number(route.params.id)
  if (!imageId) {
    error.value = '无效的图片链接'
    loading.value = false
    return
  }

  try {
    const res = await getImageById(imageId)
    if (res.data) {
      image.value = res.data
      const vis = res.data.visibility

      if (vis === 1) {
        // 公开图片：无需登录
        decrypting.value = true
        const url = await decryptImage(imageId, res.data.iv, 1)
        if (url) decryptedUrl.value = url
        decrypting.value = false
      } else if (userStore.isLoggedIn) {
        // 私密图片：需要登录，权限由服务端校验
        decrypting.value = true
        const url = await decryptImage(imageId, res.data.iv, 0)
        if (url) decryptedUrl.value = url
        decrypting.value = false
      }
      // else: 私密图片且未登录 → 显示占位
    } else {
      error.value = '图片不存在'
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message || '加载失败'
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  clearCache()
})

const handleCopyUrl = async () => {
  if (!image.value) return
  const url = `${window.location.origin}/api/image/${image.value.id}/file`
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制')
  } catch {
    const input = document.createElement('input')
    input.value = url
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
    ElMessage.success('链接已复制')
  }
}

const handleToggleVisibility = async () => {
  if (!image.value) return
  const newVis = image.value.visibility === 1 ? 0 : 1
  try {
    await updateImageVisibility(image.value.id, newVis)
    image.value.visibility = newVis
    ElMessage.success(newVis === 1 ? '已设为公开' : '已设为私密')
  } catch { /* ignore */ }
}

const handleDelete = async () => {
  if (!image.value) return
  try {
    await ElMessageBox.confirm('确定要删除这张图片吗？此操作不可恢复。', '确认删除', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
    await deleteImage(image.value.id)
    ElMessage.success('删除成功')
    router.push('/image-host/my-images')
  } catch { /* cancelled */ }
}

const formatSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const formatDate = (dateStr: string): string => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped lang="scss">
.image-detail {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 0;
  background: transparent;
}

/* Loading & Error */
.detail-loading, .detail-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 120px 0;
  color: var(--ev-text-muted);

  h2 { color: var(--ev-text-primary); font-size: 20px; font-weight: 600; }
  p { color: var(--ev-text-secondary); font-size: 14px; }
}

.detail-loading .el-icon { color: var(--ev-primary); }
.detail-error .el-icon { color: var(--ev-warning); }

/* Nav */
.detail-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .nav-actions {
    display: flex;
    gap: 6px;
  }

  :deep(.el-button.is-circle) {
    background: rgba(255, 255, 255, 0.6);
    border: 1px solid var(--ev-border-default);
    color: var(--ev-text-secondary);
    box-shadow: var(--ev-shadow-xs), inset 0 1px 0 rgba(255, 255, 255, 0.9);
    transition: all 0.2s var(--ev-ease-out);

    &:hover {
      color: var(--ev-primary);
      border-color: var(--ev-border-hover);
    }
  }

  :deep(.el-button.is-circle.el-button--danger) {
    color: var(--ev-danger);

    &:hover {
      color: var(--ev-danger);
      border-color: var(--ev-danger);
    }
  }

  :deep(.el-button.is-text) {
    color: var(--ev-text-secondary);

    &:hover {
      color: var(--ev-primary);
      background: var(--ev-bg-tint);
    }
  }
}

/* Image Section */
.detail-image-section {
  margin-bottom: 32px;
}

.image-container {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f7fbff;
  background-image:
    linear-gradient(45deg, var(--ev-mist) 25%, transparent 25%, transparent 75%, var(--ev-mist) 75%),
    linear-gradient(45deg, var(--ev-mist) 25%, transparent 25%, transparent 75%, var(--ev-mist) 75%);
  background-size: 20px 20px;
  background-position: 0 0, 10px 10px;
  border-radius: var(--ev-radius-xl);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
  overflow: hidden;
  min-height: 300px;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: rgba(18, 48, 79, 0.1);
    backdrop-filter: blur(8px);
    -webkit-backdrop-filter: blur(8px);
    pointer-events: none;
  }

  img {
    position: relative;
    z-index: 1;
    max-width: 100%;
    max-height: 75vh;
    display: block;
    border-radius: var(--ev-radius-xl);
  }
}

.image-loading {
  flex-direction: column;
  gap: 12px;
  color: var(--ev-text-secondary);
  font-size: 14px;
  min-height: 400px;

  > * { position: relative; z-index: 1; }
}

.image-encrypted {
  flex-direction: column;
  gap: 16px;
  color: var(--ev-text-secondary);
  font-size: 14px;
  min-height: 300px;

  > * { position: relative; z-index: 1; }
}

/* Bottom Layout */
.detail-bottom {
  display: grid;
  grid-template-columns: 1fr;
  gap: 32px;
}

/* Info */
.detail-info {
  .info-header {
    background: rgba(255, 255, 255, 0.66);
    border: 1px solid var(--ev-border-subtle);
    border-top-color: var(--ev-border-gloss);
    border-radius: 18px;
    padding: 24px;
    backdrop-filter: var(--ev-blur-md);
    -webkit-backdrop-filter: var(--ev-blur-md);
    box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);

    @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
      background: rgba(255, 255, 255, 0.92);
    }
  }

  .image-title {
    font-size: 18px;
    font-weight: 700;
    color: var(--ev-text-primary);
    margin-bottom: 16px;
    word-break: break-all;
  }

  .image-meta-row {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    align-items: center;
  }

  .meta-item {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    color: var(--ev-text-secondary);

    .el-icon { font-size: 14px; color: var(--ev-text-muted); }
  }

  .visibility-tag {
    font-size: 11px;
    font-weight: 600;
    padding: 3px 10px;
    border-radius: 10px;

    &.public {
      background: var(--ev-bg-tint-strong);
      color: var(--ev-primary);
      border: 1px solid var(--ev-border-subtle);
    }
    &.private {
      background: rgba(242, 99, 127, 0.12);
      color: var(--ev-danger);
      border: 1px solid rgba(242, 99, 127, 0.24);
    }
  }
}

</style>
