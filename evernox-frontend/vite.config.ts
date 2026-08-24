import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  optimizeDeps: {
    // 预置管理员页面独有的 element-plus 子组件：否则首次进入这些懒加载路由时
    // Vite 才发现新依赖 → 重新预构建 → 强制整页刷新
    include: [
      'element-plus/es',
      'element-plus/es/components/tabs/style/css',
      'element-plus/es/components/tab-pane/style/css',
      'element-plus/es/components/drawer/style/css',
      'element-plus/es/components/date-picker/style/css',
      'element-plus/es/components/input-number/style/css',
      'element-plus/es/components/switch/style/css',
      'element-plus/es/components/pagination/style/css',
      'element-plus/es/components/table/style/css',
      'element-plus/es/components/table-column/style/css',
    ],
  },
  server: {
    // 监听所有网卡（等价 0.0.0.0），局域网内其他设备才能访问
    host: true,
    port: 5211,
    // 端口被占时直接失败：否则 Vite 会静默换到 5212，导致防火墙放行的端口与实际端口不一致
    strictPort: true,
    proxy: {
      '/api': {
        // 转发由 Vite 所在这台机器发出，用 localhost 最快且不受网卡/IP 变化影响
        target: 'http://localhost:11002',
        changeOrigin: true,
      },
    },
  },
})
