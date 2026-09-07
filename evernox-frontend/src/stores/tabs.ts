import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface TabItem {
  path: string
  title: string
}

/** 固定首页，不可关闭 */
export const HOME_PATH = '/image-host'

const STORAGE_KEY = 'evernox_tabs'

function load(): TabItem[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw) as TabItem[]
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

export const useTabsStore = defineStore('tabs', () => {
  const tabs = ref<TabItem[]>(load())
  const activePath = ref(HOME_PATH)

  const persist = () => {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(tabs.value))
    } catch {
      /* 忽略 */
    }
  }

  /** 首页必须始终在第一位 */
  const ensureHome = () => {
    if (!tabs.value.some((t) => t.path === HOME_PATH)) {
      tabs.value.unshift({ path: HOME_PATH, title: '图床首页' })
    }
  }

  const addTab = (path: string, title: string) => {
    if (!path || !title) return
    ensureHome()
    if (!tabs.value.some((t) => t.path === path)) {
      tabs.value.push({ path, title })
    }
    activePath.value = path
    persist()
  }

  const removeTab = (path: string) => {
    if (path === HOME_PATH) return
    const idx = tabs.value.findIndex((t) => t.path === path)
    if (idx === -1) return
    tabs.value.splice(idx, 1)
    // 关闭的是当前页时，把激活项切到左侧相邻标签
    if (activePath.value === path) {
      const next = tabs.value[Math.max(0, idx - 1)]
      activePath.value = next ? next.path : HOME_PATH
    }
    persist()
  }

  const closeOthers = (path: string) => {
    tabs.value = tabs.value.filter((t) => t.path === HOME_PATH || t.path === path)
    activePath.value = path
    persist()
  }

  const closeAll = () => {
    tabs.value = tabs.value.filter((t) => t.path === HOME_PATH)
    activePath.value = HOME_PATH
    persist()
  }

  ensureHome()

  return { tabs, activePath, addTab, removeTab, closeOthers, closeAll }
})
