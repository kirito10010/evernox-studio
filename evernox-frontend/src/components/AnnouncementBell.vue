<template>
  <div class="announcement-bell">
    <el-popover
      v-model:visible="popoverVisible"
      trigger="click"
      placement="bottom-end"
      :width="360"
      popper-class="announcement-popover"
    >
      <template #reference>
        <div class="bell-btn">
          <el-badge :value="unread" :hidden="unread === 0" :max="99">
            <el-icon :size="18"><Bell /></el-icon>
          </el-badge>
        </div>
      </template>

      <div class="popover-header">
        <span class="popover-title">公告</span>
        <span v-if="unread" class="unread-hint">{{ unread }} 条未读</span>
      </div>

      <div v-loading="loading" class="popover-list">
        <div v-if="!list.length" class="popover-empty">暂无公告</div>
        <div
          v-for="item in list"
          :key="item.id"
          class="popover-item"
          :class="{ unread: !item.read }"
          @click="openDetail(item)"
        >
          <span
            v-if="item.tagName"
            class="tag-dot"
            :style="{ background: item.tagColor || '#409EFF' }"
          />
          <div class="item-body">
            <div class="item-title">{{ item.title }}</div>
            <div class="item-meta">
              <span
                v-if="item.tagName"
                class="item-tag"
                :style="{ color: item.tagColor || '#409EFF' }"
              >{{ item.tagName }}</span>
              <span class="item-time">{{ formatTime(item.createdAt) }}</span>
            </div>
          </div>
          <span v-if="!item.read" class="unread-dot" />
        </div>
      </div>
    </el-popover>

    <el-dialog v-model="detailVisible" :title="detail?.title || '公告'" width="640px" append-to-body>
      <div v-if="detail" class="detail-body">
        <div class="detail-meta">
          <span
            v-if="detail.tagName"
            class="tag-chip"
            :style="{ background: detail.tagColor || '#409EFF' }"
          >{{ detail.tagName }}</span>
          <span class="detail-time">{{ formatTime(detail.createdAt) }}</span>
        </div>
        <RichTextViewer :html="detail.content || ''" :loader="decryptAnnouncement" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import RichTextViewer from '@/components/RichTextViewer.vue'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import {
  getAnnouncementDetail,
  getAnnouncementImageBlob,
  getAnnouncementList,
  getUnreadCount,
  markAnnouncementRead,
} from '@/api/announcement'
import type { AnnouncementResponse } from '@/types/announcement'

const { decryptImage: decryptAnnouncement, clearCache: clearAnnouncementCache } =
  useImageDecrypt(getAnnouncementImageBlob)

const unread = ref(0)
const list = ref<AnnouncementResponse[]>([])
const loading = ref(false)
const popoverVisible = ref(false)
const detailVisible = ref(false)
const detail = ref<AnnouncementResponse | null>(null)

let eventSource: EventSource | null = null

const loadUnread = async () => {
  try {
    const res = await getUnreadCount()
    unread.value = res.data?.unread || 0
  } catch {
    // 未登录或网络异常时不打扰用户
  }
}

const loadList = async () => {
  loading.value = true
  try {
    const res = await getAnnouncementList()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

const openDetail = async (item: AnnouncementResponse) => {
  detail.value = item
  detailVisible.value = true
  popoverVisible.value = false
  // 列表项不含正文，需按 id 拉取完整详情（含插图）
  try {
    const res = await getAnnouncementDetail(item.id)
    detail.value = res.data
  } catch {
    // 拉取失败时退回列表项，至少展示标题
  }
  try {
    await markAnnouncementRead(item.id)
    item.read = true
    await loadUnread()
  } catch {
    // 标记失败不阻断查看
  }
}

const formatTime = (value?: string) => {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}

// 打开弹层时拉最新列表与未读数
watch(popoverVisible, (visible) => {
  if (visible) {
    loadList()
    loadUnread()
  }
})

const connectStream = () => {
  const token = localStorage.getItem('accessToken')
  if (!token) return
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  eventSource = new EventSource(`${base}/announcement/stream?token=${encodeURIComponent(token)}`)
  eventSource.addEventListener('refresh', () => {
    loadUnread()
    if (popoverVisible.value) loadList()
  })
}

onMounted(() => {
  loadUnread()
  connectStream()
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
  clearAnnouncementCache()
})
</script>

<style scoped lang="scss">
.announcement-bell {
  .bell-btn {
    width: 36px;
    height: 36px;
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.6);
    border: 1px solid var(--ev-border-default);
    box-shadow: var(--ev-shadow-xs), inset 0 1px 0 rgba(255, 255, 255, 0.9);
    display: flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
    cursor: pointer;
    color: var(--ev-text-secondary);
    transition: all 0.25s var(--ev-ease-out);

    :deep(.el-badge) {
      display: flex;
      align-items: center;
      justify-content: center;
    }

    :deep(.el-icon) {
      display: flex;
      align-items: center;
      justify-content: center;
    }

    &:hover {
      color: var(--ev-primary);
      border-color: var(--ev-border-hover);
      background: rgba(255, 255, 255, 0.9);
      box-shadow: var(--ev-glow-violet);
    }
  }
}

.popover-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 4px 10px;
  border-bottom: 1px solid var(--ev-border-subtle);

  .popover-title {
    font-size: 15px;
    font-weight: 700;
    color: var(--ev-text-primary);
  }

  .unread-hint {
    font-size: 12px;
    color: var(--ev-danger);
  }
}

.popover-list {
  max-height: 360px;
  overflow-y: auto;

  .popover-empty {
    padding: 40px 0;
    text-align: center;
    font-size: 13px;
    color: var(--ev-text-muted);
  }

  .popover-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 8px;
    border-radius: 8px;
    cursor: pointer;
    transition: background 0.2s ease;

    &:hover {
      background: var(--ev-bg-tint);
    }

    .tag-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      flex-shrink: 0;
    }

    .item-body {
      flex: 1;
      min-width: 0;
    }

    .item-title {
      font-size: 13px;
      font-weight: 500;
      color: var(--ev-text-primary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .item-meta {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-top: 3px;
      font-size: 12px;
      color: var(--ev-text-muted);

      .item-tag {
        font-weight: 500;
      }
    }

    .unread-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--ev-danger);
      flex-shrink: 0;
    }
  }
}

.detail-body {
  .detail-meta {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;

    .tag-chip {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 999px;
      color: #fff;
      font-size: 12px;
      line-height: 20px;
    }

    .detail-time {
      font-size: 12px;
      color: var(--ev-text-muted);
    }
  }
}
</style>
