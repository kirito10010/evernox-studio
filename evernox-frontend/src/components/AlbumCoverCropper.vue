<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="上传封面"
    width="600px"
    class="cover-cropper-dialog"
    :close-on-click-modal="!coverUploading"
    :close-on-press-escape="!coverUploading"
    :show-close="!coverUploading"
  >
    <div class="cropper-body">
      <input
        ref="fileInputRef"
        type="file"
        accept="image/jpeg,image/png,image/webp,image/bmp,image/gif"
        style="display: none"
        @change="handleFileSelect"
      />

      <div v-if="!previewUrl" class="upload-area" @click="fileInputRef?.click()">
        <el-icon :size="48"><Upload /></el-icon>
        <p>点击选择图片</p>
        <span class="upload-hint">支持 JPG / PNG / WebP / BMP / GIF，最大 20MB</span>
      </div>

      <template v-else>
        <div
          ref="cropContainerRef"
          class="crop-container"
          :style="{ aspectRatio: `${aspect}` }"
          @pointerdown="handlePointerDown"
          @wheel.prevent="handleWheel"
        >
          <img
            ref="previewImgRef"
            :src="previewUrl"
            :style="{
              width: displayWidth + 'px',
              height: displayHeight + 'px',
              transform: `translate(${imgLeft}px, ${imgTop}px)`,
            }"
            @load="onImageLoad"
            @error="onImageError"
            draggable="false"
          />
        </div>

        <div class="zoom-control">
          <span>缩放</span>
          <el-slider
            v-model="scale"
            :min="1"
            :max="ZOOM_MAX"
            :step="0.01"
            :disabled="coverUploading"
            :format-tooltip="(v: number) => `${v.toFixed(1)}x`"
          />
          <span class="zoom-value">{{ scale.toFixed(1) }}x</span>
        </div>
      </template>
    </div>

    <template #footer>
      <el-button v-if="previewUrl" @click="resetCropper" :disabled="coverUploading">重新选择</el-button>
      <el-button v-else @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :disabled="!previewUrl || coverUploading"
        :loading="coverUploading"
        @click="handleConfirm"
      >
        确认
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { cropImageToBlob, type CropRect } from '@/utils/image'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    aspect?: number
    outputWidth?: number
  }>(),
  {
    aspect: 1.6,
    outputWidth: 1600,
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'cropped', file: File): void
}>()

const outputHeight = computed(() => Math.round(props.outputWidth / props.aspect))

/** 最大放大倍数 */
const ZOOM_MAX = 8

const fileInputRef = ref<HTMLInputElement | null>(null)
const cropContainerRef = ref<HTMLElement | null>(null)
const previewImgRef = ref<HTMLImageElement | null>(null)

const previewUrl = ref<string | null>(null)
const coverUploading = ref(false)

const scale = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)

const naturalWidth = ref(0)
const naturalHeight = ref(0)
const baseScale = ref(1)

const containerWidth = ref(0)
const containerHeight = ref(0)

const displayWidth = computed(() => naturalWidth.value * baseScale.value * scale.value)
const displayHeight = computed(() => naturalHeight.value * baseScale.value * scale.value)

/** 图片左上角相对裁剪框左上角的像素坐标（居中 + 偏移） */
const imgLeft = computed(() => (containerWidth.value - displayWidth.value) / 2 + offsetX.value)
const imgTop = computed(() => (containerHeight.value - displayHeight.value) / 2 + offsetY.value)

const maxOffsetX = computed(() => Math.max(0, (displayWidth.value - containerWidth.value) / 2))
const maxOffsetY = computed(() => Math.max(0, (displayHeight.value - containerHeight.value) / 2))

const clampOffset = () => {
  offsetX.value = Math.max(-maxOffsetX.value, Math.min(maxOffsetX.value, offsetX.value))
  offsetY.value = Math.max(-maxOffsetY.value, Math.min(maxOffsetY.value, offsetY.value))
}

/** 1x 时图片刚好覆盖裁剪框（cover 语义） */
const recomputeBaseScale = () => {
  if (!naturalWidth.value || !naturalHeight.value || !containerWidth.value) return
  baseScale.value = Math.max(
    containerWidth.value / naturalWidth.value,
    containerHeight.value / naturalHeight.value
  )
}

watch(scale, () => {
  clampOffset()
})

// 裁剪框尺寸随弹窗动画 / 窗口 resize 变化，需持续跟踪
let containerObserver: ResizeObserver | null = null

watch(
  cropContainerRef,
  (el) => {
    containerObserver?.disconnect()
    containerObserver = null
    if (!el) return

    const measure = () => {
      const rect = el.getBoundingClientRect()
      containerWidth.value = rect.width
      containerHeight.value = rect.height
      recomputeBaseScale()
      clampOffset()
    }

    measure()
    containerObserver = new ResizeObserver(measure)
    containerObserver.observe(el)
  },
  { immediate: true, flush: 'post' }
)

