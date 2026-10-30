import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ThemeName = 'light' | 'dark' | 'celadon' | 'lotus'

const KEY = 'evernox_theme'

const VALID: ThemeName[] = ['light', 'dark', 'celadon', 'lotus']

const readStoredTheme = (): ThemeName => {
  const stored = localStorage.getItem(KEY) as ThemeName | null
  return stored && VALID.includes(stored) ? stored : 'light'
}

export const useThemeStore = defineStore('theme', () => {
  const theme = ref<ThemeName>(readStoredTheme())

  const apply = () => {
    const el = document.documentElement
    el.setAttribute('data-theme', theme.value)
    // Element Plus 的暗色变量只认 .dark；青瓷 / 藕荷是浅色主题，不能带这个类
    el.classList.toggle('dark', theme.value === 'dark')
    try {
      localStorage.setItem(KEY, theme.value)
    } catch {
      /* 忽略 */
    }
  }

  const setTheme = (t: ThemeName) => {
    theme.value = t
    apply()
  }

  return { theme, setTheme, apply }
})
