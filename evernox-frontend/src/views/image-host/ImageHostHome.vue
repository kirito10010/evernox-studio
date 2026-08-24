<template>
  <div class="image-host-home">
    <!-- Hero Banner -->
    <div class="hero-banner">
      <div class="banner-aurora"></div>
      <div class="geo-shapes">
        <div class="geo-ring ring-1"></div>
        <div class="geo-ring ring-2"></div>
        <div class="geo-dot dot-1"></div>
        <div class="geo-dot dot-2"></div>
      </div>
      <div class="banner-content">
        <div class="banner-pill">
          <span class="pill-dot"></span>
          客户端加密 · 零知识存储
        </div>
        <h1>安全<span class="aurora-text">图床</span>管理平台</h1>
        <p>所有图片在浏览器端完成 AES-256-GCM 加密，服务器仅存储密文，确保您的照片绝对安全</p>
        <el-button type="primary" class="upload-trigger" @click="showUpload = true">
          <el-icon><Upload /></el-icon>
          立即上传
        </el-button>
      </div>
    </div>

    <div class="home-body">
      <div class="home-main">
        <!-- Quick Actions -->
        <div class="quick-section">
          <div class="section-header">
            <h2>快速操作</h2>
          </div>
          <div class="quick-grid">
            <router-link to="/image-host/public" class="quick-card" style="--q-color: #2f7cf6; --q-rgb: 47,124,246">
              <el-icon :size="28"><View /></el-icon>
              <span>浏览公开图床</span>
              <small>查看所有人分享的公开照片</small>
            </router-link>
            <router-link to="/image-host/public-albums" class="quick-card" style="--q-color: #4fc3e8; --q-rgb: 79,195,232">
              <el-icon :size="28"><FolderOpened /></el-icon>
              <span>公开相册</span>
              <small>探索精彩的公开相册合集</small>
            </router-link>
            <router-link to="/image-host/my-images" class="quick-card" style="--q-color: #7fb2fb; --q-rgb: 127,178,251">
              <el-icon :size="28"><PictureFilled /></el-icon>
              <span>我的图床</span>
              <small>管理您的私人加密照片库</small>
            </router-link>
            <router-link to="/image-host/my-albums" class="quick-card" style="--q-color: #34c9a3; --q-rgb: 52,201,163">
              <el-icon :size="28"><Folder /></el-icon>
              <span>我的相册</span>
              <small>创建和管理照片相册分类</small>
            </router-link>
          </div>
        </div>

        <!-- Recent Images -->
        <div class="recent-section" v-if="recentImages.length > 0">
          <div class="section-header">
            <h2>最近上传</h2>
            <router-link to="/image-host/my-images" class="view-all-link">查看全部 →</router-link>
          </div>
          <div class="masonry" ref="gridRef">
            <div class="masonry-col" v-for="(col, colIndex) in columns" :key="colIndex">
              <div
                v-for="img in col"
                :key="img.id"
                class="recent-card"
                @click="openLightbox(img)"
              >
                <div class="recent-thumb">
                  <LazyImage
                    :image-id="img.id"
                    :loader="loadThumb"
                    :ratio="aspectRatioOf(img)"
                    :alt="img.originalName"
                  />
                  <div class="recent-hover-actions">
                    <el-button :icon="DocumentCopy" circle size="small" @click.stop="handleCopyUrl(img)" title="复制URL" />
                  </div>
                  <div class="visibility-badge" :class="img.visibility === 1 ? 'public' : 'private'">
                    {{ img.visibility === 1 ? '公开' : '私密' }}
                  </div>
                </div>
                <div class="recent-info">
                  <span class="recent-name" :title="img.originalName">{{ img.originalName }}</span>
                  <span class="recent-meta">{{ formatSize(img.fileSize) }} · {{ formatDate(img.createdAt) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Side Info Panel -->
      <aside class="home-side">
        <div class="side-stats">
          <div
            class="side-stat"
            v-for="item in statList"
            :key="item.label"
            :style="{ '--card-accent': item.accent, '--card-accent-rgb': item.accentRgb }"
          >
            <div class="icon-box" :style="{ background: item.iconBg }">
              <el-icon :size="18"><component :is="item.icon" /></el-icon>
            </div>
            <div class="info-box">
              <div class="stat-value">{{ item.value }}</div>
              <div class="stat-label">{{ item.label }}</div>
            </div>
          </div>
        </div>

        <div class="side-storage">
          <div class="storage-title">存储空间</div>

          <div class="storage-row">
            <span class="row-label">照片占用</span>
            <span class="row-value">{{ storage ? formatBytes(storage.imagesUsed) : '--' }}</span>
          </div>

          <template v-if="storage && storage.diskTotal > 0">
            <div class="disk-block">
              <div class="disk-head">
                <span class="row-label">照片占用率</span>
                <span class="disk-percent">{{ diskPercentText }}</span>
              </div>
              <el-progress
                :percentage="diskPercent"
                :stroke-width="8"
                :show-text="false"
                :color="diskBarColor"
              />
              <div class="disk-foot">
                <span>剩余 {{ formatBytes(storage.diskFree) }}</span>
              </div>
            </div>

            <div class="disk-block">
              <div class="disk-head">
                <span class="row-label">占全部用户</span>
                <span class="disk-percent">{{ sharePercentText }}</span>
              </div>
              <el-progress
                :percentage="sharePercent"
                :stroke-width="8"
                :show-text="false"
                color="#5b8ff9"
              />
              <div class="disk-foot">
                <span>全平台照片共 {{ formatBytes(storage.allImagesUsed) }}</span>
              </div>
            </div>
          </template>
          <div v-else class="storage-unavailable">磁盘信息暂不可用</div>
        </div>
      </aside>
    </div>

    <UploadModal v-model="showUpload" @uploaded="onUploaded" />

    <ImageLightbox v-model="lightboxVisible" :image="lightboxImage" :src="lightboxSrc" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { getImageStats, getAlbumStats, getMyImages, getStorageStats, getImageThumbnail } from '@/api/image'
import type { ImageResponse, StorageStats } from '@/api/image'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { useMasonry } from '@/composables/useMasonry'
import { ElMessage } from 'element-plus'
import { DocumentCopy, View, FolderOpened, PictureFilled, Folder, Upload } from '@element-plus/icons-vue'
import UploadModal from '@/components/UploadModal.vue'
import ImageLightbox from '@/components/ImageLightbox.vue'
import LazyImage from '@/components/LazyImage.vue'
import { aspectRatioOf, formatBytes, photoSharePercent, photoUsagePercent } from '@/utils/image'

const { decryptImage } = useImageDecrypt()
const { decryptImage: loadThumb } = useImageDecrypt(getImageThumbnail)

const showUpload = ref(false)
const myImageCount = ref(0)
const publicImageCount = ref(0)
const myAlbumCount = ref(0)
const recentImages = ref<ImageResponse[]>([])
const decryptedUrls = reactive(new Map<number, string>())
const storage = ref<StorageStats | null>(null)

const diskPercent = computed(() =>
  storage.value
    ? photoUsagePercent(storage.value.imagesUsed, storage.value.diskFree, storage.value.allImagesUsed)
    : 0
)

const diskPercentText = computed(() => `${diskPercent.value.toFixed(2)}%`)

/** 本用户占全平台照片占用的比例 */
const sharePercent = computed(() =>
  storage.value ? photoSharePercent(storage.value.imagesUsed, storage.value.allImagesUsed) : 0
)

const sharePercentText = computed(() => `${sharePercent.value.toFixed(2)}%`)

const diskBarColor = computed(() => {
  if (diskPercent.value > 90) return '#f2637f'
  if (diskPercent.value > 75) return '#e6a23c'
  return '#34c9a3'
})

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
const { gridRef, columns } = useMasonry(recentImages, ratioOf, { minColumnWidth: 220 })

const statList = computed(() => [
  {
    label: '我的图片',
    value: myImageCount.value,
    icon: 'PictureFilled',
    accent: '#2f7cf6',
    accentRgb: '47, 124, 246',
    iconBg: 'linear-gradient(135deg, rgba(47,124,246,0.16), rgba(79,195,232,0.16))',
  },
  {
    label: '公开图片',
    value: publicImageCount.value,
    icon: 'View',
    accent: '#4fc3e8',
    accentRgb: '79, 195, 232',
    iconBg: 'linear-gradient(135deg, rgba(79,195,232,0.16), rgba(165,228,242,0.18))',
  },
  {
    label: '我的相册',
    value: myAlbumCount.value,
    icon: 'FolderOpened',
    accent: '#7fb2fb',
    accentRgb: '127, 178, 251',
    iconBg: 'linear-gradient(135deg, rgba(127,178,251,0.18), rgba(207,230,255,0.20))',
  },
])

onMounted(async () => {
  await loadStats()
  await loadRecent()
  loadStorage()
})

const loadStats = async () => {
  try {
    const [imgRes, albumRes] = await Promise.all([getImageStats(), getAlbumStats()])
    if (imgRes.data) {
      myImageCount.value = imgRes.data.myImages || 0
      publicImageCount.value = imgRes.data.publicImages || 0
    }
    if (albumRes.data) {
      myAlbumCount.value = albumRes.data.myAlbums || 0
    }
  } catch { /* ignore */ }
}

// 存储统计失败不应影响首页其余内容
const loadStorage = async () => {
  try {
    const res = await getStorageStats()
    storage.value = res.data ?? null
  } catch {
    storage.value = null
  }
}

const loadRecent = async () => {
  try {
    const res = await getMyImages(1, 8)
    if (res.data?.records) {
      recentImages.value = res.data.records
    }
  } catch { /* ignore */ }
}

const onUploaded = () => {
  loadStats()
  loadRecent()
  loadStorage()
}

const formatSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const formatDate = (dateStr: string): string => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

const handleCopyUrl = async (img: ImageResponse) => {
  const url = `${window.location.origin}/api/image/${img.id}/file`
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
</script>

<style scoped lang="scss">
.image-host-home {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Hero Banner */
.hero-banner {
  position: relative;
  padding: 48px 44px;
  border-radius: var(--ev-radius-xl);
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  -webkit-backdrop-filter: var(--ev-blur-md);
  backdrop-filter: var(--ev-blur-md);
  overflow: hidden;
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    background: rgba(255, 255, 255, 0.92);
  }
}

.banner-aurora {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg,
    rgba(47, 124, 246, 0.14) 0%,
    rgba(79, 195, 232, 0.14) 40%,
    rgba(165, 228, 242, 0.18) 70%,
    rgba(47, 124, 246, 0.08) 100%
  );
  background-size: 300% 300%;
  animation: aurora-drift 28s ease-in-out infinite;
}

.geo-shapes {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.geo-ring {
  position: absolute;
  border-radius: 50%;
  border: 1.5px solid;

  &.ring-1 {
    width: 180px; height: 180px;
    right: 50px; top: -30px;
    border-color: rgba(47, 124, 246, 0.14);
    animation: float-particle 10s ease-in-out infinite;
  }
  &.ring-2 {
    width: 100px; height: 100px;
    right: 180px; bottom: 10px;
    border-color: rgba(79, 195, 232, 0.14);
    animation: float-particle 8s ease-in-out infinite 1.5s;
  }
}

.geo-dot {
  position: absolute;
  border-radius: 50%;
  &.dot-1 {
    width: 7px; height: 7px;
    right: 140px; top: 28px;
    background: rgba(47, 124, 246, 0.45);
    box-shadow: 0 0 10px rgba(47, 124, 246, 0.3);
    animation: glow-pulse 4s ease-in-out infinite;
  }
  &.dot-2 {
    width: 5px; height: 5px;
    right: 80px; bottom: 24px;
    background: rgba(79, 195, 232, 0.45);
    box-shadow: 0 0 10px rgba(79, 195, 232, 0.3);
    animation: glow-pulse 5s ease-in-out infinite 1s;
  }
}

.banner-content {
  position: relative;
  z-index: 2;

  .banner-pill {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.8px;
    color: var(--ev-primary);
    background: rgba(47, 124, 246, 0.08);
    border: 1px solid rgba(47, 124, 246, 0.2);
    padding: 5px 14px;
    border-radius: 20px;
    margin-bottom: 16px;

    .pill-dot {
      width: 5px; height: 5px;
      border-radius: 50%;
      background: var(--ev-primary);
      box-shadow: 0 0 8px rgba(47, 124, 246, 0.45);
      animation: glow-pulse 3s ease-in-out infinite;
    }
  }

  h1 {
    font-size: 28px;
    font-weight: 800;
    color: var(--ev-text-primary);
    margin-bottom: 10px;
    letter-spacing: -0.5px;
  }

  p {
    font-size: 14px;
    color: var(--ev-text-secondary);
    max-width: 520px;
    line-height: 1.7;
    margin-bottom: 20px;
  }
}

.aurora-text {
  background: linear-gradient(135deg, #2f7cf6 0%, #4fc3e8 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.upload-trigger {
  padding: 10px 28px !important;
  font-size: 14px !important;
}

/* Body: main + side */
.home-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 20px;
  align-items: start;
}

.home-main {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
}

.home-side {
  position: sticky;
  top: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-stats,
.side-storage {
  padding: 16px;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: 18px;
  -webkit-backdrop-filter: var(--ev-blur-md);
  backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    background: rgba(255, 255, 255, 0.92);
  }
}

.side-stats {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.side-stat {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;

  .icon-box {
    width: 38px;
    height: 38px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--card-accent, var(--ev-primary));
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
    flex-shrink: 0;
  }

  .stat-value {
    font-size: 20px;
    font-weight: 800;
    color: var(--ev-text-primary);
    line-height: 1.2;
    font-variant-numeric: tabular-nums;
  }

  .stat-label {
    font-size: 12px;
    color: var(--ev-text-secondary);
    margin-top: 1px;
  }
}

.side-storage {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .storage-title {
    font-size: 14px;
    font-weight: 700;
    color: var(--ev-text-primary);
  }

  .storage-row {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
  }

  .row-label {
    font-size: 12px;
    color: var(--ev-text-secondary);
  }

  .row-value {
    font-size: 14px;
    font-weight: 600;
    color: var(--ev-text-primary);
    font-variant-numeric: tabular-nums;
  }

  .disk-block {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding-top: 12px;
    border-top: 1px solid var(--ev-border-subtle);
  }

  .disk-head {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
  }

  .disk-percent {
    font-size: 13px;
    font-weight: 700;
    color: var(--ev-text-primary);
    font-variant-numeric: tabular-nums;
  }

  .disk-foot {
    display: flex;
    justify-content: flex-end;
    font-size: 11px;
    color: var(--ev-text-muted);
  }

  .storage-unavailable {
    font-size: 12px;
    color: var(--ev-text-muted);
  }
}

@media (max-width: 1100px) {
  .home-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .home-side {
    position: static;
  }

  .side-stats {
    flex-direction: row;
    justify-content: space-between;
  }
}

/* Quick Actions */
.quick-section {
  .section-header {
    margin-bottom: 16px;
    h2 {
      font-size: 18px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }
  }
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 16px;
}

.quick-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 16px;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: 16px;
  -webkit-backdrop-filter: var(--ev-blur-md);
  backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
  text-align: center;
  transition: all 0.35s var(--ev-ease-out);
  cursor: pointer;
  text-decoration: none;
  color: var(--ev-primary);
  animation: page-enter 0.5s var(--ev-ease-out) both;

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    background: rgba(255, 255, 255, 0.92);
  }

  span {
    font-size: 15px;
    font-weight: 600;
    color: var(--ev-text-primary);
  }

  small {
    font-size: 12px;
    color: var(--ev-text-muted);
  }

  &:hover {
    transform: translateY(-3px);
    border-color: var(--ev-border-hover);
    box-shadow: var(--ev-shadow-lg), var(--ev-inset-gloss);

    .el-icon {
      color: var(--q-color);
    }
  }
}

/* Recent Images */
.recent-section {
  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    h2 {
      font-size: 18px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }
  }

  .view-all-link {
    font-size: 13px;
    color: var(--ev-primary);
    &:hover { color: var(--ev-primary-hover); }
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

.recent-card {
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: 16px;
  -webkit-backdrop-filter: var(--ev-blur-md);
  backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s var(--ev-ease-out);

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    background: rgba(255, 255, 255, 0.92);
  }

  &:hover {
    transform: translateY(-3px);
    border-color: var(--ev-border-hover);
    box-shadow: var(--ev-shadow-lg), var(--ev-inset-gloss);
  }
}

.recent-thumb {
  position: relative;
  overflow: hidden;
  background: var(--ev-bg-tint);
}


.recent-hover-actions {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: rgba(47, 124, 246, 0.18);
  -webkit-backdrop-filter: blur(4px);
  backdrop-filter: blur(4px);
  opacity: 0;
  transition: opacity 0.25s ease;
  z-index: 2;

  .el-button {
    background: rgba(255, 255, 255, 0.82);
    border-color: var(--ev-border-gloss);
    color: var(--ev-primary);

    &:hover {
      background: #fff;
      border-color: var(--ev-border-active);
    }
  }
}

.recent-card:hover .recent-hover-actions {
  opacity: 1;
}

.visibility-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
  -webkit-backdrop-filter: blur(8px);
  backdrop-filter: blur(8px);

  &.public {
    background: rgba(52, 201, 163, 0.16);
    color: var(--ev-success);
    border: 1px solid rgba(52, 201, 163, 0.3);
  }
  &.private {
    background: rgba(242, 99, 127, 0.16);
    color: var(--ev-danger);
    border: 1px solid rgba(242, 99, 127, 0.3);
  }
}

.recent-info {
  padding: 10px 12px;

  .recent-name {
    display: block;
    font-size: 13px;
    font-weight: 500;
    color: var(--ev-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .recent-meta {
    font-size: 11px;
    color: var(--ev-text-muted);
    margin-top: 2px;
  }
}
</style>
