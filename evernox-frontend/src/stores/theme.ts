import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ThemeName = 'light' | 'dark'

const KEY = 'evernox_theme'

export const useThemeStore = defineStore('theme', () => {
  const theme = ref<ThemeName>((localStorage.getItem(KEY) as ThemeName) || 'light')

  const apply = () => {
    const el = document.documentElement
    el.setAttribute('data-theme', theme.value)
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
