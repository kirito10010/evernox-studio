<template>
  <div class="hyol-announcement">
    <div class="page-header">
      <div>
        <h2>公告</h2>
        <p>火影忍者OL 官方公告</p>
      </div>
      <el-button v-if="userStore.isAdmin" :loading="refreshing" @click="onRefresh">刷新公告</el-button>
    </div>

    <div class="announcement-layout">
      <div class="announcement-main">
        <div v-loading="loading" class="announcement-list">
      <div v-for="a in list" :key="a.id" class="announcement-item" @click="openDetail(a)">
        <span class="title">{{ a.title }}</span>
        <span class="time">{{ a.publishTime }}</span>
      </div>
      <el-empty v-if="!loading && !list.length" description="暂无公告，点右上角刷新获取" />
    </div>

    <div class="list-footer">
      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="reload"
      />
    </div>

      </div>

      <aside class="announcement-aside">
        <div class="aside-card">
          <div class="aside-title">数据来源</div>
          <p class="aside-text">公告内容来自火影忍者OL官网，已缓存到本地，点开即可查看全文与图片。</p>
        </div>

        <div class="aside-card">
          <div class="aside-title">自动更新</div>
          <ul class="aside-tips">
            <li><el-icon><Calendar /></el-icon><span>每周三 17:06 抓取</span></li>
            <li><el-icon><Clock /></el-icon><span>每周四 20:06 / 22:06 抓取</span></li>
            <li><el-icon><Refresh /></el-icon><span>每天 09:06 兜底检查</span></li>
          </ul>
        </div>
      </aside>
    </div>

    <el-dialog v-model="detailVisible" :title="detail?.title || '公告'" width="720px" append-to-body>
      <div v-if="detail" class="detail-body">
        <div v-if="detail.publishTime" class="detail-time">发布时间：{{ detail.publishTime }}</div>
        <div class="hyol-detail-content" v-html="safeContent"></div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import { useUserStore } from '@/stores/user'
import {
  getHyolAnnouncementDetail,
  getHyolAnnouncements,
  refreshHyolAnnouncements,
} from '@/api/hyolAnnouncement'
import type { HyolAnnouncement } from '@/types/hyolAnnouncement'

const userStore = useUserStore()

const list = ref<HyolAnnouncement[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const refreshing = ref(false)

const detailVisible = ref(false)
const detail = ref<HyolAnnouncement | null>(null)

const safeContent = computed(() => {
  if (!detail.value?.content) return ''
  return DOMPurify.sanitize(detail.value.content, {
    ALLOWED_TAGS: [
      'p', 'br', 'strong', 'b', 'em', 'i', 'u', 's',
      'h1', 'h2', 'h3', 'h4', 'ul', 'ol', 'li', 'span', 'div',
      'img', 'a', 'table', 'tbody', 'tr', 'td', 'th', 'thead',
    ],
    ALLOWED_ATTR: ['src', 'alt', 'width', 'height', 'href', 'title'],
    ALLOWED_URI_REGEXP: /^(https?:\/\/|\/api\/)/i,
  })
})

const load = async () => {
  loading.value = true
  try {
    const res = await getHyolAnnouncements(page.value, size.value)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const reload = () => {
  page.value = 1
  load()
}

const openDetail = async (a: HyolAnnouncement) => {
  detail.value = a
  detailVisible.value = true
  const res = await getHyolAnnouncementDetail(a.id)
  detail.value = res.data
}

const onRefresh = async () => {
  refreshing.value = true
  try {
    const res = await refreshHyolAnnouncements()
    ElMessage.success(`刷新完成：成功 ${res.data?.fetched ?? 0} 条，失败 ${res.data?.failed ?? 0} 条`)
    await reload()
  } catch {
    // 错误已由拦截器提示
  } finally {
    refreshing.value = false
  }
}

load()
</script>

<style scoped lang="scss">
.hyol-announcement {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;

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

  .announcement-layout {
    margin-top: 18px;
    display: flex;
    gap: 20px;
    align-items: flex-start;
  }

  .announcement-main {
    flex: 1;
    min-width: 0;
  }

  .announcement-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .announcement-aside {
    width: 300px;
    flex-shrink: 0;
    position: sticky;
    top: 20px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .aside-card {
    padding: 16px;
    border-radius: 12px;
    background: var(--el-bg-color);
    border: 1px solid var(--ev-border-subtle);
  }

  .aside-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--ev-text-primary);
    margin-bottom: 12px;
  }

  .aside-text {
    margin: 0;
    font-size: 13px;
    line-height: 1.7;
    color: var(--ev-text-regular);
  }

  .aside-tips {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 12px;

    li {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
      color: var(--ev-text-regular);

      .el-icon {
        color: var(--ev-primary);
        flex-shrink: 0;
      }
    }
  }

  @media (max-width: 900px) {
    .announcement-layout {
      flex-direction: column;
    }

    .announcement-aside {
      width: 100%;
      position: static;
    }
  }

  .announcement-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px 16px;
    background: var(--el-bg-color);
    border: 1px solid var(--ev-border-subtle);
    border-radius: 10px;
    cursor: pointer;
    transition: box-shadow 0.25s var(--ev-ease-out);

    &:hover {
      box-shadow: var(--ev-shadow-sm);

      .title {
        color: var(--ev-primary);
      }
    }

    .title {
      flex: 1;
      min-width: 0;
      font-size: 14px;
      color: var(--ev-text-primary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      transition: color 0.2s ease;
    }

    .time {
      flex-shrink: 0;
      font-size: 12px;
      color: var(--ev-text-muted);
    }
  }

  .list-footer {
    margin-top: 16px;
    display: flex;
    justify-content: center;
  }

  .detail-body {
    max-height: 70vh;
    overflow-y: auto;
    overflow-x: auto;

    .detail-time {
      font-size: 13px;
      color: var(--ev-text-muted);
      margin-bottom: 12px;
    }

    .hyol-detail-content {
      font-size: 14px;
      line-height: 1.8;
      color: var(--ev-text-primary);
      word-break: break-word;
      overflow-x: hidden;
    }
  }
}
</style>

<style>
/* 公告正文图片/表格自适应弹窗宽度。
   注意：详情弹窗 append-to-body 后位于 body 下，不能用 .hyol-announcement 祖先选择器，
   故用唯一类名 .hyol-detail-content 直接命中 v-html 内容。 */
.hyol-detail-content img {
  max-width: 100% !important;
  height: auto !important;
  border-radius: 8px;
}
.hyol-detail-content a {
  color: var(--ev-primary);
  word-break: break-all;
}
.hyol-detail-content table {
  border-collapse: collapse;
  max-width: 100% !important;
}
.hyol-detail-content td,
.hyol-detail-content th {
  border: 1px solid var(--ev-border-default);
  padding: 4px 8px;
  word-break: break-word;
}
</style>
