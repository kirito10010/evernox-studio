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

/**
 * 旧 chunk 自愈。
 *
 * 重新部署后，浏览器可能仍持有旧的 index.html，于是去请求已被删除的旧 chunk：
 * 控制台报 `Unable to preload CSS for /assets/xxx.css` 或 404，表现为「点菜单没反应」，
 * 手动刷新一次才好。Vite 遇到这种情况会派发 vite:preloadError，这里自动刷新一次。
 *
 * sessionStorage 标记用于防止「chunk 真的不存在」时无限刷新；
 * 挂载成功即说明本次加载的 chunk 完整，清掉标记让后续仍能自愈。
 */
const CHUNK_RELOAD_FLAG = 'evernox:chunk-reload-once'
window.addEventListener('vite:preloadError', () => {
  if (sessionStorage.getItem(CHUNK_RELOAD_FLAG)) return
  sessionStorage.setItem(CHUNK_RELOAD_FLAG, '1')
  window.location.reload()
})

app.mount('#app')

sessionStorage.removeItem(CHUNK_RELOAD_FLAG)
