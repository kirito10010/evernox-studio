<template>
  <div class="my-albums">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-aurora"></div>
      <div class="header-content">
        <div class="header-left">
          <h1><el-icon><Folder /></el-icon> 我的相册</h1>
          <p>创建和管理您的照片相册分类</p>
        </div>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          新建相册
        </el-button>
      </div>
    </div>

    <!-- Album Grid -->
    <div v-if="albums.length > 0" class="album-grid">
      <div
        v-for="(album, index) in albums"
        :key="album.id"
        class="album-card"
        :style="{ animationDelay: `${index * 60}ms` }"
      >
        <div class="album-cover" @click="openAlbumDetail(album)">
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
          <div class="cover-vis-badge" :class="album.visibility === 1 ? 'public' : 'private'" @click.stop="handleToggleAlbumVisibility(album)">
            {{ album.visibility === 1 ? '公开' : '私密' }}
          </div>
        </div>
        <div class="album-body">
          <div class="album-text">
            <h3>{{ album.name }}</h3>
            <p v-if="album.description">{{ album.description }}</p>
          </div>
          <div class="album-footer">
            <span class="album-date">{{ formatDate(album.createdAt) }}</span>
            <div class="album-btns">
              <el-button size="small" @click="openCoverCropper(album)" title="上传封面">
                <el-icon><PictureRounded /></el-icon>
              </el-button>
              <el-button size="small" @click="openEditDialog(album)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button size="small" type="danger" @click="handleDelete(album)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else-if="!loading" class="empty-state">
      <el-icon :size="48"><FolderOpened /></el-icon>
      <p>还没有创建相册</p>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        创建第一个相册
      </el-button>
    </div>

    <!-- Loading Skeleton -->
    <div v-if="loading" class="album-grid">
      <div v-for="i in 6" :key="i" class="album-card skeleton">
        <div class="album-cover skeleton-img"></div>
        <div class="album-body">
          <div class="skeleton-line w60"></div>
          <div class="skeleton-line w80"></div>
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

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="formDialogVisible"
      :title="isEditing ? '编辑相册' : '新建相册'"
      width="480px"
      class="form-dialog"
    >
      <el-form :model="albumForm" label-position="top" class="album-form">
        <el-form-item label="相册名称">
          <el-input
            v-model="albumForm.name"
            placeholder="请输入相册名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="描述（可选）">
          <el-input
            v-model="albumForm.description"
            type="textarea"
            :rows="3"
            placeholder="为相册添加一段描述..."
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="可见性">
          <el-radio-group v-model="albumForm.visibility">
            <el-radio-button :value="0">私密</el-radio-button>
            <el-radio-button :value="1">公开</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">
          {{ isEditing ? '保存修改' : '创建相册' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Album Detail Dialog -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="selectedAlbum?.name"
      width="85vw"
      top="5vh"
      class="detail-dialog"
    >
      <div class="album-detail" v-if="selectedAlbum">
        <div class="detail-header">
          <div>
            <h2>{{ selectedAlbum.name }}</h2>
            <p v-if="selectedAlbum.description">{{ selectedAlbum.description }}</p>
            <span class="detail-meta">{{ selectedAlbum.imageCount }} 张照片 · {{ selectedAlbum.visibility === 1 ? '公开' : '私密' }}</span>
          </div>
        </div>

        <div v-if="detailImages.length > 0" class="detail-masonry" ref="detailGridRef">
          <div class="detail-masonry-col" v-for="(col, colIndex) in detailColumns" :key="colIndex">
            <div
              v-for="img in col"
              :key="img.id"
              class="detail-image-card"
              @click="openLightbox(img)"
            >
              <LazyImage
                :image-id="img.id"
                :loader="loadThumb"
                :ratio="aspectRatioOf(img)"
                :alt="img.originalName"
              />
              <div class="detail-image-actions">
                <el-button :icon="DocumentCopy" size="small" circle @click.stop="handleCopyUrl(img)" title="复制URL" />
                <el-button
                  type="danger"
                  :icon="Delete"
                  size="small"
                  circle
                  @click.stop="handleRemoveImage(img)"
                />
              </div>
            </div>
          </div>
        </div>

        <div v-if="detailImages.length === 0 && !detailLoading" class="detail-empty">
          <p>该相册暂无照片</p>
        </div>

        <div v-if="detailLoading" class="detail-loading">
          <el-icon :size="28" class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>

        <div class="detail-footer" v-if="detailImages.length > 0">
          <div v-if="detailHasMore" ref="detailSentinelRef" class="load-sentinel"></div>
          <div v-if="detailLoadingMore" class="footer-tip">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载中...</span>
          </div>
          <el-button v-else-if="detailHasMore && !supportsObserver" text @click="loadDetailImages">加载更多</el-button>
          <span v-else-if="!detailHasMore" class="footer-tip">已全部加载</span>
        </div>
      </div>
    </el-dialog>

    <ImageLightbox v-model="lightboxVisible" :image="lightboxImage" :src="lightboxSrc" />

    <!-- Cover Cropper Dialog -->
    <AlbumCoverCropper
      v-model="coverCropperVisible"
      :aspect="1.6"
      :output-width="COVER_OUT_W"
      @cropped="handleCoverCropped"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import {
  getMyAlbums, createAlbum, updateAlbum, deleteAlbum,
  getAlbumImages,
  removeImageFromAlbum as removeImageFromAlbumApi,
  getImageThumbnail
} from '@/api/image'
import type { AlbumResponse, ImageResponse } from '@/api/image'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { useMasonry } from '@/composables/useMasonry'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import { Delete, DocumentCopy, Folder, Plus, Edit, FolderOpened, Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ImageLightbox from '@/components/ImageLightbox.vue'
import LazyImage from '@/components/LazyImage.vue'
import { aspectRatioOf } from '@/utils/image'
import AlbumCoverCropper from '@/components/AlbumCoverCropper.vue'
import { PictureRounded } from '@element-plus/icons-vue'
import { uploadImage, updateImageVisibility } from '@/api/image'

const { decryptImage, clearCache } = useImageDecrypt()
const { decryptImage: loadThumb, clearCache: clearThumbCache } = useImageDecrypt(getImageThumbnail)


const albums = ref<AlbumResponse[]>([])
const albumCoverUrls = reactive(new Map<number, string>())
const loading = ref(false)
const currentPage = ref(1)
const pageSize = 12
const total = ref(0)

// Form dialog
const formDialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const albumForm = reactive({
  name: '',
  description: '',
  visibility: 0,
})

// Detail dialog
const detailDialogVisible = ref(false)
const selectedAlbum = ref<AlbumResponse | null>(null)
const detailImages = ref<ImageResponse[]>([])
const detailDecryptedUrls = reactive(new Map<number, string>())
const detailLoading = ref(false)
const detailLoadingMore = ref(false)
/** 已加载批次数，下一次请求的页号是它 +1 */
const detailBatchIndex = ref(0)
const DETAIL_BATCH_SIZE = 48
const detailTotal = ref(0)
const detailHasMore = computed(() => detailImages.value.length < detailTotal.value)
const supportsObserver = typeof IntersectionObserver !== 'undefined'
/** 请求归属：切换相册时自增，迟到的旧响应据此丢弃 */
let detailToken = 0

// Masonry layout for detail dialog
const detailRatioOf = (img: ImageResponse) => (img.width && img.height ? img.width / img.height : 1)
const { gridRef: detailGridRef, columns: detailColumns } = useMasonry(detailImages, detailRatioOf, { minColumnWidth: 240 })
const { sentinelRef: detailSentinelRef, recheck: detailRecheck } = useInfiniteScroll(() => void loadDetailImages())

const lightboxVisible = ref(false)
const lightboxImage = ref<ImageResponse | null>(null)
const lightboxSrc = ref<string | null>(null)

// Cover cropper
const coverCropperVisible = ref(false)
const coverTargetAlbum = ref<AlbumResponse | null>(null)
const coverUploading = ref(false)

const COVER_OUT_W = 1600
const COVER_OUT_H = 1000

const openLightbox = async (img: ImageResponse) => {
  lightboxImage.value = img
  lightboxSrc.value = detailDecryptedUrls.get(img.id) ?? null
  lightboxVisible.value = true
  if (!lightboxSrc.value) {
    const url = await decryptImage(img.id, img.iv, img.visibility)
    if (url) {
      detailDecryptedUrls.set(img.id, url)
      if (lightboxImage.value?.id === img.id) lightboxSrc.value = url
    }
  }
}

/** 封面加载完成后回填，供封面上传后的刷新逻辑复用 */
const onCoverLoaded = (albumId: number, payload: { id: number; url: string }) => {
  albumCoverUrls.set(albumId, payload.url)
}

// Cover cropper handlers
const openCoverCropper = (album: AlbumResponse) => {
  coverTargetAlbum.value = album
  coverCropperVisible.value = true
}

const handleCoverCropped = async (file: File) => {
  const album = coverTargetAlbum.value
  if (!album) return
  coverUploading.value = true
  try {
    const up = await uploadImage(file, {
      originalName: file.name,
      mimeType: file.type,
      fileSize: file.size,
      width: COVER_OUT_W,
      height: COVER_OUT_H,
      visibility: album.visibility,
      purpose: 1,
    })
    const imageId = up.data?.id
    if (!imageId) throw new Error('封面上传失败')

    await updateAlbum(album.id, {
      name: album.name,
      description: album.description || undefined,
      visibility: album.visibility,
      coverImageId: imageId,
    })

    album.coverImageId = imageId
    albumCoverUrls.delete(album.id)
    const url = await decryptImage(imageId, '', album.visibility)
    if (url) albumCoverUrls.set(album.id, url)

    ElMessage.success('封面已更新')
  } catch {
    ElMessage.error('封面更新失败')
  } finally {
    coverUploading.value = false
  }
}


onMounted(() => loadAlbums())
onUnmounted(() => {
  clearCache()
  clearThumbCache()
})

const loadAlbums = async () => {
  loading.value = true
  try {
    const res = await getMyAlbums(currentPage.value, pageSize)
    if (res.data) {
      albums.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
}

const openCreateDialog = () => {
  isEditing.value = false
  editingId.value = null
  albumForm.name = ''
  albumForm.description = ''
  albumForm.visibility = 0
  formDialogVisible.value = true
}

const openEditDialog = (album: AlbumResponse) => {
  isEditing.value = true
  editingId.value = album.id
  albumForm.name = album.name
  albumForm.description = album.description || ''
  albumForm.visibility = album.visibility
  formDialogVisible.value = true
}

const submitForm = async () => {
  if (!albumForm.name.trim()) {
    ElMessage.warning('请输入相册名称')
    return
  }

  submitting.value = true
  try {
    if (isEditing.value && editingId.value) {
      await updateAlbum(editingId.value, {
        name: albumForm.name,
        description: albumForm.description || undefined,
        visibility: albumForm.visibility,
      })
      ElMessage.success('相册更新成功')
    } else {
      await createAlbum({
        name: albumForm.name,
        description: albumForm.description || undefined,
        visibility: albumForm.visibility,
      })
      ElMessage.success('相册创建成功')
    }
    formDialogVisible.value = false
    loadAlbums()
  } catch { /* ignore */ }
  finally { submitting.value = false }
}

const handleDelete = async (album: AlbumResponse) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除相册「${album.name}」吗？相册内的照片不会被删除。`,
      '确认删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await deleteAlbum(album.id)
    ElMessage.success('相册已删除')
    loadAlbums()
  } catch { /* cancelled */ }
}

// Album detail
const openAlbumDetail = async (album: AlbumResponse) => {
  selectedAlbum.value = album
  detailDialogVisible.value = true
  detailBatchIndex.value = 0
  detailTotal.value = 0
  detailToken++
  detailImages.value = []
  detailDecryptedUrls.clear()
  await loadDetailImages()
}

/** 加载相册内下一批照片并追加 */
const loadDetailImages = async () => {
  if (!selectedAlbum.value) return
  if (detailLoading.value || detailLoadingMore.value) return
  const isFirstBatch = detailBatchIndex.value === 0
  if (!isFirstBatch && !detailHasMore.value) return

  const token = detailToken
  const albumId = selectedAlbum.value.id
  if (isFirstBatch) detailLoading.value = true
  else detailLoadingMore.value = true
  try {
    const res = await getAlbumImages(albumId, detailBatchIndex.value + 1, DETAIL_BATCH_SIZE)
    if (token !== detailToken) return
    if (res.data) {
      detailImages.value.push(...(res.data.records || []))
      detailTotal.value = res.data.total || 0
      detailBatchIndex.value++
    }
  } catch { /* ignore */ }
  finally {
    if (token === detailToken) {
      detailLoading.value = false
      detailLoadingMore.value = false
      await detailRecheck()
    }
  }
}

const handleRemoveImage = async (img: ImageResponse) => {
  if (!selectedAlbum.value) return
  try {
    await ElMessageBox.confirm(
      `确定要将「${img.originalName}」从该相册中移除吗？`,
      '移除照片',
      { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' }
    )
    await removeImageFromAlbumApi(selectedAlbum.value.id, img.id)
    ElMessage.success('已移除')
    // 就地移除：重新拉取会把已追加的批次冲掉
    const idx = detailImages.value.findIndex(i => i.id === img.id)
    if (idx !== -1) detailImages.value.splice(idx, 1)
    if (detailTotal.value > 0) detailTotal.value--
    loadAlbums() // Update image count
  } catch { /* cancelled */ }
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

const handleToggleAlbumVisibility = async (album: AlbumResponse) => {
  const newVis = album.visibility === 1 ? 0 : 1
  try {
    await updateAlbum(album.id, {
      name: album.name,
      description: album.description || undefined,
      visibility: newVis,
    })
    album.visibility = newVis

    // Sync cover image visibility
    if (album.coverImageId) {
      await updateImageVisibility(album.coverImageId, newVis)
    }

    ElMessage.success(newVis === 1 ? '相册已设为公开' : '相册已设为私密')
  } catch { /* ignore */ }
}

const formatDate = (dateStr: string): string => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
</script>

<style scoped lang="scss">
.my-albums {
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
  transition: all 0.35s var(--ev-ease-out);
  animation: page-enter 0.5s var(--ev-ease-out) both;

  &:hover {
    transform: translateY(-3px);
    border-color: var(--ev-border-hover);
    box-shadow: var(--ev-shadow-lg);

    .album-cover :deep(img) { transform: scale(1.05); }
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
  cursor: pointer;
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

.cover-vis-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--ev-radius-pill);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  cursor: pointer;
  z-index: 3;
  transition: all 0.2s ease;

  &:hover { transform: scale(1.1); }

  &.public {
    background: var(--ev-bg-glass-strong);
    color: var(--ev-primary);
    border: 1px solid var(--ev-border-default);
  }
  &.private {
    background: var(--ev-bg-glass-strong);
    color: var(--ev-text-secondary);
    border: 1px solid var(--ev-border-default);
  }
}

.album-body {
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
    margin-bottom: 12px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}

.album-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;

  .album-date {
    font-size: 12px;
    color: var(--ev-text-muted);
  }

  .album-btns {
    display: flex;
    gap: 6px;

    :deep(.el-button) {
      color: var(--ev-primary);
      border-color: var(--ev-border-default);
      background: var(--ev-bg-glass-light);
    }

    :deep(.el-button--danger) {
      color: var(--ev-text-on-accent);
      background: var(--ev-danger);
      border-color: var(--ev-danger);
    }
  }
}

/* Empty */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 80px 0;
  color: var(--ev-text-muted);
  border: 1.5px dashed var(--ev-border-hover);
  border-radius: var(--ev-radius-xl);
  background: var(--ev-bg-glass-light);
  transition: all 0.3s var(--ev-ease-out);

  &:hover {
    background: var(--ev-bg-tint);
    border-color: var(--ev-primary);
    box-shadow: var(--ev-glow-violet);
  }

  p { font-size: 16px; color: var(--ev-text-secondary); margin: 4px 0; }
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
    margin: 8px 16px;
    &.w60 { width: calc(60% - 32px); }
    &.w80 { width: calc(80% - 32px); }
  }
}

/* Pagination */
.pagination-wrap {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}

/* Form Dialog */
.form-dialog {
  :deep(.el-dialog) {
    background: var(--ev-bg-surface) !important;
    border: 1px solid var(--ev-border-subtle);
    border-radius: var(--ev-radius-xl) !important;
  }
}

.album-form {
  padding: 8px 0;
}

/* Detail Dialog */
.detail-dialog {
  :deep(.el-dialog) {
    background: var(--ev-bg-surface) !important;
    border: 1px solid var(--ev-border-subtle);
    border-radius: var(--ev-radius-xl) !important;
  }
  :deep(.el-dialog__body) { padding: 0; }
}

.album-detail {
  padding: 24px;
}

.detail-header {
  margin-bottom: 20px;
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
    margin-bottom: 6px;
  }

  .detail-meta {
    font-size: 12px;
    color: var(--ev-text-muted);
  }
}

/* Detail Masonry Layout */
.detail-masonry {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  max-height: 55vh;
  overflow-y: auto;
  overflow-x: hidden;
}

.detail-masonry-col {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-image-card {
  position: relative;
  border-radius: var(--ev-radius-md);
  overflow: hidden;
  background: var(--ev-bg-tint);
  cursor: pointer;
  border: 1px solid var(--ev-border-subtle);
  transition: all 0.3s var(--ev-ease-out);

  &:hover {
    border-color: var(--ev-border-hover);

    .detail-image-actions { opacity: 1; }
  }
}


.detail-image-actions {
  position: absolute;
  bottom: 6px;
  right: 6px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.detail-empty, .detail-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 0;
  color: var(--ev-text-muted);
  font-size: 14px;
}

.detail-footer {
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
