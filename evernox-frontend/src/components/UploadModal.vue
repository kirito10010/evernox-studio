<template>
  <el-dialog
    v-model="visible"
    title="上传图片"
    width="680px"
    :close-on-click-modal="!isUploading"
    :close-on-press-escape="!isUploading"
    :show-close="!isUploading"
    class="upload-dialog"
  >
    <!-- Step 1: Select files -->
    <div v-if="step === 1" class="upload-step">
      <div class="step-header">
        <h3>选择文件</h3>
        <p>选择要上传的照片或整个文件夹</p>
      </div>

      <div class="upload-options">
        <div class="upload-option-btn" @click="selectFiles">
          <el-icon :size="32"><Upload /></el-icon>
          <span>上传照片</span>
          <small>支持 jpg/png/gif/webp/bmp/svg</small>
        </div>
        <div class="upload-option-btn" @click="selectFolder">
          <el-icon :size="32"><FolderOpened /></el-icon>
          <span>上传文件夹</span>
          <small>自动提取文件夹内所有图片</small>
        </div>
      </div>

      <input
        ref="fileInputRef"
        type="file"
        accept="image/jpeg,image/png,image/gif,image/webp,image/bmp,image/svg+xml"
        multiple
        style="display: none"
        @change="onFilesSelected"
      />
      <input
        ref="folderInputRef"
        type="file"
        webkitdirectory
        multiple
        style="display: none"
        @change="onFilesSelected"
      />
    </div>

    <!-- Step 2: Preview & Options -->
    <div v-if="step === 2" class="upload-step">
      <div class="step-header">
        <h3>确认上传 ({{ selectedFiles.length }} 个文件)</h3>
        <p>设置可见性和相册，确认后开始上传</p>
      </div>

      <!-- File preview grid -->
      <div class="file-preview-grid">
        <div v-for="(file, index) in selectedFiles" :key="index" class="file-preview-item">
          <img :src="file.preview" class="preview-thumb" />
          <div class="file-info">
            <span class="file-name" :title="file.file.name">{{ file.file.name }}</span>
            <span class="file-size">{{ formatSize(file.file.size) }}</span>
          </div>
          <div class="remove-btn" @click="removeFile(index)">
            <el-icon :size="14"><Close /></el-icon>
          </div>
        </div>
      </div>

      <!-- Options -->
      <div class="upload-options-form">
        <div class="option-row">
          <label>可见性</label>
          <el-radio-group v-model="visibility">
            <el-radio-button :value="0">私密</el-radio-button>
            <el-radio-button :value="1">公开</el-radio-button>
          </el-radio-group>
        </div>
        <div class="option-row">
          <label>放入相册</label>
          <el-select v-model="albumId" placeholder="不放入相册" clearable style="width: 220px">
            <el-option
              v-for="album in albums"
              :key="album.id"
              :label="album.name"
              :value="album.id"
            />
          </el-select>
        </div>
      </div>

      <div class="step-actions">
        <el-button @click="step = 1" :disabled="isUploading">返回选择</el-button>
        <el-button type="primary" @click="startUpload" :disabled="selectedFiles.length === 0">
          确认上传
        </el-button>
      </div>
    </div>

    <!-- Step 3: Uploading -->
    <div v-if="step === 3" class="upload-step">
      <div class="step-header">
        <h3>{{ isUploading ? '正在上传...' : '上传完成' }}</h3>
        <p>{{ completedCount }} / {{ selectedFiles.length }} 个文件</p>
      </div>

      <div class="progress-section">
        <el-progress
          :percentage="uploadProgress"
          :status="isUploading ? undefined : 'success'"
          :stroke-width="12"
          striped
          striped-flow
        />
        <p class="progress-detail" v-if="currentFileName">
          正在处理: {{ currentFileName }}
        </p>
      </div>

      <div v-if="!isUploading && failedFiles.length" class="failed-list">
        <p class="failed-title">失败文件（{{ failedFiles.length }}）</p>
        <ul>
          <li v-for="(f, idx) in failedFiles" :key="idx">
            <span class="f-name" :title="f.name">{{ f.name }}</span>
            <span class="f-reason">{{ f.reason }}</span>
          </li>
        </ul>
      </div>

      <div class="step-actions" v-if="!isUploading">
        <el-button type="primary" @click="finishUpload">完成</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { uploadImage, getMyAlbums } from '@/api/image'
