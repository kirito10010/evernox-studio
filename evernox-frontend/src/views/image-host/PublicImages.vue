<template>
  <div class="public-images">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-aurora"></div>
      <div class="header-content">
        <h1><el-icon><View /></el-icon> 公开图床</h1>
        <p>浏览所有用户分享的公开照片，发现精彩瞬间</p>
      </div>
    </div>

    <!-- Image Grid -->
    <div v-if="images.length > 0" class="masonry" ref="gridRef">
      <div class="masonry-col" v-for="(col, colIndex) in columns" :key="colIndex">
        <div
          v-for="img in col"
          :key="img.id"
          class="image-card"
          @click="openLightbox(img)"
        >
          <div class="card-image">
            <LazyImage
              :image-id="img.id"
              :loader="loadThumb"
              :ratio="aspectRatioOf(img)"
              :alt="img.originalName"
            />
            <div class="card-overlay">
              <el-icon :size="20"><ZoomIn /></el-icon>
            </div>
            <div class="card-hover-actions">
              <el-button :icon="DocumentCopy" circle size="small" @click.stop="handleCopyUrl(img)" title="复制URL" />
            </div>
          </div>
          <div class="card-meta">
            <span class="meta-name">{{ img.originalName }}</span>
            <div class="meta-bottom">
              <span class="meta-user">{{ img.uploaderName }}</span>
              <span class="meta-size">{{ formatSize(img.fileSize) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else-if="!loading" class="empty-state">
      <el-icon :size="48"><PictureFilled /></el-icon>
      <p>暂无公开图片</p>
      <small>成为第一个分享照片的人吧</small>
    </div>

    <!-- Loading Skeleton -->
    <div v-if="loading" class="masonry skeleton-grid">
      <div class="masonry-col" v-for="i in 4" :key="i">
        <div v-for="j in 3" :key="j" class="image-card skeleton">
          <div class="card-image skeleton-img" :style="{ aspectRatio: skeletonRatio(i + j) }"></div>
          <div class="card-meta">
            <div class="skeleton-line w70"></div>
            <div class="skeleton-line w40"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- Infinite scroll footer -->
    <div class="list-footer" v-if="images.length > 0">
      <div v-if="hasMore" ref="sentinelRef" class="load-sentinel"></div>
      <div v-if="loadingMore" class="footer-tip">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      <el-button v-else-if="hasMore && !supportsObserver" text @click="loadMore">加载更多</el-button>
      <span v-else-if="!hasMore" class="footer-tip">已全部加载</span>
    </div>

    <ImageLightbox v-model="lightboxVisible" :image="lightboxImage" :src="lightboxSrc" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { getPublicImages, getImageThumbnail } from '@/api/image'
import type { ImageResponse } from '@/api/image'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { useMasonry } from '@/composables/useMasonry'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import { ElMessage } from 'element-plus'
import { DocumentCopy, Loading } from '@element-plus/icons-vue'
import ImageLightbox from '@/components/ImageLightbox.vue'
import LazyImage from '@/components/LazyImage.vue'
import { aspectRatioOf, skeletonRatio } from '@/utils/image'

const { decryptImage, clearCache } = useImageDecrypt()
const { decryptImage: loadThumb, clearCache: clearThumbCache } = useImageDecrypt(getImageThumbnail)

const images = ref<ImageResponse[]>([])
const decryptedUrls = reactive(new Map<number, string>())
const loading = ref(false)
const loadingMore = ref(false)
/** 已加载批次数，下一次请求的页号是它 +1 */
const batchIndex = ref(0)
const BATCH_SIZE = 48
const total = ref(0)
const hasMore = computed(() => images.value.length < total.value)
const supportsObserver = typeof IntersectionObserver !== 'undefined'

const lightboxVisible = ref(false)
const lightboxImage = ref<ImageResponse | null>(null)
const lightboxSrc = ref<string | null>(null)

const openLightbox = async (img: ImageResponse) => {
  lightboxImage.value = img
  lightboxSrc.value = decryptedUrls.get(img.id) ?? null
  lightboxVisible.value = true
  if (!lightboxSrc.value) {
    const url = await decryptImage(img.id, img.iv, img.visibility)
    if (url) {
      decryptedUrls.set(img.id, url)
      if (lightboxImage.value?.id === img.id) lightboxSrc.value = url
    }
  }
}

// Masonry layout
const ratioOf = (img: ImageResponse) => (img.width && img.height ? img.width / img.height : 1)
const { gridRef, columns } = useMasonry(images, ratioOf, { minColumnWidth: 260 })

onMounted(() => loadMore())
onUnmounted(() => {
  clearCache()
  clearThumbCache()
})

const { sentinelRef, recheck } = useInfiniteScroll(() => void loadMore())

/** 加载下一批并追加；首批走骨架屏，后续批次走底部提示 */
const loadMore = async () => {
  if (loading.value || loadingMore.value) return
  const isFirstBatch = batchIndex.value === 0
  if (!isFirstBatch && !hasMore.value) return

  if (isFirstBatch) loading.value = true
  else loadingMore.value = true
  try {
    const res = await getPublicImages(batchIndex.value + 1, BATCH_SIZE)
    if (res.data) {
      images.value.push(...(res.data.records || []))
      total.value = res.data.total || 0
      batchIndex.value++
    }
  } catch { /* ignore */ }
  finally {
    loading.value = false
    loadingMore.value = false
    await recheck()
  }
}

const handleCopyUrl = async (img: ImageResponse) => {
  const url = `${window.location.origin}/api/image/${img.id}/file`
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制到剪贴板')
  } catch {
    const input = document.createElement('input')
    input.value = url
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
    ElMessage.success('链接已复制到剪贴板')
  }
}

const formatSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<style scoped lang="scss">
.public-images {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-header {
  position: relative;
  padding: 32px 36px;
  border-radius: var(--ev-radius-xl);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  background: rgba(255, 255, 255, 0.62);
  -webkit-backdrop-filter: var(--ev-blur-md);
  backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
  overflow: hidden;

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    background: rgba(255, 255, 255, 0.92);
  }

  .header-aurora {
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg,
      rgba(47, 124, 246, 0.10) 0%,
      rgba(79, 195, 232, 0.07) 50%,
      rgba(207, 230, 255, 0.35) 100%
    );
    pointer-events: none;
  }

  .header-content {
    position: relative;
    z-index: 1;

    h1 {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 22px;
      font-weight: 700;
      color: var(--ev-text-primary);
      margin-bottom: 6px;
    }

    p {
      font-size: 13px;
      color: var(--ev-text-secondary);
    }
  }
}

