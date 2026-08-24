<template>
  <div class="circle-detail">
    <div v-loading="circleLoading" class="circle-header">
      <template v-if="circle">
        <div class="circle-info">
          <h2>{{ circle.name }}</h2>
          <p class="circle-desc">{{ circle.description || '暂无简介' }}</p>
          <div class="circle-stats">
            <span>{{ circle.memberCount }} 成员</span>
            <span>{{ circle.postCount }} 帖子</span>
            <span>圈主 {{ circle.ownerName }}</span>
          </div>
        </div>
        <div class="circle-actions">
          <el-button type="primary" @click="openEditor">发帖</el-button>
          <el-button :type="circle.followed ? 'default' : 'primary'" plain @click="toggleFollow">
            {{ circle.followed ? '已关注' : '关注' }}
          </el-button>
          <el-button v-if="isOwner" plain @click="openTransfer">转让圈子</el-button>
          <el-button v-if="isOwner" type="danger" plain @click="handleDisband">解散圈子</el-button>
        </div>
      </template>
    </div>

    <div v-loading="loading" class="post-list">
      <PostCard v-for="p in posts" :key="p.id" :post="p" :loader="decryptImage" @open="openDetail" />
    </div>

    <el-empty v-if="!loading && !posts.length" description="这个圈子还没有帖子" />

    <div class="list-footer">
      <div :ref="(el) => (sentinelRef = el as HTMLElement | null)" class="sentinel"></div>
      <span v-if="!hasMore && posts.length" class="footer-tip">没有更多了</span>
    </div>

    <PostEditorDialog v-model="editorVisible" :default-circle-id="circleId" @saved="reload" />
    <PostDetailDialog v-model="detailVisible" :post-id="detailPostId" @changed="reload" />

    <el-dialog v-model="transferVisible" title="转让圈子" width="420px" append-to-body>
      <p class="transfer-tip">选择一位成员作为新圈主</p>
      <div class="member-list">
        <div
          v-for="m in members"
          :key="m.userId"
          class="member-item"
          :class="{ selected: transferTarget === m.userId }"
          @click="transferTarget = m.userId"
        >
          <span>{{ m.username }}</span>
          <el-tag v-if="m.isOwner" size="small" type="warning">圈主</el-tag>
        </div>
        <el-empty v-if="!members.length" description="暂无成员" :image-size="50" />
      </div>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="transferring" :disabled="!transferTarget" @click="submitTransfer">
          确认转让
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PostCard from '@/components/PostCard.vue'
import PostDetailDialog from '@/components/PostDetailDialog.vue'
import PostEditorDialog from '@/components/PostEditorDialog.vue'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import { useUserStore } from '@/stores/user'
import {
  deleteTopicCircle,
  followCircle,
  getCircleMembers,
  getCirclePosts,
  getTopicCircleDetail,
  getTopicImageBlob,
  transferCircle,
  unfollowCircle,
} from '@/api/topic'
import type { TopicCircle, TopicMember, TopicPost } from '@/types/topic'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const circleId = Number(route.params.id)

const { decryptImage, clearCache } = useImageDecrypt(getTopicImageBlob)

const circle = ref<TopicCircle | null>(null)
const circleLoading = ref(false)
const posts = ref<TopicPost[]>([])
const page = ref(1)
const hasMore = ref(true)
const loading = ref(false)

const editorVisible = ref(false)
const detailVisible = ref(false)
const detailPostId = ref<number | null>(null)

const transferVisible = ref(false)
const members = ref<TopicMember[]>([])
const transferTarget = ref<number | null>(null)
const transferring = ref(false)

const isOwner = computed(() => {
  if (!circle.value) return false
  return circle.value.ownerId === userStore.userInfo?.id || userStore.isAdmin
})

const loadCircle = async () => {
  circleLoading.value = true
  try {
    const res = await getTopicCircleDetail(circleId)
    circle.value = res.data
  } finally {
    circleLoading.value = false
  }
}

const loadMore = async () => {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const res = await getCirclePosts(circleId, page.value, 12)
    const records = res.data?.records || []
    posts.value = [...posts.value, ...records]
    hasMore.value = records.length > 0 && posts.value.length < (res.data?.total || 0)
    page.value += 1
  } finally {
    loading.value = false
    recheck()
  }
}

const reload = () => {
  posts.value = []
  page.value = 1
  hasMore.value = true
  loadCircle()
  loadMore()
}

const toggleFollow = async () => {
  if (!circle.value) return
  if (circle.value.followed) {
    await unfollowCircle(circleId)
    circle.value.followed = false
    circle.value.memberCount = Math.max(0, circle.value.memberCount - 1)
  } else {
    await followCircle(circleId)
    circle.value.followed = true
    circle.value.memberCount += 1
  }
}

const openEditor = () => {
  editorVisible.value = true
}

const openDetail = (post: TopicPost) => {
  detailPostId.value = post.id
  detailVisible.value = true
}

const openTransfer = async () => {
  transferTarget.value = null
  transferVisible.value = true
  const res = await getCircleMembers(circleId)
  members.value = res.data || []
}

const submitTransfer = async () => {
  if (!transferTarget.value) return
  transferring.value = true
  try {
    await transferCircle(circleId, transferTarget.value)
    ElMessage.success('转让成功')
    transferVisible.value = false
    await loadCircle()
  } finally {
    transferring.value = false
  }
}

const handleDisband = async () => {
  await ElMessageBox.confirm(
    '确定解散该圈子吗？圈子内所有帖子都会删除，此操作不可恢复。',
    '解散圈子',
    { type: 'warning', confirmButtonText: '解散' }
  )
  await deleteTopicCircle(circleId)
  ElMessage.success('圈子已解散')
  router.push('/topic/circles')
}

const { sentinelRef, recheck } = useInfiniteScroll(loadMore)

loadCircle()
loadMore()

onBeforeUnmount(clearCache)
</script>

<style scoped lang="scss">
.circle-detail {
  .circle-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 16px;
    padding: 20px;
    margin-bottom: 18px;
    border-radius: 12px;
    background: var(--el-bg-color);
    border: 1px solid var(--ev-border-subtle);

    .circle-info {
      min-width: 0;

      h2 {
        margin: 0;
        font-size: 20px;
        font-weight: 700;
        color: var(--ev-text-primary);
      }

      .circle-desc {
        margin: 8px 0;
        font-size: 13px;
        color: var(--ev-text-regular);
      }

      .circle-stats {
        display: flex;
        gap: 16px;
        font-size: 12px;
        color: var(--ev-text-muted);
      }
    }

    .circle-actions {
      display: flex;
      gap: 10px;
      flex-shrink: 0;
      flex-wrap: wrap;
      justify-content: flex-end;
    }
  }

  .post-list {
    display: flex;
    flex-direction: column;
    gap: 14px;
    max-width: 760px;
  }

  .list-footer {
    text-align: center;
    padding: 20px 0 4px;
    color: var(--ev-text-muted);
    font-size: 12px;

    .sentinel {
      height: 1px;
    }
  }

  .transfer-tip {
    margin: 0 0 10px;
    font-size: 13px;
    color: var(--ev-text-regular);
  }

  .member-list {
    max-height: 280px;
    overflow-y: auto;

    .member-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 12px;
      border-radius: 8px;
      cursor: pointer;
      transition: background 0.2s ease;

      &:hover {
        background: var(--ev-bg-tint);
      }

      &.selected {
        background: rgba(47, 124, 246, 0.1);
        color: var(--ev-primary);
      }
    }
  }
}
</style>
