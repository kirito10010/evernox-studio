<template>
  <div class="release-uploader">
    <div
      class="drop-zone"
      :class="{ 'is-dragging': dragging }"
      @dragenter.prevent="dragging = true"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
      @click="pickFiles"
    >
      <el-icon class="drop-icon"><UploadFilled /></el-icon>
      <div class="drop-title">拖拽安装包 / blockmap 到这里</div>
      <div class="drop-tip">或点击选择文件 · 支持多选 · 单文件 ≤ 2GB · 4MB 分片上传</div>
      <input ref="inputRef" type="file" multiple class="file-input" @change="onInputChange" />
    </div>

    <div v-if="queue.length" class="queue-list">
      <div v-for="item in queue" :key="item.uid" class="queue-row">
        <el-icon class="file-icon"><Document /></el-icon>
        <div class="queue-main">
          <div class="queue-head">
            <span class="file-name" :title="item.file.name">{{ item.file.name }}</span>
            <span class="file-size">{{ formatBytes(item.file.size) }}</span>
          </div>
          <el-progress
            :percentage="item.progress"
            :stroke-width="4"
            :status="item.status === 'error' ? 'exception' : undefined"
            :show-text="false"
          />
          <div class="queue-meta">
            <span v-if="item.status === 'waiting'">等待上传</span>
            <span v-else-if="item.status === 'uploading'">
              上传中 {{ item.progress }}%<template v-if="item.speed"> · {{ item.speed }}</template>
            </span>
            <span v-else-if="item.status === 'hashing'">正在计算 SHA-512…</span>
            <span v-else-if="item.status === 'error'" class="error-text">{{ item.error || '上传失败' }}</span>
          </div>
        </div>
        <div class="queue-actions">
          <el-button
            v-if="item.status === 'error'"
            link
            type="primary"
            size="small"
            @click.stop="retry(item)"
          >
            重试
          </el-button>
          <el-button link type="danger" size="small" @click.stop="cancel(item)">取消</el-button>
        </div>
      </div>
    </div>

    <div v-if="assets.length" class="asset-list">
      <div v-for="asset in assets" :key="asset.id" class="asset-row">
        <el-icon class="file-icon done"><CircleCheck /></el-icon>
        <div class="asset-info">
          <div class="file-name" :title="asset.fileName">{{ asset.fileName }}</div>
          <div class="asset-meta">
            {{ formatBytes(asset.size) }}
            <span class="dot">·</span>
            sha512 {{ shortSha512(asset.sha512) }}
          </div>
        </div>
        <el-button link type="danger" size="small" @click.stop="removeAsset(asset)">移除</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  cancelReleaseUpload,
  completeReleaseUpload,
  deleteStagedReleaseAsset,
  initReleaseUpload,
  uploadReleaseChunk,
} from '@/api/adminRelease'
import type { ReleaseAsset } from '@/types/release'
import { formatBytes } from '@/utils/image'
import { shortSha512, validateFileName } from '@/utils/release'

type UploadStatus = 'waiting' | 'uploading' | 'hashing' | 'done' | 'error'

interface UploadItem {
  uid: string
  file: File
  status: UploadStatus
  progress: number
  speed: string
  error?: string
  uploadId?: string
  cancelled: boolean
}

const props = defineProps<{
  projectId: number
  modelValue: ReleaseAsset[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: ReleaseAsset[]): void
  (e: 'uploading-change', value: boolean): void
}>()

const MAX_FILE_SIZE = 2 * 1024 * 1024 * 1024
const MAX_RETRY = 2

const inputRef = ref<HTMLInputElement>()
const dragging = ref(false)
const queue = ref<UploadItem[]>([])
const processing = ref(false)
const stagedIds = ref(new Set<number>())

const assets = computed(() => props.modelValue)
const isUploading = computed(
  () => processing.value || queue.value.some((item) => item.status !== 'error')
)

watch(isUploading, (value) => emit('uploading-change', value), { immediate: true })

function pickFiles() {
  inputRef.value?.click()
}

function onInputChange(event: Event) {
  const input = event.target as HTMLInputElement
  addFiles(Array.from(input.files || []))
  input.value = ''
}

function onDrop(event: DragEvent) {
  dragging.value = false
  addFiles(Array.from(event.dataTransfer?.files || []))
}

function addFiles(files: File[]) {
  const accepted: File[] = []
  for (const file of files) {
    const nameError = validateFileName(file.name)
    if (nameError) {
      ElMessage.warning(`${file.name}：${nameError}`)
      continue
    }
    if (file.size <= 0) {
      ElMessage.warning(`${file.name}：文件为空`)
      continue
    }
    if (file.size > MAX_FILE_SIZE) {
      ElMessage.warning(`${file.name}：超过 2GB 上限`)
      continue
    }
    const duplicated =
      assets.value.some((asset) => asset.fileName === file.name) ||
      queue.value.some((item) => item.file.name === file.name)
    if (duplicated) {
      ElMessage.warning(`${file.name}：同名附件已存在，请先移除旧附件再上传`)
      continue
    }
    accepted.push(file)

  }

  for (const file of accepted) {
    queue.value.push({
      uid: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      file,
      status: 'waiting',
      progress: 0,
      speed: '',
      cancelled: false,
    })
  }
  if (accepted.length) void processQueue()
}

