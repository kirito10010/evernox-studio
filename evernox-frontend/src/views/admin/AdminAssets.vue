<template>
  <div class="admin-assets">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-aurora"></div>
      <div class="header-content">
        <div class="header-left">
          <h1><el-icon><PictureFilled /></el-icon> 相册图床</h1>
          <p>统管全平台所有用户的图片与相册，含私密内容</p>
        </div>
        <el-button @click="refresh">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- Stats -->
    <div class="stats-bar">
      <div class="stat-item" v-for="item in statItems" :key="item.label">
        <span class="stat-value">{{ item.value }}</span>
        <span class="stat-label">{{ item.label }}</span>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="asset-tabs">
      <!-- ==================== 图片 ==================== -->
      <el-tab-pane label="图片" name="image">
        <div class="filter-bar">
          <el-input
            v-model="imageFilters.keyword"
            placeholder="搜索文件名或类型"
            clearable
            :prefix-icon="Search"
            class="filter-keyword"
            @input="onImageKeywordInput"
            @clear="applyImageFilters"
          />
          <el-select
            v-model="imageFilters.userId"
            placeholder="全部用户"
            clearable
            filterable
            class="filter-select"
            @change="applyImageFilters"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="user.username"
              :value="user.id"
            />
          </el-select>
          <el-select
            v-model="imageFilters.visibility"
            placeholder="全部可见性"
            clearable
            class="filter-select"
            @change="applyImageFilters"
          >
            <el-option label="公开" :value="1" />
            <el-option label="私密" :value="0" />
          </el-select>
          <el-date-picker
            v-model="imageDateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="~"
            start-placeholder="上传起"
            end-placeholder="止"
            class="filter-date"
            @change="applyImageFilters"
          />
          <el-select v-model="imageFilters.sortField" class="filter-select" @change="applyImageFilters">
            <el-option label="上传时间" value="createdAt" />
            <el-option label="文件大小" value="fileSize" />
            <el-option label="文件名" value="originalName" />
          </el-select>
          <el-select v-model="imageFilters.sortOrder" class="filter-order" @change="applyImageFilters">
            <el-option label="降序" value="desc" />
            <el-option label="升序" value="asc" />
          </el-select>
          <el-button class="filter-reset" @click="resetImageFilters">重置</el-button>
        </div>

        <div class="table-wrap">
          <el-table
            :data="images"
            v-loading="imageLoading"
            row-key="id"
            @selection-change="onImageSelectionChange"
            empty-text="没有符合条件的图片"
          >
            <el-table-column type="selection" width="46" />
            <el-table-column label="预览" width="80">
              <template #default="{ row }">
                <div class="thumb-cell" @click="openLightbox(row as ImageResponse)">
                  <LazyImage
                    :image-id="row.id"
                    :loader="decryptImage"
                    ratio="1 / 1"
                    :alt="row.originalName"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="originalName" label="文件名" min-width="160" show-overflow-tooltip />
            <el-table-column prop="uploaderName" label="归属用户" min-width="110" show-overflow-tooltip />
            <el-table-column label="尺寸" min-width="100">
              <template #default="{ row }">
                {{ row.width && row.height ? `${row.width}×${row.height}` : '—' }}
              </template>
            </el-table-column>
            <el-table-column label="大小" min-width="90">
              <template #default="{ row }">{{ formatBytes(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="公开" min-width="80">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.visibility === 1"
                  :loading="imageUpdatingId === row.id"
                  @change="(val: string | number | boolean) => toggleImageVisibility(row as ImageResponse, val === true)"
                />
              </template>
            </el-table-column>
            <el-table-column label="上传时间" min-width="150">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right" class-name="op-column">
              <template #default="{ row }">
                <div class="op-cell">
                  <el-button size="small" @click="openLightbox(row as ImageResponse)">预览</el-button>
                  <el-button size="small" @click="openAlbumPicker(row as ImageResponse)">相册</el-button>
                  <el-button size="small" type="danger" @click="handleDeleteImage(row as ImageResponse)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="table-footer">
          <div class="footer-left">
            <template v-if="selectedImageIds.length">
              <span class="selected-hint">已选 {{ selectedImageIds.length }} 项</span>
              <el-button size="small" @click="batchImageVisibility(1)">设为公开</el-button>
              <el-button size="small" @click="batchImageVisibility(0)">设为私密</el-button>
              <el-button size="small" type="danger" @click="handleBatchDeleteImages">批量删除</el-button>
            </template>
          </div>
          <el-pagination
            v-model:current-page="imagePage"
            v-model:page-size="imageSize"
            :page-sizes="[20, 40, 60, 100]"
            :total="imageTotal"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadImages"
            @size-change="onImageSizeChange"
          />
        </div>
      </el-tab-pane>

      <!-- ==================== 相册 ==================== -->
      <el-tab-pane label="相册" name="album">
        <div class="filter-bar">
          <el-input
            v-model="albumFilters.keyword"
            placeholder="搜索相册名或描述"
            clearable
            :prefix-icon="Search"
            class="filter-keyword"
            @input="onAlbumKeywordInput"
            @clear="applyAlbumFilters"
          />
          <el-select
            v-model="albumFilters.userId"
            placeholder="全部用户"
            clearable
            filterable
            class="filter-select"
            @change="applyAlbumFilters"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="user.username"
              :value="user.id"
            />
          </el-select>
          <el-select
            v-model="albumFilters.visibility"
            placeholder="全部可见性"
            clearable
            class="filter-select"
            @change="applyAlbumFilters"
          >
            <el-option label="公开" :value="1" />
            <el-option label="私密" :value="0" />
          </el-select>
          <el-date-picker
            v-model="albumDateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="~"
            start-placeholder="创建起"
            end-placeholder="止"
            class="filter-date"
            @change="applyAlbumFilters"
          />
          <el-select v-model="albumFilters.sortField" class="filter-select" @change="applyAlbumFilters">
            <el-option label="创建时间" value="createdAt" />
            <el-option label="更新时间" value="updatedAt" />
            <el-option label="相册名" value="name" />
          </el-select>
          <el-select v-model="albumFilters.sortOrder" class="filter-order" @change="applyAlbumFilters">
            <el-option label="降序" value="desc" />
            <el-option label="升序" value="asc" />
          </el-select>
          <el-button class="filter-reset" @click="resetAlbumFilters">重置</el-button>
        </div>

        <div class="table-wrap">
          <el-table
            :data="albums"
            v-loading="albumLoading"
            row-key="id"
            @selection-change="onAlbumSelectionChange"
            empty-text="没有符合条件的相册"
          >
            <el-table-column type="selection" width="46" />
            <el-table-column label="封面" width="90">
              <template #default="{ row }">
                <div class="cover-cell">
                  <LazyImage
                    v-if="row.coverImageId"
                    :image-id="row.coverImageId"
                    :loader="decryptImage"
                    ratio="16 / 10"
                    :alt="row.name"
                  />
                  <div v-else class="cover-empty"><el-icon><Folder /></el-icon></div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="相册名" min-width="150" show-overflow-tooltip />
            <el-table-column prop="creatorName" label="归属用户" min-width="110" show-overflow-tooltip />
            <el-table-column prop="imageCount" label="图片数" min-width="90" />
            <el-table-column label="公开" min-width="80">
              <template #default="{ row }">
                <el-tag :type="row.visibility === 1 ? 'primary' : 'danger'" size="small" effect="light">
                  {{ row.visibility === 1 ? '公开' : '私密' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="150">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right" class-name="op-column">
              <template #default="{ row }">
                <div class="op-cell">
                  <el-button size="small" @click="openAlbumDrawer(row as AlbumResponse)">查看</el-button>
                  <el-button size="small" @click="openAlbumEdit(row as AlbumResponse)">编辑</el-button>
                  <el-button size="small" type="danger" @click="handleDeleteAlbum(row as AlbumResponse)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="table-footer">
          <div class="footer-left">
            <template v-if="selectedAlbumIds.length">
              <span class="selected-hint">已选 {{ selectedAlbumIds.length }} 项</span>
              <el-button size="small" type="danger" @click="handleBatchDeleteAlbums">批量删除</el-button>
            </template>
          </div>
          <el-pagination
            v-model:current-page="albumPage"
            v-model:page-size="albumSize"
            :page-sizes="[20, 40, 60, 100]"
            :total="albumTotal"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadAlbums"
            @size-change="onAlbumSizeChange"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 原图预览 -->
    <el-dialog v-model="lightboxVisible" width="min(1000px, 92vw)" align-center class="lightbox-dialog">
      <template #header>
        <span class="lightbox-title">
          {{ lightboxImage?.originalName }}
          <el-tag
            v-if="lightboxImage"
            :type="lightboxImage.visibility === 1 ? 'primary' : 'danger'"
            size="small"
            effect="light"
          >
            {{ lightboxImage.visibility === 1 ? '公开' : '私密' }}
          </el-tag>
        </span>
      </template>
      <div class="lightbox-body">
        <img v-if="lightboxSrc" :src="lightboxSrc" :alt="lightboxImage?.originalName" />
        <div v-else class="lightbox-loading">加载中…</div>
      </div>
    </el-dialog>

    <!-- 相册内图片 -->
    <el-drawer v-model="drawerVisible" size="60%" :title="drawerAlbum ? `相册：${drawerAlbum.name}` : '相册'">
      <div v-loading="drawerLoading" class="drawer-body">
        <div v-if="!drawerImages.length && !drawerLoading" class="drawer-empty">该相册暂无图片</div>
        <div v-else class="drawer-grid">
          <div v-for="img in drawerImages" :key="img.id" class="drawer-item">
            <div class="drawer-thumb" @click="openLightbox(img)">
              <LazyImage
                :image-id="img.id"
                :loader="decryptImage"
                :ratio="aspectRatioOf(img)"
                :alt="img.originalName"
              />
            </div>
            <div class="drawer-meta">
              <span class="drawer-name" :title="img.originalName">{{ img.originalName }}</span>
              <el-button size="small" text type="danger" @click="handleRemoveFromAlbum(img)">移出</el-button>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-pagination
          v-model:current-page="drawerPage"
          :page-size="drawerSize"
          :total="drawerTotal"
          layout="total, prev, pager, next"
          @current-change="loadDrawerImages"
        />
      </template>
    </el-drawer>

    <!-- 编辑相册 -->
    <el-dialog v-model="albumEditVisible" title="编辑相册" width="480px">
      <el-form ref="albumFormRef" :model="albumForm" :rules="albumRules" label-position="top">
        <el-form-item label="相册名称" prop="name">
          <el-input v-model="albumForm.name" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="albumForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="可见性">
          <el-radio-group v-model="albumForm.visibility">
            <el-radio-button :value="1">公开</el-radio-button>
            <el-radio-button :value="0">私密</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="封面">
          <el-select v-model="albumForm.coverImageId" placeholder="不修改封面" clearable class="cover-select">
            <el-option
              v-for="img in albumCoverCandidates"
              :key="img.id"
              :label="img.originalName"
              :value="img.id"
            />
          </el-select>
          <div class="form-hint">只能选择该相册内的图片</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="albumEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAlbum">保存修改</el-button>
      </template>
    </el-dialog>

    <!-- 加入相册 -->
    <el-dialog v-model="albumPickerVisible" title="加入相册" width="460px">
      <div class="picker-hint">
        图片归属用户：<strong>{{ pickerImage?.uploaderName }}</strong>。
        为避免跨用户混放，只能加入该用户自己的相册。
      </div>
      <el-select v-model="pickerAlbumId" placeholder="选择相册" filterable class="picker-select">
        <el-option
          v-for="album in pickerAlbums"
          :key="album.id"
          :label="`${album.name}（${album.imageCount ?? 0} 张）`"
          :value="album.id"
        />
      </el-select>
      <div v-if="!pickerAlbums.length" class="form-hint">该用户还没有相册</div>
      <template #footer>
        <el-button @click="albumPickerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!pickerAlbumId" @click="submitAddToAlbum">
          加入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Folder, PictureFilled, Refresh, Search } from '@element-plus/icons-vue'
import LazyImage from '@/components/LazyImage.vue'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { aspectRatioOf, formatBytes } from '@/utils/image'
import type { AlbumResponse, ImageResponse } from '@/api/image'
import {
  getUserOptions, getAdminAssetStats,
  getAdminImages, getAdminImageBlob, updateAdminImageVisibility,
  updateAdminImageVisibilityBatch, deleteAdminImage, deleteAdminImages,
  getAdminAlbums, getAdminAlbumImages, updateAdminAlbum, addAdminAlbumImages,
  removeAdminAlbumImage, deleteAdminAlbum, deleteAdminAlbums,
  type AdminAssetStats, type AdminImageListParams, type AdminAlbumListParams, type UserOption,
} from '@/api/adminAsset'

// 管理员通道取流：普通通道对他人私密图会 403
const { decryptImage, clearCache } = useImageDecrypt(getAdminImageBlob)

const activeTab = ref<'image' | 'album'>('image')
const submitting = ref(false)
const stats = ref<AdminAssetStats | null>(null)
const userOptions = ref<UserOption[]>([])

const statItems = computed(() => [
  { label: '图片总数', value: stats.value?.totalImages ?? '—' },
  { label: '其中私密', value: stats.value?.privateImages ?? '—' },
  { label: '相册总数', value: stats.value?.totalAlbums ?? '—' },
  { label: '图片占用', value: stats.value ? formatBytes(stats.value.imagesUsedBytes) : '—' },
])

const formatDateTime = (value: string): string => {
  if (!value) return '—'
  const d = new Date(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// ==================== 图片列表 ====================

const images = ref<ImageResponse[]>([])
const imageTotal = ref(0)
const imagePage = ref(1)
const imageSize = ref(20)
const imageLoading = ref(false)
const imageUpdatingId = ref<number | null>(null)
const selectedImageIds = ref<number[]>([])
const imageDateRange = ref<[string, string] | null>(null)

const imageFilters = reactive({
  keyword: '',
  userId: null as number | null,
  visibility: null as number | null,
  sortField: 'createdAt' as NonNullable<AdminImageListParams['sortField']>,
  sortOrder: 'desc' as NonNullable<AdminImageListParams['sortOrder']>,
})

const loadImages = async () => {
  imageLoading.value = true
  try {
    const res = await getAdminImages({
      page: imagePage.value,
      size: imageSize.value,
      keyword: imageFilters.keyword.trim() || undefined,
      userId: imageFilters.userId,
      visibility: imageFilters.visibility,
      startDate: imageDateRange.value?.[0],
      endDate: imageDateRange.value?.[1],
      sortField: imageFilters.sortField,
      sortOrder: imageFilters.sortOrder,
    })
    images.value = res.data?.records || []
    imageTotal.value = res.data?.total || 0

    // 删完最后一页时自动回退，避免停在空页
    if (images.value.length === 0 && imagePage.value > 1) {
      imagePage.value -= 1
      await loadImages()
    }
  } catch { /* 请求层已提示 */ }
  finally { imageLoading.value = false }
}

const applyImageFilters = () => {
  imagePage.value = 1
  loadImages()
}

const onImageSizeChange = () => {
  imagePage.value = 1
  loadImages()
}

// 关键词防抖：避免每敲一个字符打一次接口
let imageKeywordTimer: ReturnType<typeof setTimeout> | null = null
const onImageKeywordInput = () => {
  if (imageKeywordTimer) clearTimeout(imageKeywordTimer)
  imageKeywordTimer = setTimeout(applyImageFilters, 300)
}

const resetImageFilters = () => {
  imageFilters.keyword = ''
  imageFilters.userId = null
  imageFilters.visibility = null
  imageFilters.sortField = 'createdAt'
  imageFilters.sortOrder = 'desc'
  imageDateRange.value = null
  applyImageFilters()
}

const onImageSelectionChange = (rows: ImageResponse[]) => {
  selectedImageIds.value = rows.map((row) => row.id)
}

const toggleImageVisibility = async (row: ImageResponse, next: boolean) => {
  const target = next ? 1 : 0
  imageUpdatingId.value = row.id
  try {
    await updateAdminImageVisibility(row.id, target)
    row.visibility = target
    ElMessage.success(target === 1 ? '已设为公开' : '已设为私密')
    loadStats()
  } catch {
    // 失败时不改动 row.visibility，开关会因受控绑定自动回弹
  } finally {
    imageUpdatingId.value = null
  }
}

const batchImageVisibility = async (target: number) => {
  if (!selectedImageIds.value.length) return
  try {
    await updateAdminImageVisibilityBatch(selectedImageIds.value, target)
    ElMessage.success(`已将 ${selectedImageIds.value.length} 张图片设为${target === 1 ? '公开' : '私密'}`)
    await refresh()
  } catch { /* 请求层已提示 */ }
}

const IMAGE_DELETE_WARNING = '此操作不可恢复，图片文件将从磁盘删除，相册关联一并清理。'

const handleDeleteImage = async (row: ImageResponse) => {
  try {
    await ElMessageBox.confirm(
      `${IMAGE_DELETE_WARNING}\n\n确认删除「${row.originalName}」（归属 ${row.uploaderName}）？`,
      '删除图片',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    await deleteAdminImage(row.id)
    ElMessage.success('图片已删除')
    await refresh()
  } catch { /* 请求层已提示 */ }
}

const handleBatchDeleteImages = async () => {
  const count = selectedImageIds.value.length
  if (!count) return

  try {
    // 要求输入 DELETE 才能执行，避免误删他人资产
    await ElMessageBox.prompt(
      `${IMAGE_DELETE_WARNING}\n\n即将删除 ${count} 张图片，请输入 DELETE 以确认：`,
      '批量删除图片',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        inputValidator: (value: string) => value === 'DELETE' || '请输入 DELETE',
      }
    )
  } catch {
    return
  }

  try {
    await deleteAdminImages(selectedImageIds.value)
    ElMessage.success(`已删除 ${count} 张图片`)
    selectedImageIds.value = []
    await refresh()
  } catch { /* 请求层已提示 */ }
}

// ==================== 原图预览 ====================

const lightboxVisible = ref(false)
const lightboxImage = ref<ImageResponse | null>(null)
const lightboxSrc = ref<string | null>(null)

const openLightbox = async (img: ImageResponse) => {
  lightboxImage.value = img
  lightboxSrc.value = null
  lightboxVisible.value = true
  const url = await decryptImage(img.id)
  // 期间可能已切换到别的图片
  if (url && lightboxImage.value?.id === img.id) {
    lightboxSrc.value = url
  }
}

// ==================== 相册列表 ====================

const albums = ref<AlbumResponse[]>([])
const albumTotal = ref(0)
const albumPage = ref(1)
const albumSize = ref(20)
const albumLoading = ref(false)
const selectedAlbumIds = ref<number[]>([])
const albumDateRange = ref<[string, string] | null>(null)

const albumFilters = reactive({
  keyword: '',
  userId: null as number | null,
  visibility: null as number | null,
  sortField: 'createdAt' as NonNullable<AdminAlbumListParams['sortField']>,
  sortOrder: 'desc' as NonNullable<AdminAlbumListParams['sortOrder']>,
})

const loadAlbums = async () => {
  albumLoading.value = true
  try {
    const res = await getAdminAlbums({
      page: albumPage.value,
      size: albumSize.value,
      keyword: albumFilters.keyword.trim() || undefined,
      userId: albumFilters.userId,
      visibility: albumFilters.visibility,
      startDate: albumDateRange.value?.[0],
      endDate: albumDateRange.value?.[1],
      sortField: albumFilters.sortField,
      sortOrder: albumFilters.sortOrder,
    })
    albums.value = res.data?.records || []
    albumTotal.value = res.data?.total || 0

    if (albums.value.length === 0 && albumPage.value > 1) {
      albumPage.value -= 1
      await loadAlbums()
    }
  } catch { /* 请求层已提示 */ }
  finally { albumLoading.value = false }
}

const applyAlbumFilters = () => {
  albumPage.value = 1
  loadAlbums()
}

const onAlbumSizeChange = () => {
  albumPage.value = 1
  loadAlbums()
}

let albumKeywordTimer: ReturnType<typeof setTimeout> | null = null
const onAlbumKeywordInput = () => {
  if (albumKeywordTimer) clearTimeout(albumKeywordTimer)
  albumKeywordTimer = setTimeout(applyAlbumFilters, 300)
}

const resetAlbumFilters = () => {
  albumFilters.keyword = ''
  albumFilters.userId = null
  albumFilters.visibility = null
  albumFilters.sortField = 'createdAt'
  albumFilters.sortOrder = 'desc'
  albumDateRange.value = null
  applyAlbumFilters()
}

const onAlbumSelectionChange = (rows: AlbumResponse[]) => {
  selectedAlbumIds.value = rows.map((row) => row.id)
}

const ALBUM_DELETE_WARNING = '删除相册不会删除其中的图片，只会解除关联。'

const handleDeleteAlbum = async (row: AlbumResponse) => {
  try {
    await ElMessageBox.confirm(
      `${ALBUM_DELETE_WARNING}\n\n确认删除相册「${row.name}」（归属 ${row.creatorName}）？`,
      '删除相册',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    await deleteAdminAlbum(row.id)
    ElMessage.success('相册已删除')
    await refresh()
  } catch { /* 请求层已提示 */ }
}

const handleBatchDeleteAlbums = async () => {
  const count = selectedAlbumIds.value.length
  if (!count) return

  try {
    await ElMessageBox.confirm(
      `${ALBUM_DELETE_WARNING}\n\n即将删除 ${count} 个相册，确认继续？`,
      '批量删除相册',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    await deleteAdminAlbums(selectedAlbumIds.value)
    ElMessage.success(`已删除 ${count} 个相册`)
    selectedAlbumIds.value = []
    await refresh()
  } catch { /* 请求层已提示 */ }
}

// ==================== 相册内图片 ====================

const drawerVisible = ref(false)
const drawerAlbum = ref<AlbumResponse | null>(null)
const drawerImages = ref<ImageResponse[]>([])
const drawerTotal = ref(0)
const drawerPage = ref(1)
const drawerSize = ref(24)
const drawerLoading = ref(false)

const loadDrawerImages = async () => {
  if (!drawerAlbum.value) return
  drawerLoading.value = true
  try {
    const res = await getAdminAlbumImages(drawerAlbum.value.id, drawerPage.value, drawerSize.value)
    drawerImages.value = res.data?.records || []
    drawerTotal.value = res.data?.total || 0
  } catch { /* 请求层已提示 */ }
  finally { drawerLoading.value = false }
}

const openAlbumDrawer = async (row: AlbumResponse) => {
  drawerAlbum.value = row
  drawerPage.value = 1
  drawerImages.value = []
  drawerVisible.value = true
  await loadDrawerImages()
}

const handleRemoveFromAlbum = async (img: ImageResponse) => {
  if (!drawerAlbum.value) return
  try {
    await ElMessageBox.confirm(
      `将「${img.originalName}」从相册移出？图片本体保留。`,
      '移出相册',
      { type: 'warning', confirmButtonText: '移出', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    await removeAdminAlbumImage(drawerAlbum.value.id, img.id)
    ElMessage.success('已移出相册')
    await loadDrawerImages()
    await loadAlbums()
  } catch { /* 请求层已提示 */ }
}

// ==================== 编辑相册 ====================

const albumEditVisible = ref(false)
const albumFormRef = ref<FormInstance>()
const editingAlbumId = ref<number | null>(null)
const albumCoverCandidates = ref<ImageResponse[]>([])

const albumForm = reactive({
  name: '',
  description: '',
  visibility: 0,
  coverImageId: null as number | null,
})

const albumRules: FormRules = {
  name: [
    { required: true, message: '请输入相册名称', trigger: 'blur' },
    { max: 100, message: '名称不能超过 100 字符', trigger: 'blur' },
  ],
}

const openAlbumEdit = async (row: AlbumResponse) => {
  editingAlbumId.value = row.id
  albumForm.name = row.name
  albumForm.description = row.description ?? ''
  albumForm.visibility = row.visibility
  albumForm.coverImageId = row.coverImageId ?? null
  albumFormRef.value?.clearValidate()
  albumEditVisible.value = true

  // 封面候选必须来自相册内部，否则后端会拒绝
  try {
    const res = await getAdminAlbumImages(row.id, 1, 100)
    albumCoverCandidates.value = res.data?.records || []
  } catch {
    albumCoverCandidates.value = []
  }
}

const submitAlbum = async () => {
  const valid = await albumFormRef.value?.validate().catch(() => false)
  if (!valid || editingAlbumId.value === null) return

  submitting.value = true
  try {
    await updateAdminAlbum(editingAlbumId.value, {
      name: albumForm.name.trim(),
      description: albumForm.description.trim() || null,
      visibility: albumForm.visibility,
      coverImageId: albumForm.coverImageId,
    })
    ElMessage.success('相册已更新')
    albumEditVisible.value = false
    await refresh()
  } catch { /* 请求层已提示 */ }
  finally { submitting.value = false }
}

// ==================== 加入相册 ====================

const albumPickerVisible = ref(false)
const pickerImage = ref<ImageResponse | null>(null)
const pickerAlbums = ref<AlbumResponse[]>([])
const pickerAlbumId = ref<number | null>(null)

const openAlbumPicker = async (row: ImageResponse) => {
  pickerImage.value = row
  pickerAlbumId.value = null
  pickerAlbums.value = []
  albumPickerVisible.value = true
  try {
    // 只列该图片所属用户的相册：跨用户加图后端会拒绝
    const res = await getAdminAlbums({ page: 1, size: 100, userId: row.userId })
    pickerAlbums.value = res.data?.records || []
  } catch {
    pickerAlbums.value = []
  }
}

const submitAddToAlbum = async () => {
  if (!pickerImage.value || pickerAlbumId.value === null) return
  submitting.value = true
  try {
    await addAdminAlbumImages(pickerAlbumId.value, [pickerImage.value.id])
    ElMessage.success('已加入相册')
    albumPickerVisible.value = false
    await loadAlbums()
  } catch { /* 请求层已提示 */ }
  finally { submitting.value = false }
}

// ==================== 初始化 ====================

const loadStats = async () => {
  try {
    const res = await getAdminAssetStats()
    stats.value = res.data ?? null
  } catch {
    stats.value = null
  }
}

const loadUserOptions = async () => {
  try {
    const res = await getUserOptions()
    userOptions.value = res.data ?? []
  } catch {
    userOptions.value = []
  }
}

const refresh = async () => {
  await Promise.all([loadImages(), loadAlbums(), loadStats()])
}

onMounted(async () => {
  await loadUserOptions()
  await refresh()
})

onUnmounted(() => {
  if (imageKeywordTimer) clearTimeout(imageKeywordTimer)
  if (albumKeywordTimer) clearTimeout(albumKeywordTimer)
  clearCache()
})
</script>

<style scoped lang="scss">
.admin-assets {
  display: flex;
  flex-direction: column;
  gap: 18px;
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

.stats-bar,
.filter-bar,
.table-wrap,
.table-footer {
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: 16px;
  backdrop-filter: var(--ev-blur-md);
  -webkit-backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
}

.stats-bar {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  padding: 16px 20px;
  gap: 12px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .stat-value {
    font-size: 22px;
    font-weight: 800;
    color: var(--ev-text-primary);
    font-variant-numeric: tabular-nums;
  }

  .stat-label {
    font-size: 12px;
    color: var(--ev-text-secondary);
  }
}

.asset-tabs {
  :deep(.el-tabs__content) {
    display: flex;
    flex-direction: column;
    gap: 18px;
    overflow: visible;
  }

  :deep(.el-tab-pane) {
    display: flex;
    flex-direction: column;
    gap: 18px;
  }
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;

  /* 各控件按权重自适应剩余宽度，窄屏才换行 */
  .filter-keyword { flex: 3 1 190px; min-width: 0; }
  .filter-select { flex: 1 1 120px; min-width: 0; max-width: 170px; }
  .filter-order { flex: 1 1 88px; min-width: 0; max-width: 110px; }
  .filter-date {
    flex: 2 1 200px;
    min-width: 0;
    /* 覆盖 el-date-editor--daterange 的固定 350px */
    width: auto !important;
    max-width: 250px;
  }
  .filter-reset { flex: 0 0 auto; }
}

.table-wrap {
  padding: 8px 12px;
  overflow: hidden;

  :deep(.el-table) {
    background: transparent;
  }

  :deep(.el-table tr),
  :deep(.el-table th.el-table__cell) {
    background: transparent;
  }

  :deep(.op-column .cell) {
    padding-left: 8px;
    padding-right: 8px;
  }

  /* 固定宽度列 + nowrap：无论表格被挤到多窄，按钮始终同一排 */
  .op-cell {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: nowrap;
    white-space: nowrap;

    :deep(.el-button) {
      margin-left: 0;
    }
  }
}

.thumb-cell,
.cover-cell {
  width: 56px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid var(--ev-border-subtle);
  background: rgba(255, 255, 255, 0.5);
}

.cover-cell {
  cursor: default;
}

.cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 16 / 10;
  color: var(--ev-text-muted);
}

.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 20px;
}

.footer-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 24px;

  .selected-hint {
    font-size: 13px;
    color: var(--ev-text-secondary);
  }
}

.form-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ev-text-muted);
}

/* 预览弹窗 */
.lightbox-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ev-text-primary);
}

.lightbox-body {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  max-height: 72vh;

  img {
    max-width: 100%;
    max-height: 72vh;
    border-radius: 10px;
    object-fit: contain;
  }
}

.lightbox-loading {
  font-size: 13px;
  color: var(--ev-text-secondary);
}

/* 相册抽屉 */
.drawer-body {
  min-height: 120px;
}

.drawer-empty {
  padding: 40px 0;
  text-align: center;
  font-size: 13px;
  color: var(--ev-text-secondary);
}

.drawer-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 14px;
}

.drawer-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.drawer-thumb {
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid var(--ev-border-subtle);
  background: rgba(255, 255, 255, 0.5);
}

.drawer-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;

  .drawer-name {
    flex: 1;
    min-width: 0;
    font-size: 12px;
    color: var(--ev-text-secondary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

/* 对话框内控件 */
.cover-select,
.picker-select {
  width: 100%;
}

.picker-hint {
  margin-bottom: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ev-text-secondary);
}
</style>
