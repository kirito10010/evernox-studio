<template>
  <div class="my-images">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-aurora"></div>
      <div class="header-content">
        <div class="header-left">
          <h1><el-icon><PictureFilled /></el-icon> 我的图床</h1>
          <p>管理您的私人加密照片库</p>
        </div>
        <el-button type="primary" @click="showUpload = true">
          <el-icon><Upload /></el-icon>
          上传图片
        </el-button>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="toolbar">
      <div class="toolbar-left">
        <span class="result-count">共 <strong>{{ total }}</strong> 张图片</span>
      </div>
      <div class="toolbar-right">
        <el-button
          :icon="isGrid ? Grid : List"
          @click="isGrid = !isGrid"
          circle
        />
      </div>
    </div>

    <!-- Grid View (Masonry) -->
    <div v-if="isGrid && images.length > 0" class="masonry" ref="gridRef">
      <div class="masonry-col" v-for="(col, colIndex) in columns" :key="colIndex">
        <div
          v-for="img in col"
          :key="img.id"
          class="image-card"
        >
          <div class="card-image">
            <div class="card-click-area" @click="openLightbox(img)">
              <LazyImage
                :image-id="img.id"
                :loader="loadThumb"
                :ratio="aspectRatioOf(img)"
                :alt="img.originalName"
              />
              <div class="card-overlay">
                <el-icon :size="20"><ZoomIn /></el-icon>
              </div>
            </div>
            <div class="visibility-badge" :class="img.visibility === 1 ? 'public' : 'private'" @click.stop="handleToggleVisibility(img)">
              {{ img.visibility === 1 ? '公开' : '私密' }}
            </div>
            <div class="card-hover-actions">
              <el-button :icon="DocumentCopy" circle size="small" @click.stop="handleCopyUrl(img)" title="复制URL" />
              <el-button :icon="img.visibility === 1 ? Hide : View" circle size="small" @click.stop="handleToggleVisibility(img)" :title="img.visibility === 1 ? '设为私密' : '设为公开'" />
              <el-button :icon="FolderAdd" circle size="small" @click.stop="openAlbumManage(img)" title="管理相册" />
            </div>
          </div>
          <div class="card-actions">
            <div class="action-info">
              <span class="action-name" :title="img.originalName">{{ img.originalName }}</span>
              <span class="action-meta">{{ formatSize(img.fileSize) }}</span>
            </div>
            <el-button
              type="danger"
              :icon="Delete"
              size="small"
              circle
              @click="handleDelete(img)"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- List View -->
    <div v-else-if="!isGrid" class="image-list">
      <div
        v-for="img in images"
        :key="img.id"
        class="list-item"
      >
        <div class="list-thumb" @click="openLightbox(img)">
          <LazyImage
            :image-id="img.id"
            :loader="loadThumb"
            ratio="1 / 1"
            :alt="img.originalName"
          />
        </div>
        <div class="list-info">
          <span class="list-name">{{ img.originalName }}</span>
          <span class="list-meta">{{ img.mimeType }} · {{ formatSize(img.fileSize) }}</span>
        </div>
        <div class="list-badges">
          <span class="vis-badge" :class="img.visibility === 1 ? 'public' : 'private'">
            {{ img.visibility === 1 ? '公开' : '私密' }}
          </span>
        </div>
        <div class="list-date">{{ formatDate(img.createdAt) }}</div>
        <div class="list-actions">
          <el-button :icon="DocumentCopy" size="small" circle @click="handleCopyUrl(img)" title="复制URL" />
          <el-button :icon="img.visibility === 1 ? Hide : View" size="small" circle @click="handleToggleVisibility(img)" :title="img.visibility === 1 ? '设为私密' : '设为公开'" />
          <el-button :icon="FolderAdd" size="small" circle @click="openAlbumManage(img)" title="管理相册" />
          <el-button
            type="danger"
            :icon="Delete"
            size="small"
            circle
            @click="handleDelete(img)"
          />
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="images.length === 0 && !loading" class="empty-state">
      <el-icon :size="48"><PictureFilled /></el-icon>
      <p>还没有上传图片</p>
      <el-button type="primary" @click="showUpload = true">
        <el-icon><Upload /></el-icon>
        立即上传
      </el-button>
    </div>

    <!-- Loading Skeleton -->
    <div v-if="loading" class="masonry skeleton-grid">
      <div class="masonry-col" v-for="i in 4" :key="i">
        <div v-for="j in 3" :key="j" class="image-card skeleton">
          <div class="card-image skeleton-img" :style="{ aspectRatio: skeletonRatio(i + j) }"></div>
          <div class="card-actions">
            <div class="skeleton-line w70"></div>
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

    <UploadModal v-model="showUpload" @uploaded="onUploaded" />

    <!-- Album Management Dialog -->
    <el-dialog
      v-model="albumManageVisible"
      title="管理相册"
      width="420px"
      class="album-manage-dialog"
    >
      <div v-if="albumManageLoading" class="album-manage-loading">
        <el-icon :size="24" class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      <div v-else-if="allAlbums.length === 0" class="album-manage-empty">
        <p>还没有创建相册</p>
        <small>请先在「我的相册」中创建相册</small>
      </div>
      <div v-else class="album-checkbox-list">
        <label
          v-for="album in allAlbums"
          :key="album.id"
          class="album-checkbox-item"
          :class="{ active: managedAlbumIds.has(album.id) }"
        >
          <el-checkbox
            :model-value="managedAlbumIds.has(album.id)"
            @change="(val: any) => toggleAlbumForImage(album.id, val === true)"
          />
          <div class="album-item-info">
            <span class="album-item-name">{{ album.name }}</span>
            <span class="album-item-meta">{{ album.imageCount }} 张照片 · {{ album.visibility === 1 ? '公开' : '私密' }}</span>
          </div>
        </label>
      </div>
      <template #footer>
        <el-button type="primary" @click="albumManageVisible = false">完成</el-button>
      </template>
    </el-dialog>

    <ImageLightbox v-model="lightboxVisible" :image="lightboxImage" :src="lightboxSrc" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import {
  getMyImages, deleteImage, updateImageVisibility,
  getMyAlbums, addImageToAlbum, removeImageFromAlbum, getImageAlbums,
  getImageThumbnail
} from '@/api/image'
import type { ImageResponse, AlbumResponse } from '@/api/image'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { useMasonry } from '@/composables/useMasonry'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Grid, List, DocumentCopy, View, Hide, FolderAdd, ZoomIn, PictureFilled, Upload, Loading } from '@element-plus/icons-vue'
import UploadModal from '@/components/UploadModal.vue'
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
const isGrid = ref(true)
const showUpload = ref(false)

