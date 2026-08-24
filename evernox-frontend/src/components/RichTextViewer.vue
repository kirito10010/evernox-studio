<template>
  <div ref="rootRef" class="rich-viewer" v-html="safeHtml"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch, nextTick } from 'vue'
import DOMPurify from 'dompurify'

const props = defineProps<{
  /** 入库形态的 HTML：插图为 <img data-image-id="N"> */
  html: string
  /** 取图函数，由视图注入 */
  loader: (id: number) => Promise<string | null>
}>()

const safeHtml = ref('')
const rootRef = ref<HTMLElement | null>(null)

/**
 * 渲染前消毒
 *
 * 后端入库时已用 jsoup 白名单清过一遍，这里再清一次是纵深防御：库里的历史数据、
 * 或任何绕过后端写入的内容都不该在浏览器里获得执行机会。
 */
const sanitize = (raw: string) =>
  DOMPurify.sanitize(raw || '', {
    ALLOWED_TAGS: [
      'p', 'br', 'strong', 'b', 'em', 'i', 'u', 's', 'h1', 'h2', 'h3',
      'ul', 'ol', 'li', 'blockquote', 'pre', 'code', 'a', 'img', 'span',
    ],
    ALLOWED_ATTR: ['href', 'title', 'class', 'data-image-id'],
    ALLOWED_URI_REGEXP: /^https?:/i,
  })

/** 已填充的图片节点，卸载时把 src 清掉，ObjectURL 由视图层统一 revoke */
const filled = new Set<HTMLImageElement>()

const hydrateImages = async (root: HTMLElement) => {
  const imgs = Array.from(root.querySelectorAll<HTMLImageElement>('img[data-image-id]'))
  for (const img of imgs) {
    const id = Number(img.getAttribute('data-image-id'))
    if (!id) continue
    const url = await props.loader(id)
    if (!url) {
      img.classList.add('img-failed')
      continue
    }
    img.src = url
    filled.add(img)
  }
}

watch(
  () => props.html,
  async (value) => {
    safeHtml.value = sanitize(value)
    await nextTick()
    // v-html 渲染后才能拿到节点；外链统一补 noopener，防止目标页反向操纵本页
    const host = rootRef.value
    if (!host) return
    host.querySelectorAll('a[href]').forEach((a) => {
      a.setAttribute('target', '_blank')
      a.setAttribute('rel', 'noopener noreferrer')
    })
    await hydrateImages(host)
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  filled.forEach((img) => img.removeAttribute('src'))
  filled.clear()
})
</script>

<style scoped lang="scss">
.rich-viewer {
  font-size: 14px;
  line-height: 1.8;
  color: var(--el-text-color-primary);
  word-break: break-word;

  :deep(h1) { font-size: 20px; margin: 12px 0 8px; }
  :deep(h2) { font-size: 17px; margin: 12px 0 8px; }
  :deep(h3) { font-size: 15px; margin: 10px 0 6px; }
  :deep(p) { margin: 6px 0; }
  :deep(ul), :deep(ol) { padding-left: 22px; margin: 6px 0; }

  :deep(blockquote) {
    margin: 8px 0;
    padding: 4px 12px;
    border-left: 3px solid var(--el-border-color);
    color: var(--el-text-color-regular);
  }

  :deep(pre) {
    margin: 8px 0;
    padding: 10px 12px;
    border-radius: 8px;
    background: var(--el-fill-color-light);
    overflow-x: auto;
    font-size: 13px;
  }

  :deep(img) {
    max-width: 100%;
    border-radius: 8px;
    margin: 6px 0;
  }

  :deep(img.img-failed) {
    min-width: 80px;
    min-height: 60px;
    background: var(--el-fill-color-light);
  }

  :deep(a) {
    color: var(--el-color-primary);
    text-decoration: none;
  }
}
</style>
