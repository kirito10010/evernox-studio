<template>
  <div class="rich-editor">
    <div ref="editorRef" class="editor-body"></div>
    <div class="editor-tip">
      <span>支持加粗、列表、引用、代码块与插图</span>
      <span v-if="uploading" class="uploading">图片上传中…</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import Quill from 'quill'
import 'quill/dist/quill.snow.css'
import { uploadImage } from '@/api/image'

const props = withDefaults(
  defineProps<{
    /** 正文 HTML，插图以 <img data-image-id="N"> 形式存储 */
    modelValue: string
    /** 取图函数，由视图注入（与图床页共用一套 ObjectURL 缓存） */
    loader: (id: number) => Promise<string | null>
    placeholder?: string
    /** 插图用途，默认 3(笔记插图)；公告传 4(公告插图) */
    purpose?: number
  }>(),
  { placeholder: '记点什么…', purpose: 3 }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const editorRef = ref<HTMLElement | null>(null)
const uploading = ref(false)
let quill: Quill | null = null

/**
 * blob URL → image.id
 *
 * 正文里不存任何可访问地址：编辑期用 ObjectURL 显示，保存时按这张表换回 data-image-id。
 * 不去扩展 Quill 的 image blot 保留自定义属性——那要覆写内部常量，升级易碎。
 */
const urlToId = new Map<string, number>()

/** 组件自己 emit 出去的值，用于区分外部赋值与自身编辑，避免回环重置光标 */
let lastEmitted = ''
/** 把库里的 HTML 还原成可预览的 HTML：给每个 data-image-id 补上临时 blob src */
const hydrate = async (html: string): Promise<string> => {
  if (!html) return ''
  const doc = new DOMParser().parseFromString(`<div>${html}</div>`, 'text/html')
  const imgs = Array.from(doc.querySelectorAll('img[data-image-id]'))
  for (const img of imgs) {
    const id = Number(img.getAttribute('data-image-id'))
    if (!id) {
      img.remove()
      continue
    }
    const url = await props.loader(id)
    if (!url) {
      // 图片已被删除或取流失败：直接去掉这个节点，正文其余部分照常可编辑
      img.remove()
      continue
    }
    urlToId.set(url, id)
    img.setAttribute('src', url)
  }
  return doc.body.firstElementChild?.innerHTML ?? ''
}

/** 把编辑器里的 HTML 转成入库形态：blob src 换成 data-image-id，未知图片直接丢掉 */
const dehydrate = (html: string): string => {
  const doc = new DOMParser().parseFromString(`<div>${html}</div>`, 'text/html')
  doc.querySelectorAll('img').forEach((img) => {
    const src = img.getAttribute('src') || ''
    const id = urlToId.get(src)
    if (!id) {
      img.remove()
      return
    }
    img.removeAttribute('src')
    img.setAttribute('data-image-id', String(id))
  })
  const inner = doc.body.firstElementChild?.innerHTML ?? ''
  // Quill 空内容是 <p><br></p>，统一成空串，免得「看着空的」笔记也算有正文
  return inner === '<p><br></p>' ? '' : inner
}
/** 走图床上传通道，拿到 id 后用 ObjectURL 就地预览 */
const pickAndInsertImage = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file || !quill) return
    uploading.value = true
    try {
      const res = await uploadImage(file, {
        originalName: file.name,
        mimeType: file.type || 'image/png',
        fileSize: file.size,
        visibility: 0,
        purpose: props.purpose,
      })
      const id = res.data?.id
      if (!id) throw new Error('上传未返回图片ID')
      const url = await props.loader(id)
      if (!url) throw new Error('图片取流失败')
      urlToId.set(url, id)
      const range = quill.getSelection(true)
      quill.insertEmbed(range?.index ?? quill.getLength(), 'image', url, 'user')
      quill.setSelection((range?.index ?? 0) + 1, 0)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '插入图片失败')
    } finally {
      uploading.value = false
    }
  }
  input.click()
}

onMounted(async () => {
  if (!editorRef.value) return

  // Quill 默认只放行 http/https/data 协议，blob: 会被 sanitize 掉
  const ImageFormat = Quill.import('formats/image') as unknown as {
    sanitize: (url: string) => string
  }
  ImageFormat.sanitize = (url: string) => url

  quill = new Quill(editorRef.value, {
    theme: 'snow',
    placeholder: props.placeholder,
    modules: {
      toolbar: {
        container: [
          [{ header: [1, 2, 3, false] }],
          ['bold', 'italic', 'underline', 'strike'],
          [{ list: 'ordered' }, { list: 'bullet' }],
          ['blockquote', 'code-block'],
          ['link', 'image'],
          ['clean'],
        ],
        handlers: { image: pickAndInsertImage },
      },
    },
  })
  quill.on('text-change', () => {
    if (!quill) return
    lastEmitted = dehydrate(quill.root.innerHTML)
    emit('update:modelValue', lastEmitted)
  })

  if (props.modelValue) {
    const html = await hydrate(props.modelValue)
    quill.clipboard.dangerouslyPasteHTML(html, 'silent')
    lastEmitted = props.modelValue
  }
})

// 外部换了一篇笔记（比如从列表点开另一条）才重新灌内容，自身编辑产生的回传忽略
watch(
  () => props.modelValue,
  async (value) => {
    if (!quill || value === lastEmitted) return
    const html = await hydrate(value || '')
    quill.setContents([], 'silent')
    if (html) quill.clipboard.dangerouslyPasteHTML(html, 'silent')
    lastEmitted = value
  }
)

onBeforeUnmount(() => {
  // ObjectURL 由视图层的 useImageDecrypt 统一 revoke，这里只清映射
  urlToId.clear()
  quill = null
})

defineExpose({
  /** 供父组件保存前主动取一次最终 HTML */
  getHtml: () => (quill ? dehydrate(quill.root.innerHTML) : ''),
})
</script>

<style scoped lang="scss">
.rich-editor {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.editor-body {
  min-height: 260px;
  background: var(--el-bg-color);

  :deep(.ql-editor) {
    min-height: 260px;
    font-size: 14px;
    line-height: 1.7;
  }

  :deep(.ql-editor img) {
    max-width: 100%;
    border-radius: 8px;
  }
}

.editor-tip {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.uploading {
  color: var(--el-color-primary);
}
</style>
