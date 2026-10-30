<template>
  <!-- 中文 locale 由这里提供：main.ts 不再 app.use(ElementPlus)，
       所以弹窗按钮、分页文案、日期面板等要靠 config-provider 才能是中文。
       el-config-provider 不渲染额外包裹元素，DOM 结构与原来一致。 -->
  <el-config-provider :locale="zhCn">
    <router-view v-slot="{ Component }">
      <transition name="page-fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>

    <!-- 全局登录弹窗：匿名用户在任何页面触发需要登录的操作时打开，不跳转路由 -->
    <AuthDialog />
  </el-config-provider>
</template>

<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import AuthDialog from '@/components/auth/AuthDialog.vue'
</script>

<style>
#app {
  position: relative;
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  overflow: hidden;
  background-color: var(--ev-bg-deep);
}

/* 薄雾光斑层：静止的氛围渐变，不遮挡内容。
   这里刻意不再叠加 filter: blur(40px) 与无限漂移动画：
   - 网格本身由 radial-gradient 构成，边缘天然平滑，40px 高斯模糊几乎不改变观感，
     却要为整个视口额外维护一个带滤镜的合成层（2.5K@DPR2 下纹理可达数千万物理像素）。
   - 背景一旦运动，压在其上的 sidebar / header / 卡片的 backdrop-filter 会被迫
     每帧重新采样并重新模糊；背景静止后它们只在真正重绘时才计算。
   这两点正是「1K 流畅、2.5K 整站发钝」的根源。 */
#app::before {
  content: '';
  position: absolute;
  inset: -12%;                                       /* 比视口大 12%，避免渐变边缘露白 */
  z-index: -1;
  background: var(--ev-grad-mesh);
  background-size: 180% 180%;
  pointer-events: none;
}

/* 细腻点阵：抑制大面积渐变的色带，增加质感 */
#app::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background-image: radial-gradient(var(--ev-dot-color) 1px, transparent 1px);
  background-size: 22px 22px;
  opacity: 0.6;
  pointer-events: none;
}
</style>