import type { AlbumResponse } from '@/api/image'
import { ElMessage } from 'element-plus'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'uploaded'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const fileInputRef = ref<HTMLInputElement>()
const folderInputRef = ref<HTMLInputElement>()

const step = ref(1)
const selectedFiles = ref<{ file: File; preview: string }[]>([])
const visibility = ref(0)
const albumId = ref<number | undefined>(undefined)
const albums = ref<AlbumResponse[]>([])

const isUploading = ref(false)
const completedCount = ref(0)
const currentFileName = ref('')
const failedFiles = ref<{ name: string; reason: string }[]>([])

const uploadProgress = computed(() => {
  if (selectedFiles.value.length === 0) return 0
  return Math.round((completedCount.value / selectedFiles.value.length) * 100)
})

const IMAGE_TYPES = [
  'image/jpeg', 'image/png', 'image/gif', 'image/webp',
  'image/bmp', 'image/svg+xml',
]

/** 单文件大小上限 8MB */
const MAX_FILE_SIZE = 8 * 1024 * 1024

watch(visible, (val) => {
  if (val) {
    resetState()
    loadAlbums()
  }
})

const resetState = () => {
  step.value = 1
  selectedFiles.value = []
  visibility.value = 0
  albumId.value = undefined
  isUploading.value = false
  completedCount.value = 0
  currentFileName.value = ''
}

const loadAlbums = async () => {
  try {
    const res = await getMyAlbums(1, 100)
    if (res.data) {
      albums.value = res.data.records || []
    }
  } catch {
    // ignore
  }
}

const selectFiles = () => fileInputRef.value?.click()
const selectFolder = () => folderInputRef.value?.click()

const onFilesSelected = (e: Event) => {
  const input = e.target as HTMLInputElement
  if (!input.files) return

  const imageFiles = Array.from(input.files).filter((f) =>
    IMAGE_TYPES.includes(f.type)
  )
  const oversize = imageFiles.filter((f) => f.size > MAX_FILE_SIZE)
  const files = imageFiles.filter((f) => f.size <= MAX_FILE_SIZE)

  if (oversize.length) {
    ElMessage.warning(`${oversize.length} 个文件超过 8MB，已跳过`)
  }

  if (files.length === 0) {
    if (oversize.length === 0) ElMessage.warning('未找到支持格式的图片文件')
    return
  }

  // Generate previews
  for (const file of files) {
    const preview = URL.createObjectURL(file)
    selectedFiles.value.push({ file, preview })
  }

  step.value = 2
  // Reset input
  input.value = ''
}

const removeFile = (index: number) => {
  URL.revokeObjectURL(selectedFiles.value[index].preview)
  selectedFiles.value.splice(index, 1)
  if (selectedFiles.value.length === 0) {
    step.value = 1
  }
}

/** 读取图片真实像素尺寸，读不到时返回空对象（不阻断上传） */
const readDimensions = (src: string): Promise<{ width?: number; height?: number }> =>
  new Promise((resolve) => {
    const probe = new Image()
    probe.onload = () => resolve({ width: probe.naturalWidth, height: probe.naturalHeight })
    probe.onerror = () => resolve({})
    probe.src = src
  })

const startUpload = async () => {
  isUploading.value = true
  step.value = 3
  completedCount.value = 0
  failedFiles.value = []

  let success = 0
  for (let i = 0; i < selectedFiles.value.length; i++) {
    const { file, preview } = selectedFiles.value[i]
    currentFileName.value = file.name

    try {
      const { width, height } = await readDimensions(preview)

      // 直接上传原始文件，落盘编码由服务端完成
      await uploadImage(file, {
        originalName: file.name,
        mimeType: file.type,
        fileSize: file.size,
        width,
        height,
        visibility: visibility.value,
        albumId: albumId.value,
      })
      success++
    } catch (error) {
      const reason = error instanceof Error ? error.message : '网络错误'
      failedFiles.value.push({ name: file.name, reason })
    } finally {
      URL.revokeObjectURL(preview)
    }
    completedCount.value = i + 1
  }

  if (success === selectedFiles.value.length) {
    ElMessage.success(`成功上传 ${success} 个文件`)
  } else if (success > 0) {
    ElMessage.warning(`上传完成：成功 ${success} 个，失败 ${failedFiles.value.length} 个`)
  } else {
    ElMessage.error('上传失败，请查看失败列表')
  }

  emit('uploaded')
  isUploading.value = false
  currentFileName.value = ''
}

