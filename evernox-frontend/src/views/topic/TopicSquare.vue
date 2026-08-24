<template>
  <div class="topic-square">
    <div class="page-header">
      <h2>广场</h2>
      <p>所有话题圈的帖子都在这里</p>
    </div>

    <div class="square-layout">
      <div class="square-main">
        <div class="sort-bar">
          <button
            v-for="s in sorts"
            :key="s.value"
            class="sort-btn"
            :class="{ active: sort === s.value }"
            @click="changeSort(s.value)"
          >
            {{ s.label }}
          </button>
        </div>

        <div v-loading="loading" class="post-list">
          <PostCard v-for="p in posts" :key="p.id" :post="p" :loader="decryptImage" @open="openDetail" />
        </div>

        <el-empty v-if="!loading && !posts.length" description="还没有帖子，去圈子发一篇吧" />

        <div class="list-footer">
          <div :ref="(el) => (sentinelRef = el as HTMLElement | null)" class="sentinel"></div>
          <span v-if="!hasMore && posts.length" class="footer-tip">没有更多了</span>
        </div>
      </div>

      <aside class="square-aside">
        <div class="rank-card">
          <div class="rank-title">圈子热度排行</div>
          <div v-if="circleRank.length" class="rank-list">
            <div
              v-for="(c, i) in circleRank"
              :key="c.id"
              class="rank-item"
              @click="enterCircle(c.id)"
            >
              <span class="rank-index" :class="{ top: i < 3 }">{{ i + 1 }}</span>
              <span class="rank-name">{{ c.name }}</span>
              <span class="rank-count">{{ c.postCount }} 帖</span>
            </div>
          </div>
          <el-empty v-else description="暂无" :image-size="40" />
        </div>

        <div class="rank-card">
          <div class="rank-title">发帖达人排行</div>
          <div v-if="userRank.length" class="rank-list">
            <div v-for="(u, i) in userRank" :key="u.userId" class="rank-item">
              <span class="rank-index" :class="{ top: i < 3 }">{{ i + 1 }}</span>
              <span class="rank-name">{{ u.username }}</span>
              <span class="rank-count">{{ u.postCount }} 帖</span>
            </div>
          </div>
          <el-empty v-else description="暂无" :image-size="40" />
        </div>
      </aside>
    </div>

    <PostDetailDialog v-model="detailVisible" :post-id="detailPostId" @changed="reload" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import PostCard from '@/components/PostCard.vue'
import PostDetailDialog from '@/components/PostDetailDialog.vue'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import { getSquarePosts, getTopicImageBlob, getTopicRank } from '@/api/topic'
import type { TopicCircle, TopicPost, TopicSort, TopicUserRank } from '@/types/topic'

const router = useRouter()

const sorts: { value: TopicSort; label: string }[] = [
  { value: 'hot', label: '热门' },
  { value: 'latest', label: '最新' },
  { value: 'like', label: '点赞最多' },
  { value: 'favorite', label: '收藏最多' },
  { value: 'comment', label: '评论最多' },
]

const { decryptImage, clearCache } = useImageDecrypt(getTopicImageBlob)

const sort = ref<TopicSort>('hot')
const posts = ref<TopicPost[]>([])
const page = ref(1)
const hasMore = ref(true)
const loading = ref(false)

const circleRank = ref<TopicCircle[]>([])
const userRank = ref<TopicUserRank[]>([])

const detailVisible = ref(false)
const detailPostId = ref<number | null>(null)

const loadMore = async () => {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const res = await getSquarePosts({ page: page.value, size: 12, sort: sort.value })
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
  loadMore()
}

const loadRank = async () => {
  const res = await getTopicRank()
  circleRank.value = res.data?.circles || []
  userRank.value = res.data?.users || []
}

const changeSort = (value: TopicSort) => {
  if (sort.value === value) return
  sort.value = value
  reload()
}

const openDetail = (post: TopicPost) => {
  detailPostId.value = post.id
  detailVisible.value = true
}

const enterCircle = (circleId: number) => {
  router.push(`/topic/circle/${circleId}`)
}

const { sentinelRef, recheck } = useInfiniteScroll(loadMore)

loadMore()
loadRank()

onBeforeUnmount(clearCache)
</script>

<style scoped lang="scss">
.topic-square {
  .page-header {
    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }

    p {
      margin: 6px 0 0;
      font-size: 13px;
      color: var(--ev-text-muted);
    }
  }

  .square-layout {
    display: flex;
    gap: 20px;
    align-items: flex-start;
    margin-top: 18px;
  }

  .square-main {
    flex: 1;
    min-width: 0;
  }

  .sort-bar {
    display: flex;
    gap: 8px;
    margin-bottom: 16px;

    .sort-btn {
      padding: 6px 16px;
      border-radius: 999px;
      border: 1px solid var(--ev-border-default);
      background: var(--el-bg-color);
      color: var(--ev-text-secondary);
      font-size: 13px;
      cursor: pointer;
      transition: all 0.2s ease;

      &.active {
        background: var(--ev-primary);
        border-color: var(--ev-primary);
        color: #fff;
      }
    }
  }

  .post-list {
    display: flex;
    flex-direction: column;
    gap: 14px;
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

  .square-aside {
    width: 260px;
    flex-shrink: 0;
    position: sticky;
    top: 20px;

    .rank-card {
      padding: 14px 16px;
      border-radius: 12px;
      background: var(--el-bg-color);
      border: 1px solid var(--ev-border-subtle);
      margin-bottom: 14px;

      .rank-title {
        font-size: 14px;
        font-weight: 600;
        color: var(--ev-text-primary);
        margin-bottom: 10px;
      }

      .rank-list {
        .rank-item {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 7px 0;
          border-bottom: 1px solid var(--ev-border-subtle);
          cursor: pointer;

          &:last-child {
            border-bottom: none;
          }

          .rank-index {
            width: 20px;
            height: 20px;
            border-radius: 6px;
            background: var(--ev-bg-tint);
            color: var(--ev-text-muted);
            font-size: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;

            &.top {
              background: var(--ev-primary);
              color: #fff;
            }
          }

          .rank-name {
            flex: 1;
            min-width: 0;
            font-size: 13px;
            color: var(--ev-text-primary);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }

          .rank-count {
            font-size: 12px;
            color: var(--ev-text-muted);
            flex-shrink: 0;
          }
        }
      }
    }
  }
}
</style>
