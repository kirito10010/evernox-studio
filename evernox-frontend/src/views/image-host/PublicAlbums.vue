<template>
  <div class="public-albums">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-aurora"></div>
      <div class="header-content">
        <h1><el-icon><FolderOpened /></el-icon> 公开相册</h1>
        <p>探索所有用户创建的公开相册，发现精选照片合集</p>
      </div>
    </div>

    <!-- Album Grid -->
    <div v-if="albums.length > 0" class="album-grid">
      <div
        v-for="(album, index) in albums"
        :key="album.id"
        class="album-card"
        :style="{ animationDelay: `${index * 60}ms` }"
        @click="openAlbum(album)"
      >
        <div class="album-cover">
          <LazyImage
            v-if="album.coverImageId"
            :image-id="album.coverImageId"
            :loader="loadThumb"
            ratio="16 / 10"
            :alt="album.name"
            @loaded="onCoverLoaded(album.id, $event)"
          />
          <div v-else class="cover-placeholder">
            <el-icon :size="36"><Folder /></el-icon>
          </div>
          <div class="cover-overlay">
            <div class="cover-badge">{{ album.imageCount }} 张照片</div>
          </div>
        </div>
        <div class="album-info">
          <h3>{{ album.name }}</h3>
          <p v-if="album.description">{{ album.description }}</p>
          <div class="album-meta">
            <span>{{ album.creatorName }}</span>
            <span>{{ formatDate(album.createdAt) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else-if="!loading" class="empty-state">
      <el-icon :size="48"><FolderOpened /></el-icon>
      <p>暂无公开相册</p>
      <small>还没有人创建公开相册</small>
    </div>

    <!-- Loading Skeleton -->
    <div v-if="loading" class="album-grid">
      <div v-for="i in 6" :key="i" class="album-card skeleton">
        <div class="album-cover skeleton-img"></div>
        <div class="album-info">
          <div class="skeleton-line w60"></div>
          <div class="skeleton-line w80"></div>
          <div class="skeleton-line w40"></div>
        </div>
      </div>
    </div>

    <!-- Pagination -->
    <div class="pagination-wrap" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadAlbums"
      />
    </div>

    <!-- Album Detail Dialog -->
    <el-dialog
      v-model="albumDialogVisible"
      :title="selectedAlbum?.name"
      width="85vw"
      top="5vh"
      class="album-dialog"
    >
      <div class="album-detail" v-if="selectedAlbum">
        <div class="album-detail-header">
          <div>
            <h2>{{ selectedAlbum.name }}</h2>
            <p v-if="selectedAlbum.description">{{ selectedAlbum.description }}</p>
            <span class="detail-meta">by {{ selectedAlbum.creatorName }} · {{ selectedAlbum.imageCount }} 张照片</span>
          </div>
        </div>

        <div ref="albumGridRef" class="album-images-masonry">
          <div v-for="(col, colIdx) in albumColumns" :key="colIdx" class="masonry-col">
            <div
              v-for="img in col"
              :key="img.id"
              class="album-image-card"
              @click="openLightbox(img)"
            >
              <LazyImage
                :image-id="img.id"
                :loader="loadThumb"
                :ratio="aspectRatioOf(img)"
                :alt="img.originalName"
              />
              <div class="album-image-hover-actions">
                <el-button :icon="DocumentCopy" circle size="small" @click.stop="handleCopyUrl(img)" title="复制URL" />
              </div>
            </div>
          </div>
        </div>

        <div v-if="albumImages.length === 0 && !albumLoading" class="album-empty">
          <p>该相册暂无照片</p>
        </div>

        <div v-if="albumLoading" class="album-loading">
          <el-icon :size="28" class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>

        <div class="album-footer" v-if="albumImages.length > 0">
          <div v-if="albumHasMore" ref="albumSentinelRef" class="load-sentinel"></div>
          <div v-if="albumLoadingMore" class="footer-tip">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载中...</span>
          </div>
          <el-button v-else-if="albumHasMore && !supportsObserver" text @click="loadAlbumImages">加载更多</el-button>
          <span v-else-if="!albumHasMore" class="footer-tip">已全部加载</span>
        </div>
      </div>
    </el-dialog>

    <ImageLightbox v-model="lightboxVisible" :image="lightboxImage" :src="lightboxSrc" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { getPublicAlbums, getAlbumImages, getImageThumbnail } from '@/api/image'
import type { AlbumResponse, ImageResponse } from '@/api/image'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { useMasonry } from '@/composables/useMasonry'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import { ElMessage } from 'element-plus'
import { DocumentCopy } from '@element-plus/icons-vue'
import ImageLightbox from '@/components/ImageLightbox.vue'
import LazyImage from '@/components/LazyImage.vue'
import { aspectRatioOf } from '@/utils/image'

const { decryptImage, clearCache } = useImageDecrypt()
const { decryptImage: loadThumb, clearCache: clearThumbCache } = useImageDecrypt(getImageThumbnail)

const albums = ref<AlbumResponse[]>([])
const albumCoverUrls = reactive(new Map<number, string>())
const loading = ref(false)
const currentPage = ref(1)
const pageSize = 12
const total = ref(0)

const albumDialogVisible = ref(false)
const selectedAlbum = ref<AlbumResponse | null>(null)
const albumImages = ref<ImageResponse[]>([])
const albumDecryptedUrls = reactive(new Map<number, string>())
const albumLoading = ref(false)
const albumLoadingMore = ref(false)
/** 已加载批次数，下一次请求的页号是它 +1 */
const albumBatchIndex = ref(0)
const ALBUM_BATCH_SIZE = 48
const albumTotal = ref(0)
const albumHasMore = computed(() => albumImages.value.length < albumTotal.value)
const supportsObserver = typeof IntersectionObserver !== 'undefined'
/** 请求归属：切换相册时自增，迟到的旧响应据此丢弃 */
let albumToken = 0

// Masonry layout for album detail images
const ratioOf = (img: ImageResponse) => (img.width && img.height ? img.width / img.height : 1)
const { gridRef: albumGridRef, columns: albumColumns } = useMasonry(albumImages, ratioOf, { minColumnWidth: 240 })
const { sentinelRef: albumSentinelRef, recheck: albumRecheck } = useInfiniteScroll(() => void loadAlbumImages())

const lightboxVisible = ref(false)
const lightboxImage = ref<ImageResponse | null>(null)
const lightboxSrc = ref<string | null>(null)

const openLightbox = async (img: ImageResponse) => {
  lightboxImage.value = img
  lightboxSrc.value = albumDecryptedUrls.get(img.id) ?? null
  lightboxVisible.value = true
  if (!lightboxSrc.value) {
    const url = await decryptImage(img.id, img.iv, img.visibility)
    if (url) {
      albumDecryptedUrls.set(img.id, url)
      if (lightboxImage.value?.id === img.id) lightboxSrc.value = url
    }
  }
}

const onCoverLoaded = (albumId: number, payload: { id: number; url: string }) => {
  albumCoverUrls.set(albumId, payload.url)
}


onMounted(() => loadAlbums())
onUnmounted(() => {
  clearCache()
  clearThumbCache()
})

const loadAlbums = async () => {
  loading.value = true
  try {
    const res = await getPublicAlbums(currentPage.value, pageSize)
    if (res.data) {
      albums.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
}

const openAlbum = async (album: AlbumResponse) => {
  selectedAlbum.value = album
  albumDialogVisible.value = true
  albumBatchIndex.value = 0
  albumTotal.value = 0
  albumToken++
  albumImages.value = []
  albumDecryptedUrls.clear()
  await loadAlbumImages()
}

/** 加载相册内下一批照片并追加 */
const loadAlbumImages = async () => {
  if (!selectedAlbum.value) return
  if (albumLoading.value || albumLoadingMore.value) return
  const isFirstBatch = albumBatchIndex.value === 0
  if (!isFirstBatch && !albumHasMore.value) return

  const token = albumToken
  const albumId = selectedAlbum.value.id
  if (isFirstBatch) albumLoading.value = true
  else albumLoadingMore.value = true
  try {
    const res = await getAlbumImages(albumId, albumBatchIndex.value + 1, ALBUM_BATCH_SIZE)
    if (token !== albumToken) return
    if (res.data) {
      albumImages.value.push(...(res.data.records || []))
      albumTotal.value = res.data.total || 0
      albumBatchIndex.value++
    }
  } catch { /* ignore */ }
  finally {
    if (token === albumToken) {
      albumLoading.value = false
      albumLoadingMore.value = false
      await albumRecheck()
    }
  }
}

const formatDate = (dateStr: string): string => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
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
</script>

<style scoped lang="scss">
.public-albums {
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
  backdrop-filter: var(--ev-blur-md);
  -webkit-backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
  overflow: hidden;

  .header-aurora {
    position: absolute;
    inset: 0;
    background: var(--ev-grad-aurora-soft);
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

/* Album Grid */
.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.album-card {
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: 18px;
  backdrop-filter: var(--ev-blur-md);
  -webkit-backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.35s var(--ev-ease-out);
  animation: page-enter 0.5s var(--ev-ease-out) both;

  &:hover {
    transform: translateY(-3px);
    border-color: var(--ev-border-hover);
    box-shadow: var(--ev-shadow-lg);

    .album-cover img { transform: scale(1.05); }
    .cover-overlay { opacity: 1; }
  }
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .album-card { background: rgba(255, 255, 255, 0.92); }
}

.album-cover {
  position: relative;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  background: var(--ev-bg-tint);

  :deep(img) {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.4s var(--ev-ease-out);
  }
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ev-text-muted);
  background: linear-gradient(135deg, var(--ev-mist) 0%, #ffffff 100%);
  box-shadow: inset 0 0 0 1px var(--ev-border-default);
}

.cover-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  padding: 12px;
  opacity: 0;
  transition: opacity 0.3s ease;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 52px;
    background: rgba(255, 255, 255, 0.72);
    backdrop-filter: blur(10px);
    -webkit-backdrop-filter: blur(10px);
  }
}

.cover-badge {
  position: relative;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: var(--ev-radius-pill);
  background: var(--ev-bg-tint-strong);
  color: var(--ev-primary);
  border: 1px solid var(--ev-border-default);
}


.album-info {
  padding: 16px;

  h3 {
    font-size: 16px;
    font-weight: 600;
    color: var(--ev-text-primary);
    margin-bottom: 4px;
  }

  p {
    font-size: 13px;
    color: var(--ev-text-secondary);
    line-height: 1.5;
    margin-bottom: 8px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .album-meta {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
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
  color: var(--ev-text-muted);

  p { font-size: 16px; color: var(--ev-text-secondary); margin-top: 8px; }
  small { font-size: 13px; }
}

/* Skeleton */
.album-card.skeleton {
  pointer-events: none;
  .skeleton-img {
    aspect-ratio: 16 / 10;
    background: linear-gradient(90deg, var(--ev-bg-tint) 25%, var(--ev-bg-tint-strong) 50%, var(--ev-bg-tint) 75%);
    background-size: 200% 100%;
    animation: shimmer-sweep 1.5s infinite;
  }
  .skeleton-line {
    height: 12px;
    border-radius: 6px;
    background: var(--ev-bg-tint-strong);
    margin-bottom: 8px;
    &.w60 { width: 60%; }
    &.w80 { width: 80%; }
    &.w40 { width: 40%; }
  }
}

/* Pagination */
.pagination-wrap {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}

/* Album Dialog */
.album-dialog {
  :deep(.el-dialog) {
    background: var(--ev-bg-surface) !important;
    border: 1px solid var(--ev-border-subtle);
    border-radius: var(--ev-radius-xl) !important;
  }
  :deep(.el-dialog__body) {
    padding: 0;
  }
}

.album-detail {
  padding: 24px;
}

.album-detail-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--ev-border-subtle);

  h2 {
    font-size: 22px;
    font-weight: 700;
    color: var(--ev-text-primary);
    margin-bottom: 4px;
  }

  p {
    font-size: 14px;
    color: var(--ev-text-secondary);
    margin-bottom: 8px;
  }

  .detail-meta {
    font-size: 12px;
    color: var(--ev-text-muted);
  }
}

.album-images-masonry {
  display: flex;
  gap: 12px;
  max-height: 60vh;
  overflow-y: auto;
  overflow-x: hidden;
}

.masonry-col {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.album-image-card {
  position: relative;
  border-radius: var(--ev-radius-md);
  overflow: hidden;
  background: var(--ev-bg-tint);
  cursor: pointer;
  border: 1px solid var(--ev-border-subtle);
  transition: all 0.3s var(--ev-ease-out);

  &:hover {
    border-color: var(--ev-border-hover);
    transform: scale(1.02);

    .album-image-hover-actions { opacity: 1; }
  }
}


.album-image-hover-actions {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  gap: 6px;
  padding: 6px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  opacity: 0;
  transition: opacity 0.3s ease;
  z-index: 2;

  .el-button {
    width: 28px;
    height: 28px;
    color: var(--ev-primary) !important;
    background: var(--ev-bg-glass-strong) !important;
    border: 1px solid var(--ev-border-default) !important;
  }
}

.album-empty, .album-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 0;
  color: var(--ev-text-muted);
  font-size: 14px;
}

.album-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--ev-border-subtle);
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
