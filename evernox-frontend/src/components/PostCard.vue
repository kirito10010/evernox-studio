<template>
  <div class="post-card" @click="$emit('open', post)">
    <div class="post-head">
      <span class="head-author">{{ post.authorName || '匿名' }}</span>
      <span v-if="post.circleName" class="head-circle"># {{ post.circleName }}</span>
      <span class="head-time">{{ formatTime(post.createdAt) }}</span>
    </div>
    <div class="post-title">{{ post.title }}</div>
    <div v-if="post.content" class="post-content">
      <RichTextViewer :html="post.content" :loader="loader" />
    </div>
    <div class="post-actions">
      <button
        class="action-btn"
        :class="{ active: post.liked }"
        title="点赞"
        @click.stop="onLike"
      >
        <el-icon :size="14"><StarFilled v-if="post.liked" /><Star v-else /></el-icon>
        <span>{{ post.likeCount || 0 }}</span>
      </button>
      <button
        class="action-btn"
        :class="{ active: post.favorited }"
        title="收藏"
        @click.stop="onFavorite"
      >
        <el-icon :size="14"><CollectionTag /></el-icon>
        <span>{{ post.favoriteCount || 0 }}</span>
      </button>
      <span class="action-btn static" title="评论">
        <el-icon :size="14"><ChatDotRound /></el-icon>
        <span>{{ post.commentCount || 0 }}</span>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import RichTextViewer from '@/components/RichTextViewer.vue'
import { likePost, favoritePost } from '@/api/topic'
import type { TopicPost } from '@/types/topic'
import { formatTime } from '@/utils/format'

const props = defineProps<{
  post: TopicPost
  loader: (id: number) => Promise<string | null>
}>()

defineEmits<{
  (e: 'open', post: TopicPost): void
}>()

const onLike = async () => {
  const res = await likePost(props.post.id)
  if (res.data) {
    props.post.liked = res.data.liked
    props.post.likeCount = res.data.likeCount ?? props.post.likeCount
  }
}

const onFavorite = async () => {
  const res = await favoritePost(props.post.id)
  if (res.data) {
    props.post.favorited = res.data.favorited
    props.post.favoriteCount = res.data.favoriteCount ?? props.post.favoriteCount
  }
}

</script>

<style scoped lang="scss">
.post-card {
  position: relative;
  padding: 14px 16px 14px 20px;
  background: var(--el-bg-color);
  border: 1px solid var(--ev-border-subtle);
  border-radius: 14px;
  cursor: pointer;
  box-shadow: var(--ev-shadow-xs);
  transition: box-shadow 0.2s ease, transform 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--ev-shadow-md);
  }

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 16px;
    bottom: 16px;
    width: 3px;
    border-radius: 2px;
    background: var(--ev-grad-aurora);
  }

  .post-head {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 12px;
    color: var(--ev-text-muted);

    .head-author {
      font-weight: 600;
      color: var(--ev-primary);
    }

    .head-circle {
      color: var(--ev-primary);
    }

    .head-time {
      margin-left: auto;
    }
  }

  .post-title {
    margin-top: 8px;
    font-size: 16px;
    font-weight: 600;
    color: var(--ev-text-primary);
  }

  .post-content {
    margin-top: 8px;
  }

  .post-actions {
    display: flex;
    gap: 6px;
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px solid var(--ev-border-subtle);
  }

  .action-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 4px 10px;
    border: none;
    border-radius: 999px;
    background: var(--ev-bg-tint);
    color: var(--ev-text-secondary);
    font-size: 12px;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      background: var(--ev-fill-color);
    }

    &.active {
      color: var(--ev-primary);
      background: rgba(47, 124, 246, 0.1);
    }

    &.static {
      cursor: default;
    }
  }
}
</style>
