<template>
  <el-dialog
    :model-value="modelValue"
    :title="post?.title || '帖子详情'"
    width="640px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="detail-body">
      <template v-if="post">
        <div class="detail-meta">
          <span class="meta-author">{{ post.authorName || '匿名' }}</span>
          <span v-if="post.circleName" class="meta-circle"># {{ post.circleName }}</span>
          <span class="meta-time">{{ formatTime(post.createdAt) }}</span>
        </div>

        <RichTextViewer v-if="post.content" :html="post.content" :loader="decryptImage" />

        <div class="detail-actions">
          <el-button :type="post.liked ? 'primary' : 'default'" size="small" @click="onLike">
            <el-icon><StarFilled v-if="post.liked" /><Star v-else /></el-icon>
            点赞 {{ post.likeCount || 0 }}
          </el-button>
          <el-button :type="post.favorited ? 'primary' : 'default'" size="small" @click="onFavorite">
            <el-icon><CollectionTag /></el-icon>
            收藏 {{ post.favoriteCount || 0 }}
          </el-button>
          <el-button v-if="canDelete" size="small" type="danger" plain @click="onDelete">删除</el-button>
        </div>

        <div class="comment-section">
          <div class="comment-title">评论（{{ post.commentCount || 0 }}）</div>
          <div class="comment-input">
            <el-input
              v-model="commentInput"
              type="textarea"
              :rows="2"
              maxlength="500"
              placeholder="说点什么…"
            />
            <el-button type="primary" size="small" :loading="submitting" @click="onComment">发表</el-button>
          </div>
          <div class="comment-list">
            <div v-for="c in comments" :key="c.id" class="comment-item">
              <div class="comment-head">
                <span class="comment-author">{{ c.authorName || '匿名' }}</span>
                <span class="comment-time">{{ formatTime(c.createdAt) }}</span>
                <el-button
                  v-if="canDeleteComment(c)"
                  size="small"
                  text
                  type="danger"
                  @click="onDeleteComment(c)"
                >删除</el-button>
              </div>
              <div class="comment-content">{{ c.content }}</div>
            </div>
            <el-empty v-if="!comments.length && !loading" description="暂无评论" :image-size="60" />
            <div v-if="hasMoreComments" class="load-more">
              <el-button text @click="loadComments">加载更多</el-button>
            </div>
          </div>
        </div>
      </template>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import RichTextViewer from '@/components/RichTextViewer.vue'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { useUserStore } from '@/stores/user'
import {
  createComment,
  deleteComment,
  deleteTopicPost,
  favoritePost,
  getComments,
  getTopicImageBlob,
  getTopicPostDetail,
  likePost,
} from '@/api/topic'
import type { TopicComment, TopicPost } from '@/types/topic'

const props = defineProps<{
  modelValue: boolean
  postId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'changed'): void
}>()

const { decryptImage, clearCache } = useImageDecrypt(getTopicImageBlob)
const userStore = useUserStore()

const post = ref<TopicPost | null>(null)
const loading = ref(false)
const submitting = ref(false)
const comments = ref<TopicComment[]>([])
const commentPage = ref(1)
const commentTotal = ref(0)
const commentInput = ref('')
const COMMENT_SIZE = 10

const hasMoreComments = computed(() => comments.value.length < commentTotal.value)
const canDelete = computed(() => {
  if (!post.value) return false
  return post.value.userId === userStore.userInfo?.id || userStore.isAdmin
})

const loadPost = async () => {
  if (!props.postId) return
  loading.value = true
  try {
    const res = await getTopicPostDetail(props.postId)
    post.value = res.data
    await loadComments()
  } finally {
    loading.value = false
  }
}

const loadComments = async () => {
  if (!props.postId) return
  const res = await getComments(props.postId, commentPage.value, COMMENT_SIZE)
  comments.value = [...comments.value, ...(res.data?.records || [])]
  commentTotal.value = res.data?.total || 0
  commentPage.value += 1
}

const onLike = async () => {
  if (!post.value) return
  const res = await likePost(post.value.id)
  if (res.data) {
    post.value.liked = res.data.liked
    post.value.likeCount = res.data.likeCount ?? post.value.likeCount
  }
  emit('changed')
}