const handleFileSelect = (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    input.value = ''
    return
  }

  if (file.type === 'image/svg+xml') {
    ElMessage.warning('暂不支持 SVG 格式')
    input.value = ''
    return
  }

  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 20MB')
    input.value = ''
    return
  }

  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }

  previewUrl.value = URL.createObjectURL(file)
  scale.value = 1
  offsetX.value = 0
  offsetY.value = 0
  input.value = ''
}

const onImageLoad = () => {
  const img = previewImgRef.value
  if (!img) return

  naturalWidth.value = img.naturalWidth
  naturalHeight.value = img.naturalHeight

  recomputeBaseScale()
  offsetX.value = 0
  offsetY.value = 0
  scale.value = 1
}

const onImageError = () => {
  ElMessage.error('图片无法读取')
  resetCropper()
}

const resetCropper = () => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = null
  scale.value = 1
  offsetX.value = 0
  offsetY.value = 0
}

const isDragging = ref(false)
const dragStartX = ref(0)
const dragStartY = ref(0)
const dragStartOffsetX = ref(0)
const dragStartOffsetY = ref(0)

const handlePointerDown = (e: PointerEvent) => {
  if (coverUploading.value) return
  if (maxOffsetX.value === 0 && maxOffsetY.value === 0) return

  const target = e.target as HTMLElement
  target.setPointerCapture(e.pointerId)

  isDragging.value = true
  dragStartX.value = e.clientX
  dragStartY.value = e.clientY
  dragStartOffsetX.value = offsetX.value
  dragStartOffsetY.value = offsetY.value

  target.addEventListener('pointermove', handlePointerMove)
  target.addEventListener('pointerup', handlePointerUp)
}

const handlePointerMove = (e: PointerEvent) => {
  if (!isDragging.value) return

  const deltaX = e.clientX - dragStartX.value
  const deltaY = e.clientY - dragStartY.value

  offsetX.value = dragStartOffsetX.value + deltaX
  offsetY.value = dragStartOffsetY.value + deltaY

  clampOffset()
}

const handlePointerUp = (e: PointerEvent) => {
  const target = e.target as HTMLElement
  target.releasePointerCapture(e.pointerId)
  target.removeEventListener('pointermove', handlePointerMove)
  target.removeEventListener('pointerup', handlePointerUp)
  isDragging.value = false
}

const handleWheel = (e: WheelEvent) => {
  if (coverUploading.value) return

  const factor = e.deltaY > 0 ? 0.9 : 1 / 0.9
  scale.value = Math.max(1, Math.min(ZOOM_MAX, scale.value * factor))
}

const handleConfirm = async () => {
  const img = previewImgRef.value
  if (!img || !previewUrl.value) return

  coverUploading.value = true
  try {
    const totalScale = baseScale.value * scale.value

    const sw = containerWidth.value / totalScale
    const sh = containerHeight.value / totalScale
    const rawSx = (naturalWidth.value - sw) / 2 - offsetX.value / totalScale
    const rawSy = (naturalHeight.value - sh) / 2 - offsetY.value / totalScale

    // 浮点误差可能让源区域越界，drawImage 越界行为跨浏览器不一致
    const sx = Math.max(0, Math.min(naturalWidth.value - sw, rawSx))
    const sy = Math.max(0, Math.min(naturalHeight.value - sh, rawSy))

    const rect: CropRect = { sx, sy, sw, sh }

    const blob = await cropImageToBlob(img, rect, props.outputWidth, outputHeight.value, 0.9)

    const file = new File([blob], `cover-${Date.now()}.jpg`, { type: 'image/jpeg' })

    emit('cropped', file)
    ElMessage.success('封面已生成')
    resetCropper()
    emit('update:modelValue', false)
  } catch {
    ElMessage.error('生成封面失败')
  } finally {
    coverUploading.value = false
  }
}

watch(
  () => props.modelValue,
  (val) => {
    if (!val && previewUrl.value) {
      URL.revokeObjectURL(previewUrl.value)
      previewUrl.value = null
    }
  }
)

onUnmounted(() => {
  containerObserver?.disconnect()
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
})
</script>

<style scoped lang="scss">
.cropper-body {
  min-height: 300px;
}

.upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  height: 300px;
  border: 2px dashed var(--el-border-color);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s;

  &:hover {
    border-color: var(--el-color-primary);
  }

  p {
    margin: 0;
    font-size: 16px;
    color: var(--el-text-color-regular);
  }

  .upload-hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.crop-container {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: 8px;
  background: #000;
  cursor: grab;

  &:active {
    cursor: grabbing;
  }

img {
    position: absolute;
    top: 0;
    left: 0;
    transform-origin: 0 0;
    pointer-events: none;
    user-select: none;
  }
}

.zoom-control {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;

  span {
    font-size: 14px;
    color: var(--el-text-color-regular);
    white-space: nowrap;
  }

  .zoom-value {
    min-width: 40px;
    text-align: right;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-secondary);
  }

  :deep(.el-slider) {
    flex: 1;
  }
}
</style>