async function processQueue() {
  if (processing.value) return
  processing.value = true
  try {
    const pending = [...queue.value]
    for (const item of pending) {
      if (item.cancelled || item.status === 'done' || item.status === 'error') continue
      await uploadOne(item)
    }
  } finally {
    processing.value = false
  }
}

async function uploadOne(item: UploadItem) {
  item.status = 'uploading'
  item.progress = 0
  item.error = undefined
  const started = Date.now()

  try {
    const initRes = await initReleaseUpload(props.projectId, item.file.name, item.file.size)
    if (!initRes.data) throw new Error('初始化上传失败')
    const { uploadId, chunkSize, totalChunks } = initRes.data
    item.uploadId = uploadId

    for (let index = 0; index < totalChunks; index++) {
      if (item.cancelled) {
        await cancelReleaseUpload(uploadId).catch(() => undefined)
        return
      }
      const start = index * chunkSize
      const blob = item.file.slice(start, Math.min(start + chunkSize, item.file.size))
      let attempt = 0
      for (;;) {
        try {
          await uploadReleaseChunk(uploadId, index, blob, (event) => {
            const loaded = start + (event.loaded || 0)
            item.progress = Math.min(99, Math.round((loaded / item.file.size) * 100))
            const seconds = Math.max((Date.now() - started) / 1000, 0.2)
            item.speed = `${formatBytes(loaded / seconds)}/s`
          })
          break
        } catch (error) {
          attempt += 1
          if (attempt > MAX_RETRY) throw error
          await sleep(600 * attempt)
        }
      }
    }

    item.status = 'hashing'
    item.progress = 99
    const done = await completeReleaseUpload(uploadId)
    if (!done.data) throw new Error('上传完成但未返回附件信息')

    item.progress = 100
    item.status = 'done'
    stagedIds.value.add(done.data.id)
    emit('update:modelValue', [...props.modelValue, done.data])
    queue.value = queue.value.filter((row) => row.uid !== item.uid)
  } catch {
    item.status = 'error'
    item.error = '上传失败，可重试'
  }
}

function retry(item: UploadItem) {
  item.status = 'waiting'
  item.error = undefined
  item.cancelled = false
  item.progress = 0
  void processQueue()
}

async function cancel(item: UploadItem) {
  item.cancelled = true
  queue.value = queue.value.filter((row) => row.uid !== item.uid)
  if (item.uploadId) {
    await cancelReleaseUpload(item.uploadId).catch(() => undefined)
  }
}

async function removeAsset(asset: ReleaseAsset) {
  emit('update:modelValue', props.modelValue.filter((row) => row.id !== asset.id))
  if (stagedIds.value.has(asset.id)) {
    stagedIds.value.delete(asset.id)
    await deleteStagedReleaseAsset(asset.id).catch(() => undefined)
  }
}

function sleep(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}
</script>

<style scoped lang="scss">
.release-uploader {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.drop-zone {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 22px 16px;
  border: 1px dashed var(--ev-border-default);
  border-radius: 14px;
  background: rgba(var(--ev-primary-rgb), 0.025);
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;

  &:hover,
  &.is-dragging {
    border-color: rgba(var(--ev-primary-rgb), 0.55);
    background: rgba(var(--ev-primary-rgb), 0.07);
  }

  .drop-icon {
    font-size: 26px;
    color: var(--ev-primary);
  }

  .drop-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--ev-text-primary);
  }

  .drop-tip {
    font-size: 11px;
    color: var(--ev-text-muted);
  }

  .file-input {
    display: none;
  }
}

.queue-list,
.asset-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.queue-row,
.asset-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--ev-border-subtle);
  border-radius: 10px;
  background: var(--ev-bg-surface);
}

.file-icon {
  font-size: 18px;
  color: var(--ev-text-muted);
  flex-shrink: 0;

  &.done {
    color: var(--el-color-success);
  }
}

.queue-main,
.asset-info {
  flex: 1;
  min-width: 0;
}

.queue-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}

.file-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ev-text-primary);
}

.file-size,
.asset-meta {
  font-size: 11px;
  color: var(--ev-text-muted);
  white-space: nowrap;
}

.queue-meta {
  margin-top: 4px;
  font-size: 11px;
  color: var(--ev-text-muted);
}

.error-text {
  color: var(--el-color-danger);
}

.queue-actions {
  display: flex;
  flex-shrink: 0;
  gap: 2px;
}

.asset-meta {
  margin-top: 2px;

  .dot {
    margin: 0 5px;
    opacity: 0.6;
  }
}
</style>
