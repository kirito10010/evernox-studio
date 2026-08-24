<template>
  <div ref="rootRef" class="lazy-image" :style="effectiveRatio ? { aspectRatio: effectiveRatio } : undefined">
    <img v-if="url" :src="url" :alt="alt" class="lazy-img" @load="onImgLoad" />
    <div v-else-if="failed" class="lazy-state lazy-failed" @click.stop="retry" title="加载失败，点击重试">
      <el-icon :size="20"><Refresh /></el-icon>
    </div>
    <div v-else class="lazy-state lazy-placeholder">
      <el-icon :size="22"><Picture /></el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { Picture, Refresh } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    /** 图片 ID */
    imageId: number
    /** 加载函数，由视图注入（视图持有 useImageDecrypt 实例，缓存与生命周期不变） */
    loader: (id: number) => Promise<string | null>
    /** 占位比例，如 '4 / 3'，避免加载前后布局跳动 */
    ratio?: string
    alt?: string
    /** 提前多少距离开始加载 */
    rootMargin?: string
  }>(),
  {
    rootMargin: '300px',
  }
)

const emit = defineEmits<{
  (e: 'loaded', payload: { id: number; url: string; width: number; height: number }): void
}>()

const rootRef = ref<HTMLElement | null>(null)
const url = ref<string | null>(null)
const failed = ref(false)
const loading = ref(false)

/**
 * 解码后测得的真实比例，优先级高于 props.ratio
 *
 * props.ratio 来自后端 width/height，而那两个字段是上传时由浏览器测量后提交的，
 * 可能因 EXIF 方向或历史数据而与实际像素不符。容器比例一旦和图片不一致，
 * 卡片下方就会露出空白，所以图片解码完成后必须用真实尺寸把它纠正过来。
 */
const measuredRatio = ref<string | null>(null)
const effectiveRatio = computed(() => measuredRatio.value ?? props.ratio)

const onImgLoad = (event: Event) => {
  const img = event.target as HTMLImageElement
  const width = img.naturalWidth
  const height = img.naturalHeight
  // 解码异常时读到 0，此时保留占位比例，不要写坏容器
  if (!width || !height) return
  measuredRatio.value = `${width} / ${height}`
  emit('loaded', { id: props.imageId, url: img.src, width, height })
}


let observer: IntersectionObserver | null = null

const disconnect = () => {
  observer?.disconnect()
  observer = null
}

const load = async () => {
  if (loading.value || url.value) return
  loading.value = true
  failed.value = false
  // imageId 的 watch 会把 loading 重置，使新一轮 load 得以并发进入。
  // 不认领请求归属的话，先发出的旧图请求后返回时会盖掉新图，正是串图的成因。
  const requestedId = props.imageId
  try {
    const result = await props.loader(requestedId)
    if (requestedId !== props.imageId) return
    if (result) {
      // loaded 事件推迟到 <img> 真正解码完成时再发，那时才能读到 naturalWidth
      url.value = result
    } else {
      failed.value = true
    }
  } catch {
    if (requestedId !== props.imageId) return
    failed.value = true
  } finally {
    if (requestedId === props.imageId) loading.value = false
  }
}

const retry = () => {
  failed.value = false
  void load()
}

const observe = (el: HTMLElement | null) => {
  disconnect()
  if (!el || url.value) return

  // 极旧浏览器兜底：直接加载，退化为非懒加载行为
  if (typeof IntersectionObserver === 'undefined') {
    void load()
    return
  }

  observer = new IntersectionObserver(
    (entries) => {
      if (!entries[0]?.isIntersecting) return
      // 命中即断开，避免滚动过程反复触发
      disconnect()
      void load()
    },
    { rootMargin: props.rootMargin }
  )
  observer.observe(el)
}

watch(rootRef, (el) => observe(el), { immediate: true, flush: 'post' })

// 分页切换时组件实例可能被复用，需重置状态并重新观察
watch(
  () => props.imageId,
  () => {
    url.value = null
    failed.value = false
    loading.value = false
    // 不重置的话新图会沿用上一张图的比例，正是本次要修的那类错位
    measuredRatio.value = null
    observe(rootRef.value)
  }
)

onUnmounted(disconnect)
</script>

<style scoped lang="scss">
.lazy-image {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: var(--ev-bg-tint);
}

.lazy-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  animation: lazy-fade-in 0.35s ease both;
}

.lazy-state {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ev-text-muted);
}

.lazy-failed {
  cursor: pointer;
  color: var(--ev-danger);

  &:hover {
    background: rgba(242, 99, 127, 0.08);
  }
}

@keyframes lazy-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
