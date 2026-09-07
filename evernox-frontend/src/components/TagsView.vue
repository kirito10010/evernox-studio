<template>
  <div class="tags-view">
    <div ref="scrollRef" class="tags-view-scroll">
      <div
        v-for="tab in tabsStore.tabs"
        :key="tab.path"
        class="tag-item"
        :class="{ 'is-active': tab.path === tabsStore.activePath }"
        @click="go(tab)"
      >
        <span class="tag-dot"></span>
        <span class="tag-title">{{ tab.title }}</span>
        <span v-if="tab.path !== HOME_PATH" class="tag-close" @click.stop="close(tab)">
          <el-icon :size="12"><Close /></el-icon>
        </span>
      </div>
    </div>
    <div class="tags-view-actions">
      <el-dropdown trigger="click" @command="onCommand">
        <span class="tag-more"><el-icon :size="16"><MoreFilled /></el-icon></span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="close-others">关闭其他</el-dropdown-item>
            <el-dropdown-item command="close-all">关闭全部</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Close, MoreFilled } from '@element-plus/icons-vue'
import { useTabsStore, HOME_PATH, type TabItem } from '@/stores/tabs'

const route = useRoute()
const router = useRouter()
const tabsStore = useTabsStore()

const scrollRef = ref<HTMLElement | null>(null)

const go = (tab: TabItem) => {
  if (tab.path !== route.path) {
    router.push(tab.path)
  }
}

const close = (tab: TabItem) => {
  tabsStore.removeTab(tab.path)
  if (route.path === tab.path) {
    router.push(tabsStore.activePath)
  }
}

const onCommand = (cmd: string) => {
  if (cmd === 'close-others') {
    tabsStore.closeOthers(route.path)
  } else if (cmd === 'close-all') {
    tabsStore.closeAll()
    router.push(HOME_PATH)
  }
}
</script>

<style scoped lang="scss">
.tags-view {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 42px;
  padding: 0 20px;
  background: var(--ev-bg-glass-light);
  border-bottom: 1px solid var(--ev-border-subtle);
  backdrop-filter: var(--ev-blur-md);
  -webkit-backdrop-filter: var(--ev-blur-md);
  flex-shrink: 0;
}

.tags-view-scroll {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 100%;
  overflow-x: auto;
  overflow-y: hidden;

  &::-webkit-scrollbar {
    height: 3px;
  }
}

.tag-item {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 10px;
  font-size: 12.5px;
  color: var(--ev-text-secondary);
  border-radius: 8px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.18s var(--ev-ease-out);

  &:hover {
    background: var(--ev-bg-glass-strong);
    color: var(--ev-primary);

    .tag-close {
      opacity: 1;
    }
  }

  &.is-active {
    background: var(--ev-bg-elevated);
    color: var(--ev-primary);
    font-weight: 600;
    border-color: var(--ev-border-default);
    box-shadow: var(--ev-shadow-sm);

    .tag-dot {
      opacity: 1;
    }

    .tag-close {
      opacity: 1;
    }
  }
}

.tag-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ev-grad-aurora);
  opacity: 0;
  transition: opacity 0.18s ease;
}

.tag-title {
  white-space: nowrap;
  line-height: 1;
}

.tag-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  color: var(--ev-text-muted);
  opacity: 0;
  transition: all 0.15s ease;

  &:hover {
    background: rgba(242, 99, 127, 0.12);
    color: var(--ev-danger);
  }
}

.tags-view-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
}

.tag-more {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  color: var(--ev-text-secondary);
  cursor: pointer;
  transition: all 0.2s var(--ev-ease-out);

  &:hover {
    background: var(--ev-bg-glass-strong);
    color: var(--ev-primary);
  }
}
</style>