const lightboxVisible = ref(false)
const lightboxImage = ref<ImageResponse | null>(null)
const lightboxSrc = ref<string | null>(null)

const openLightbox = async (img: ImageResponse) => {
  lightboxImage.value = img
  lightboxSrc.value = decryptedUrls.get(img.id) ?? null
  lightboxVisible.value = true
  // 用户点了还没加载完的占位：主动补一次（命中 inflight 去重）
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

onMounted(() => resetAndLoad())
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
    const res = await getMyImages(batchIndex.value + 1, BATCH_SIZE)
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

/** 上传后列表顺序会变，从第一批重新拉 */
const resetAndLoad = async () => {
  batchIndex.value = 0
  images.value = []
  total.value = 0
  await loadMore()
}

const handleDelete = async (img: ImageResponse) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${img.originalName}」吗？此操作不可恢复。`,
      '确认删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await deleteImage(img.id)
    ElMessage.success('删除成功')
    // Remove from local state
    const idx = images.value.findIndex(i => i.id === img.id)
    if (idx !== -1) images.value.splice(idx, 1)
    total.value--
    // Cleanup URL
    if (decryptedUrls.has(img.id)) {
      URL.revokeObjectURL(decryptedUrls.get(img.id)!)
      decryptedUrls.delete(img.id)
    }
  } catch { /* cancelled */ }
}

const onUploaded = () => {
  void resetAndLoad()
}

// ========== Copy URL ==========
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

// ========== Toggle Visibility ==========
const handleToggleVisibility = async (img: ImageResponse) => {
  const newVis = img.visibility === 1 ? 0 : 1
  try {
    await updateImageVisibility(img.id, newVis)
    img.visibility = newVis
    ElMessage.success(newVis === 1 ? '已设为公开' : '已设为私密')
  } catch { /* ignore */ }
}

// ========== Album Management ==========
const albumManageVisible = ref(false)
const albumManageLoading = ref(false)
const managingImage = ref<ImageResponse | null>(null)
const allAlbums = ref<AlbumResponse[]>([])
const managedAlbumIds = reactive(new Set<number>())

const openAlbumManage = async (img: ImageResponse) => {
  managingImage.value = img
  albumManageVisible.value = true
  albumManageLoading.value = true
  managedAlbumIds.clear()

  try {
    const [albumsRes, imageAlbumsRes] = await Promise.all([
      getMyAlbums(1, 100),
      getImageAlbums(img.id),
    ])
    if (albumsRes.data) {
      allAlbums.value = albumsRes.data.records || []
    }
    if (imageAlbumsRes.data) {
      imageAlbumsRes.data.forEach(id => managedAlbumIds.add(id))
    }
  } catch { /* ignore */ }
  finally { albumManageLoading.value = false }
}

const toggleAlbumForImage = async (albumId: number, addToAlbum: boolean) => {
  if (!managingImage.value) return
  try {
    if (addToAlbum) {
      await addImageToAlbum(albumId, managingImage.value.id)
      managedAlbumIds.add(albumId)
    } else {
      await removeImageFromAlbum(albumId, managingImage.value.id)
      managedAlbumIds.delete(albumId)
    }
    const album = allAlbums.value.find(a => a.id === albumId)
    if (album) {
      album.imageCount += addToAlbum ? 1 : -1
    }
  } catch { /* ignore */ }
}

const formatSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const formatDate = (dateStr: string): string => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
</script>

<style scoped lang="scss">
.my-images {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  position: relative;
  padding: 28px 36px;
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
      rgba(79, 195, 232, 0.06) 50%,
      rgba(207, 230, 255, 0.35) 100%
    );
    pointer-events: none;
  }

  .header-content {
    position: relative;
    z-index: 1;
    display: flex;
    justify-content: space-between;
    align-items: center;

    h1 {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 22px;
      font-weight: 700;
      color: var(--ev-text-primary);
      margin-bottom: 4px;
    }

    p {
      font-size: 13px;
      color: var(--ev-text-secondary);
    }
  }
}

/* Toolbar */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-radius: 16px;
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  background: rgba(255, 255, 255, 0.62);
  -webkit-backdrop-filter: var(--ev-blur-md);
  backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    background: rgba(255, 255, 255, 0.92);
  }

  .result-count {
    font-size: 13px;
    color: var(--ev-text-secondary);
    strong { color: var(--ev-text-primary); }
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

  &.selected {
    box-shadow: 0 0 0 2px var(--ev-primary), var(--ev-glow-violet);
    border-color: var(--ev-primary);
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

  &:hover :deep(img) { transform: scale(1.04); }
}

.select-check-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ev-grad-aurora);
  color: var(--ev-text-on-accent);
  box-shadow: var(--ev-shadow-xs);
  z-index: 3;
}

.card-click-area {
  width: 100%;
  cursor: pointer;
  position: relative;
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

.card-overlay {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.45);
  -webkit-backdrop-filter: blur(2px);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ev-primary);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.visibility-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
  -webkit-backdrop-filter: blur(8px);
  backdrop-filter: blur(8px);
  cursor: pointer;
  z-index: 3;
  transition: all 0.2s ease;

  &:hover {
    transform: scale(1.1);
  }

  &.public {
    background: rgba(47, 124, 246, 0.14);
    color: var(--ev-primary);
    border: 1px solid rgba(47, 124, 246, 0.3);
  }
  &.private {
    background: rgba(242, 99, 127, 0.14);
    color: var(--ev-danger);
    border: 1px solid rgba(242, 99, 127, 0.3);
  }
}

.card-actions {
  padding: 10px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;

  .action-info {
    flex: 1;
    min-width: 0;
  }

  .action-name {
    display: block;
    font-size: 13px;
    font-weight: 500;
    color: var(--ev-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .action-meta {
    font-size: 11px;
    color: var(--ev-text-muted);
  }
}

/* List View */
.image-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.list-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: var(--ev-radius-md);
  -webkit-backdrop-filter: var(--ev-blur-sm);
  backdrop-filter: var(--ev-blur-sm);
  box-shadow: var(--ev-shadow-xs);
  transition: all 0.25s var(--ev-ease-out);

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    background: rgba(255, 255, 255, 0.92);
  }

  & + & {
    border-top: 1px solid var(--ev-border-subtle);
  }

  &:hover {
    border-color: var(--ev-border-hover);
    background: var(--ev-bg-tint);
  }
}

.list-thumb {
  width: 48px;
  height: 48px;
  border-radius: var(--ev-radius-sm);
  overflow: hidden;
  flex-shrink: 0;
  cursor: pointer;
  background: linear-gradient(135deg, var(--ev-mist) 0%, #ffffff 100%);
  box-shadow: inset 0 0 0 1px var(--ev-border-default);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ev-text-muted);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.list-info {
  flex: 1;
  min-width: 0;

  .list-name {
    display: block;
    font-size: 14px;
    font-weight: 500;
    color: var(--ev-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .list-meta {
    font-size: 12px;
    color: var(--ev-text-muted);
  }
}

.list-badges {
  flex-shrink: 0;
}

.vis-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 10px;

  &.public {
    background: rgba(47, 124, 246, 0.12);
    color: var(--ev-primary);
  }
  &.private {
    background: rgba(242, 99, 127, 0.12);
    color: var(--ev-danger);
  }
}

.list-date {
  font-size: 13px;
  color: var(--ev-text-muted);
  flex-shrink: 0;
  min-width: 90px;
}

/* Empty */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 80px 0;
  color: var(--ev-mist);

  p { font-size: 16px; color: var(--ev-text-secondary); margin: 4px 0; }
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
    &.w70 { width: 70%; }
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

/* List Actions */
.list-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

/* Album Manage Dialog */
.album-manage-dialog {
  :deep(.el-dialog) {
    background: var(--ev-bg-surface) !important;
    border: 1px solid var(--ev-border-subtle);
    border-radius: var(--ev-radius-xl) !important;
  }
}

.album-manage-loading, .album-manage-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: var(--ev-text-muted);
  font-size: 14px;
}

.album-checkbox-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 400px;
  overflow-y: auto;
}

.album-checkbox-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: var(--ev-radius-md);
  cursor: pointer;
  transition: background 0.2s ease;
  border: 1px solid transparent;

  &:hover {
    background: var(--ev-bg-tint);
  }

  &.active {
    background: var(--ev-bg-tint-strong);
    border-color: var(--ev-border-active);
  }

  .album-item-info {
    flex: 1;
    min-width: 0;
  }

  .album-item-name {
    display: block;
    font-size: 14px;
    font-weight: 500;
    color: var(--ev-text-primary);
  }

  .album-item-meta {
    font-size: 12px;
    color: var(--ev-text-muted);
  }
}
</style>
