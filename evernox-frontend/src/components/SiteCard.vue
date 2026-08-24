<template>
  <div class="site-card" @click="openSite">
    <div class="tile">
      <div v-if="site.coverImageId" class="tile-img">
        <LazyImage :image-id="site.coverImageId" :loader="loader" :alt="site.title" />
      </div>
      <div v-else class="tile-empty">
        <el-icon :size="34"><Link /></el-icon>
      </div>

      <div class="tile-top">
        <div v-if="visibleTags.length" class="tile-tags">
          <span v-for="tag in visibleTags" :key="tag.id" class="chip">{{ tag.name }}</span>
          <span v-if="hiddenTagCount" class="chip" :title="hiddenTagNames">+{{ hiddenTagCount }}</span>
        </div>
        <span v-if="statusLabel" class="chip chip-status" :class="`chip-status-${site.status}`">
          {{ statusLabel }}
        </span>
      </div>

      <div class="tile-text">
        <span class="title" :title="site.title">{{ site.title }}</span>
        <span class="host">
          <span class="host-name">{{ host }}</span>
          <el-icon class="external"><TopRight /></el-icon>
        </span>
      </div>

      <div class="desc-overlay">
        <p class="desc">{{ site.description || '暂无介绍' }}</p>
        <span v-if="site.ownerName" class="owner">分享者 {{ site.ownerName }}</span>
      </div>
    </div>

    <div v-if="$slots.actions || rejectText" class="card-foot" @click.stop>
      <p v-if="rejectText" class="reject" :title="rejectText">驳回：{{ rejectText }}</p>
      <div v-if="$slots.actions" class="actions">
        <slot name="actions" :site="site" />
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import LazyImage from '@/components/LazyImage.vue'
import { SiteStatus, SiteStatusMap } from '@/types/site'
import type { SiteLink } from '@/types/site'

const props = withDefaults(
  defineProps<{
    site: SiteLink
    /** 封面加载函数，由视图注入（与图床页保持同一套缓存策略） */
    loader: (id: number) => Promise<string | null>
    /** 公开页不需要状态角标 */
    showStatus?: boolean
  }>(),
  { showStatus: false }
)

/** 标签 chip 压在图上，超过两个就折叠，否则会盖住封面主体 */
const MAX_VISIBLE_TAGS = 2

/**
 * 状态文字标记
 *
 * 四种状态都显示；取不到枚举（脏数据）时返回空串，避免出现「未知」这种没有信息量的标签。
 */
const statusLabel = computed(() =>
  props.showStatus ? SiteStatusMap[props.site.status] ?? '' : ''
)

/** 仅驳回状态且填了原因时才在底栏展示 */
const rejectText = computed(() =>
  props.site.status === SiteStatus.REJECTED ? props.site.rejectReason || '' : ''
)

const visibleTags = computed(() => (props.site.tags ?? []).slice(0, MAX_VISIBLE_TAGS))
const hiddenTagCount = computed(() => Math.max((props.site.tags?.length ?? 0) - MAX_VISIBLE_TAGS, 0))
const hiddenTagNames = computed(() =>
  (props.site.tags ?? []).slice(MAX_VISIBLE_TAGS).map((tag) => tag.name).join('、')
)

const host = computed(() => {
  try {
    return new URL(props.site.url).host
  } catch {
    // 后端已限制 http/https，这里只是兜住历史脏数据
    return props.site.url
  }
})

/**
 * 新标签页打开目标站点
 *
 * noopener,noreferrer 必须带：否则目标页可通过 window.opener 反向操纵本站页面，
 * 且会把来源地址带给第三方。
 */
const openSite = () => {
  window.open(props.site.url, '_blank', 'noopener,noreferrer')
}
</script>

<style scoped lang="scss">
.site-card {
  display: flex;
  flex-direction: column;
  cursor: pointer;
  /* 圆角与裁剪统一放在容器：封面裁出上两角，白色底栏裁出下两角 */
  border-radius: 40px;
  overflow: hidden;
  background: var(--el-bg-color);
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.1);
  transition: box-shadow 0.22s, transform 0.22s;
}

.site-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.18);
}

.tile {
  position: relative;
  aspect-ratio: 1 / 1;
  background: var(--el-fill-color-light);
}

.tile-img {
  position: absolute;
  inset: 0;

  /*
   * LazyImage 会把测得的真实比例写进容器的内联 aspect-ratio，
   * 要让封面铺满 1:1 的图块，必须在这里用 !important 覆盖掉。
   */
  :deep(.lazy-image) {
    height: 100%;
    aspect-ratio: auto !important;
  }

  :deep(img) {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.35s ease;
  }
}

.site-card:hover .tile-img :deep(img) {
  transform: scale(1.05);
}
.tile-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.9);
  background: linear-gradient(135deg, #3b5bdb 0%, #7048e8 100%);
}

.tile-top {
  position: absolute;
  /* 圆角 40px，顶部胶囊要往里收，否则会贴上两角的弧线 */
  top: 14px;
  left: 16px;
  right: 16px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 6px;
  transition: opacity 0.2s;
}

.tile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-width: 0;
}
.chip {
  max-width: 84px;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 17px;
  color: rgba(255, 255, 255, 0.94);
  background: rgba(8, 12, 20, 0.46);
  backdrop-filter: blur(2px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 状态与标签同款胶囊，只靠文字色区分，避免刺眼的纯色圆点 */
.chip-status {
  flex: 0 0 auto;
  font-weight: 500;
}

.chip-status-0 { color: rgba(255, 255, 255, 0.86); }
.chip-status-1 { color: #ffd48a; }
.chip-status-2 { color: #8ce0a8; }
.chip-status-3 { color: #ff9a9a; }

/* 只在文字位置做一块底板，不再用整幅渐变盖住图标 */
.tile-text {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 6px 18px 8px;
  background: rgba(10, 16, 28, 0.62);
  backdrop-filter: blur(6px);
  transition: opacity 0.2s;
}

.tile-text .title {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.host {
  display: flex;
  align-items: center;
  gap: 3px;
  min-width: 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.8);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.45);
}

.host-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.external {
  flex: 0 0 auto;
  font-size: 11px;
}

/* 悬停浮现的介绍层 */
.desc-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 14px;
  background: rgba(10, 16, 28, 0.9);
  backdrop-filter: blur(3px);
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.2s ease;
}

.desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.92);
  display: -webkit-box;
  -webkit-line-clamp: 6;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.owner {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.6);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.site-card:hover {
  .desc-overlay {
    opacity: 1;
    visibility: visible;
  }

  /* 浮层出现时图上的文字与标签淡出，避免两层文字叠在一起 */
  .tile-text,
  .tile-top {
    opacity: 0;
  }
}

/* 触摸设备没有真实 hover，点一下会卡在浮层态，直接关掉这套效果 */
@media (hover: none) {
  .site-card:hover {
    .desc-overlay { opacity: 0; visibility: hidden; }
    .tile-text,
    .tile-top { opacity: 1; }
  }

  .site-card:hover { transform: none; }
}

/* ---------- 白色底栏 ---------- */
.card-foot {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 8px 18px;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
}

.reject {
  margin: 0;
  font-size: 12px;
  color: var(--el-color-danger);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 4px;

  /* Element Plus 默认给相邻按钮 12px 左外边距，这条细操作条里太宽 */
  :deep(.el-button + .el-button) {
    margin-left: 0;
  }

  :deep(.el-button--small) {
    padding: 5px 9px;
  }
}
</style>
