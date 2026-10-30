<template>
  <div v-if="userStore.isLoggedIn" class="topic-mine">
    <div class="page-header">
      <div class="hero-text">
        <h2>我的</h2>
        <p>{{ activeTab === 'posts' ? '我发布的帖子' : '我收藏的帖子' }}</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="我的帖子" name="posts" />
      <el-tab-pane label="我的收藏" name="favorites" />
    </el-tabs>

    <div v-loading="loading" class="post-grid">
      <PostCard v-for="p in posts" :key="p.id" :post="p" :loader="decryptImage" @open="openDetail" />
    </div>

    <el-empty
      v-if="!loading && !posts.length"
      :description="activeTab === 'posts' ? '还没有发帖' : '还没有收藏'"
    />

    <div class="list-footer">
      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="load"
        @size-change="reload"
      />
    </div>

    <PostDetailDialog v-model="detailVisible" :post-id="detailPostId" @changed="load" />
  </div>
  <AuthRequired
    v-else
    title="登录后查看我的话题"
    description="登录后可查看自己发布的帖子和收藏的内容。"
  />
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import PostCard from '@/components/PostCard.vue'
import PostDetailDialog from '@/components/PostDetailDialog.vue'
import AuthRequired from '@/components/auth/AuthRequired.vue'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { getMyFavorites, getMyPosts, getTopicImageBlob } from '@/api/topic'
import { useUserStore } from '@/stores/user'
import type { TopicPost } from '@/types/topic'

const userStore = useUserStore()
const { decryptImage, clearCache } = useImageDecrypt(getTopicImageBlob)

const activeTab = ref<'posts' | 'favorites'>('posts')
const posts = ref<TopicPost[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const loading = ref(false)

const detailVisible = ref(false)
const detailPostId = ref<number | null>(null)

const load = async () => {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    const res =
      activeTab.value === 'posts'
        ? await getMyPosts(page.value, size.value)
        : await getMyFavorites(page.value, size.value)
    posts.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const reload = () => {
  page.value = 1
  load()
}

const onTabChange = () => {
  reload()
}

const openDetail = (post: TopicPost) => {
  detailPostId.value = post.id
  detailVisible.value = true
}

watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) void load()
  }
)

load()
onBeforeUnmount(clearCache)
</script>

<style scoped lang="scss">
.topic-mine {
  .page-header {
    margin-bottom: 12px;
    padding: 22px 24px;
    border-radius: 16px;
    background: linear-gradient(135deg, rgba(47, 124, 246, 0.1), rgba(79, 195, 232, 0.08));
    border: 1px solid var(--ev-border-subtle);

    h2 {
      margin: 0;
      font-size: 22px;
      font-weight: 800;
      color: var(--ev-text-primary);
    }

    p {
      margin: 6px 0 0;
      font-size: 13px;
      color: var(--ev-text-muted);
    }
  }

  .post-grid {
    display: flex;
    flex-direction: column;
    gap: 16px;
    margin-top: 16px;
    max-width: 760px;
  }

  .list-footer {
    margin-top: 18px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
