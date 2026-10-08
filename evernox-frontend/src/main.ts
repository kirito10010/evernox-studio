import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'

import App from './App.vue'
import router from './router'
import * as appIcons from './icons'
import './styles/main.scss'
import { useThemeStore } from './stores/theme'

// 两处都要设：组件文案（按钮、提示）来自 Element Plus 的 locale，
// 而日期面板里的月份、星期名由 dayjs 生成，只改前者仍会残留英文。
dayjs.locale('zh-cn')

const app = createApp(App)
const pinia = createPinia()

// 只注册项目实际用到的图标（见 ./icons）。
// 原来这里是 `import * as ElementPlusIconsVue` + 循环注册全部 293 个，
// 会让整个图标包进 bundle。
for (const [key, component] of Object.entries(appIcons)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)

// 注意：这里刻意**不再** `import ElementPlus from 'element-plus'` + `app.use(ElementPlus)`。
// vite.config.ts 已用 ElementPlusResolver 配置了按需引入（组件与指令都会自动引入），
// 再整库 use 一次会把按需引入完全抵消——主 chunk 会因此涨到 1.26MB。
// Element Plus 的中文 locale 改由 App.vue 的 <el-config-provider :locale="zhCn"> 提供。

// 应用持久化主题（index.html 已预置，此处再同步 store，保证后续切换一致）
useThemeStore(pinia).apply()

app.mount('#app')
