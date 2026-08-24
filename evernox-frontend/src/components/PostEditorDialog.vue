<template>
  <el-dialog
    :model-value="modelValue"
    :title="editingPost ? '编辑帖子' : '发布帖子'"
    width="720px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form label-width="64px">
      <el-form-item label="圈子">
        <el-select v-model="circleId" placeholder="选择圈子" style="width: 260px" filterable>
          <el-option v-for="c in circles" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题">
        <el-input v-model="title" maxlength="100" show-word-limit placeholder="帖子标题" />
      </el-form-item>
      <el-form-item label="正文">
        <RichTextEditor
          ref="editorRef"
          v-model="content"
          :loader="decryptImage"
          :purpose="5"
          placeholder="写点内容，支持插图…"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">
        {{ editingPost ? '保存' : '发布' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import RichTextEditor from '@/components/RichTextEditor.vue'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import {
  createTopicPost,
  getTopicCircles,
  getTopicImageBlob,
  updateTopicPost,
} from '@/api/topic'
import type { TopicCircle, TopicPost } from '@/types/topic'

const props = defineProps<{
  modelValue: boolean
  defaultCircleId?: number | null
  editingPost?: TopicPost | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved'): void
}>()

const { decryptImage, clearCache } = useImageDecrypt(getTopicImageBlob)

const circles = ref<TopicCircle[]>([])
const circleId = ref<number | null>(null)
const title = ref('')
const content = ref('')
const submitting = ref(false)
const editorRef = ref<{ getHtml: () => string } | null>(null)

const loadCircles = async () => {
  const res = await getTopicCircles({ page: 1, size: 100, mine: false })
  circles.value = res.data?.records || []
}

const resetForm = () => {
  if (props.editingPost) {
    circleId.value = props.editingPost.circleId
    title.value = props.editingPost.title
    content.value = props.editingPost.content || ''
  } else {
    circleId.value = props.defaultCircleId ?? null
    title.value = ''
    content.value = ''
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      resetForm()
      loadCircles()
    }
  }
)

const onSubmit = async () => {
  if (!circleId.value) {
    ElMessage.warning('请选择圈子')
    return
  }
  if (!title.value.trim()) {
    ElMessage.warning('请输入标题')
    return
  }
  const finalContent = editorRef.value?.getHtml() ?? content.value
  if (!finalContent) {
    ElMessage.warning('请输入正文')
    return
  }
  submitting.value = true
  try {
    const data = { circleId: circleId.value, title: title.value.trim(), content: finalContent }
    if (props.editingPost) {
      await updateTopicPost(props.editingPost.id, data)
      ElMessage.success('保存成功')
    } else {
      await createTopicPost(data)
      ElMessage.success('发布成功')
    }
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    submitting.value = false
  }
}

onBeforeUnmount(clearCache)
</script>