const onFavorite = async () => {
  if (!post.value) return
  const res = await favoritePost(post.value.id)
  if (res.data) {
    post.value.favorited = res.data.favorited
    post.value.favoriteCount = res.data.favoriteCount ?? post.value.favoriteCount
  }
  emit('changed')
}

const onComment = async () => {
  if (!commentInput.value.trim() || !post.value) return
  submitting.value = true
  try {
    await createComment({ postId: post.value.id, content: commentInput.value.trim() })
    commentInput.value = ''
    comments.value = []
    commentPage.value = 1
    await loadComments()
    post.value.commentCount = (post.value.commentCount || 0) + 1
    emit('changed')
  } finally {
    submitting.value = false
  }
}

const canDeleteComment = (c: TopicComment) => c.userId === userStore.userInfo?.id || userStore.isAdmin

const onDeleteComment = async (c: TopicComment) => {
  await ElMessageBox.confirm('确定删除这条评论吗？', '提示', { type: 'warning' })
  await deleteComment(c.id)
  comments.value = comments.value.filter((x) => x.id !== c.id)
  commentTotal.value = Math.max(0, commentTotal.value - 1)
  if (post.value) post.value.commentCount = Math.max(0, (post.value.commentCount || 1) - 1)
  emit('changed')
}

const onDelete = async () => {
  if (!post.value) return
  await ElMessageBox.confirm('确定删除这条帖子吗？', '提示', { type: 'warning' })
  await deleteTopicPost(post.value.id)
  ElMessage.success('删除成功')
  emit('changed')
  emit('update:modelValue', false)
}

watch(
  () => props.postId,
  () => {
    post.value = null
    comments.value = []
    commentPage.value = 1
    commentTotal.value = 0
    commentInput.value = ''
    if (props.postId) loadPost()
  },
  { immediate: true }
)

onBeforeUnmount(clearCache)

const formatTime = (value?: string) => {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}
</script>

<style scoped lang="scss">
.detail-body {
  max-height: 70vh;
  overflow-y: auto;

  .detail-meta {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 13px;
    color: var(--ev-text-muted);
    margin-bottom: 12px;

    .meta-author {
      font-weight: 600;
      color: var(--ev-text-primary);
    }

    .meta-circle {
      color: var(--ev-primary);
    }

    .meta-time {
      margin-left: auto;
    }
  }

  .detail-images {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    margin-bottom: 12px;

    .detail-image {
      border-radius: 8px;
      overflow: hidden;
    }
  }

  .detail-content {
    font-size: 14px;
    line-height: 1.8;
    color: var(--ev-text-primary);
    white-space: pre-wrap;
    word-break: break-word;
    margin-bottom: 14px;
  }

  .detail-actions {
    display: flex;
    gap: 8px;
    padding: 10px 0;
    border-top: 1px solid var(--ev-border-subtle);
    border-bottom: 1px solid var(--ev-border-subtle);
    margin-bottom: 16px;
  }

  .comment-section {
    .comment-title {
      font-size: 14px;
      font-weight: 600;
      color: var(--ev-text-primary);
      margin-bottom: 10px;
    }

    .comment-input {
      display: flex;
      gap: 10px;
      margin-bottom: 14px;

      .el-input {
        flex: 1;
      }
    }

    .comment-list {
      .comment-item {
        padding: 10px 0;
        border-bottom: 1px solid var(--ev-border-subtle);

        .comment-head {
          display: flex;
          align-items: center;
          gap: 10px;
          font-size: 12px;

          .comment-author {
            font-weight: 600;
            color: var(--ev-text-primary);
          }

          .comment-time {
            color: var(--ev-text-muted);
            flex: 1;
          }
        }

        .comment-content {
          margin-top: 4px;
          font-size: 13px;
          color: var(--ev-text-regular);
          white-space: pre-wrap;
          word-break: break-word;
        }
      }
    }

    .load-more {
      text-align: center;
      padding: 8px 0;
    }
  }
}
</style>