const finishUpload = () => {
  visible.value = false
}

const formatSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<style scoped lang="scss">
.upload-dialog {
  :deep(.el-dialog) {
    background: var(--ev-bg-glass-strong);
    border: 1px solid var(--ev-border-subtle);
    border-top-color: var(--ev-border-gloss);
    border-radius: 22px;
    backdrop-filter: var(--ev-blur-lg);
    -webkit-backdrop-filter: var(--ev-blur-lg);
    box-shadow: var(--ev-shadow-float), var(--ev-inset-gloss);
  }

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    :deep(.el-dialog) {
      background: rgba(255, 255, 255, 0.96);
    }
  }

  :deep(.el-dialog__header) {
    border-bottom: 1px solid var(--ev-border-subtle);
    padding: 16px 24px;
  }

  :deep(.el-dialog__title) {
    color: var(--ev-text-primary);
    font-weight: 700;
  }

  :deep(.el-dialog__body) {
    padding: 24px;
  }
}

.upload-step {
  .step-header {
    margin-bottom: 20px;

    h3 {
      font-size: 18px;
      font-weight: 700;
      color: var(--ev-text-primary);
      margin-bottom: 4px;
    }

    p {
      font-size: 13px;
      color: var(--ev-text-muted);
    }
  }
}

.upload-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.upload-option-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 16px;
  background: var(--ev-bg-glass-light);
  border: 1.5px dashed var(--ev-border-hover);
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.3s var(--ev-ease-out);
  color: var(--ev-primary);

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
    border-color: var(--ev-primary);
    background: var(--ev-bg-tint);
    box-shadow: var(--ev-glow-violet);
  }
}

.file-preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
  max-height: 240px;
  overflow-y: auto;
  margin-bottom: 20px;
  padding: 4px;
}

.file-preview-item {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid var(--ev-border-subtle);

  .preview-thumb {
    width: 100%;
    aspect-ratio: 1;
    object-fit: cover;
    display: block;
    background: linear-gradient(135deg, var(--ev-mist), #fff);
    box-shadow: inset 0 0 0 1px var(--ev-border-default);
  }

  .file-info {
    padding: 6px 8px;

    .file-name {
      display: block;
      font-size: 11px;
      color: var(--ev-text-secondary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .file-size {
      font-size: 10px;
      color: var(--ev-text-muted);
    }
  }

  .remove-btn {
    position: absolute;
    top: 4px;
    right: 4px;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background: var(--ev-danger);
    color: var(--ev-text-on-accent);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    opacity: 0;
    transition: opacity 0.2s;
  }

  &:hover .remove-btn {
    opacity: 1;
  }
}

.upload-options-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px 0;
  border-top: 1px solid var(--ev-border-subtle);

  .option-row {
    display: flex;
    align-items: center;
    gap: 16px;

    label {
      font-size: 13px;
      font-weight: 600;
      color: var(--ev-text-secondary);
      min-width: 70px;
    }
  }
}

.step-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--ev-border-subtle);
}

.progress-section {
  padding: 20px 0;

  :deep(.el-progress-bar__outer) {
    background: var(--ev-mist);
  }

  :deep(.el-progress-bar__inner) {
    background: var(--ev-grad-aurora);
  }

  :deep(.el-progress.is-success .el-progress-bar__inner) {
    background: var(--ev-success);
  }

  :deep(.el-progress.is-exception .el-progress-bar__inner) {
    background: var(--ev-danger);
  }

  :deep(.el-progress__text) {
    color: var(--ev-text-secondary);
  }

  .progress-detail {
    margin-top: 12px;
    font-size: 13px;
    color: var(--ev-text-muted);
    text-align: center;
  }
}

.failed-list {
  margin-top: 8px;
  padding: 12px;
  border: 1px solid var(--ev-border-subtle);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.5);
  max-height: 160px;
  overflow-y: auto;

  .failed-title {
    margin: 0 0 8px;
    font-size: 13px;
    font-weight: 600;
    color: var(--ev-danger);
  }

  ul {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  li {
    display: flex;
    gap: 8px;
    align-items: baseline;
    font-size: 12px;
    padding: 3px 0;

    .f-name {
      flex: 0 0 auto;
      max-width: 200px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: var(--ev-text-secondary);
    }

    .f-reason {
      color: var(--ev-text-muted);
    }
  }
}
</style>