/* Masonry Layout */
.masonry {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.masonry-col {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.image-card {
  background: rgba(255, 255, 255, 0.66);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: var(--ev-radius-md);
  overflow: hidden;
  cursor: pointer;
  -webkit-backdrop-filter: var(--ev-blur-sm);
  backdrop-filter: var(--ev-blur-sm);
  box-shadow: var(--ev-shadow-sm), var(--ev-inset-gloss);
  transition: all 0.35s var(--ev-ease-out);

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    background: rgba(255, 255, 255, 0.92);
  }

  &:hover {
    transform: translateY(-3px);
    border-color: var(--ev-border-hover);
    box-shadow: var(--ev-shadow-card), var(--ev-glow-violet);

    .card-overlay { opacity: 1; }
  }
}

.card-image {
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, var(--ev-mist) 0%, #ffffff 100%);
  box-shadow: inset 0 0 0 1px var(--ev-border-default), var(--ev-shadow-xs);

  // 尺寸交给 LazyImage 的 aspect-ratio 容器，这里只做视觉修饰
  :deep(img) {
    transition: transform 0.4s var(--ev-ease-out);
  }

  &:hover :deep(img) {
    transform: scale(1.04);
  }
}

.card-overlay {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ev-primary);
  opacity: 0;
  transition: opacity 0.3s ease;
  -webkit-backdrop-filter: blur(2px);
  backdrop-filter: blur(2px);
}

.card-hover-actions {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  gap: 6px;
  padding: 8px;
  background: rgba(255, 255, 255, 0.72);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
  border-top: 1px solid var(--ev-border-subtle);
  color: var(--ev-text-secondary);
  opacity: 0;
  transition: opacity 0.3s ease;
  z-index: 2;

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    background: rgba(255, 255, 255, 0.92);
  }

  .el-button {
    width: 30px;
    height: 30px;
    background: var(--ev-bg-glass-strong) !important;
    border: 1px solid var(--ev-border-default) !important;
    color: var(--ev-text-primary) !important;
  }

  .image-card:hover & { opacity: 1; }
}

.card-meta {
  padding: 10px 12px;

  .meta-name {
    display: block;
    font-size: 13px;
    font-weight: 500;
    color: var(--ev-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    margin-bottom: 4px;
  }

  .meta-bottom {
    display: flex;
    justify-content: space-between;
    font-size: 11px;
    color: var(--ev-text-muted);
  }
}

/* Empty */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 80px 0;
  color: var(--ev-mist);

  p { font-size: 16px; color: var(--ev-text-secondary); margin-top: 8px; }
  small { font-size: 13px; color: var(--ev-text-muted); }
}

/* Skeleton */
.skeleton-grid {
  pointer-events: none;
}

.image-card.skeleton {
  pointer-events: none;
  .skeleton-img {
    background: linear-gradient(90deg, var(--ev-mist) 25%, #fff 37%, var(--ev-mist) 63%);
    background-size: 400% 100%;
    animation: ev-skeleton 1.4s ease infinite;
  }
  .skeleton-line {
    height: 12px;
    border-radius: 6px;
    background: linear-gradient(90deg, var(--ev-mist) 25%, #fff 37%, var(--ev-mist) 63%);
    background-size: 400% 100%;
    animation: ev-skeleton 1.4s ease infinite;
    margin-top: 6px;
    &.w70 { width: 70%; }
    &.w40 { width: 40%; }
  }
}

/* Infinite scroll footer */
.list-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 8px 0 16px;
}

.load-sentinel {
  width: 100%;
  height: 1px;
}

.footer-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--ev-text-muted);
}
</style>
