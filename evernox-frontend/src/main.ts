import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'

import App from './App.vue'
import router from './router'
import './styles/main.scss'
import { useThemeStore } from './stores/theme'

// 两处都要设：组件文案（按钮、提示）来自 Element Plus 的 locale，
// 而日期面板里的月份、星期名由 dayjs 生成，只改前者仍会残留英文。
dayjs.locale('zh-cn')

const app = createApp(App)
const pinia = createPinia()

// 注册Element Plus图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 应用持久化主题（index.html 已预置，此处再同步 store，保证后续切换一致）
useThemeStore(pinia).apply()

app.mount('#app')
