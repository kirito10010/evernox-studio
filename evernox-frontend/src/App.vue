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
  </el-config-provider>
</template>

<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'
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

/* 薄雾光斑层：缓慢漂移，仅作氛围，不遮挡内容 */
#app::before {
  content: '';
  position: absolute;
  inset: -12%;                                       /* 比视口大 12%，留白：漂移时不会露边 */
  z-index: -1;
  background: var(--ev-grad-mesh);
  background-size: 180% 180%;
  filter: blur(40px);
  /* 用 transform 版漂移：模糊只栅格化一次，之后每帧只做 GPU 矩阵变换。
     原先动 background-position 会每帧重新光栅化这一层并重算 40px 模糊，
     在 2.5K 这类高分屏上会拖垮整个外壳——包括压在上面的 sidebar / header 的
     backdrop-filter（它们会因为背后画面每帧变化而每帧重新采样）。 */
  animation: aurora-drift-transform 28s ease-in-out infinite;
  will-change: transform;
  pointer-events: none;
}

/* 细腻点阵：抑制大面积渐变的色带，增加质感 */
#app::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background-image: radial-gradient(rgba(47, 124, 246, 0.045) 1px, transparent 1px);
  background-size: 22px 22px;
  opacity: 0.6;
  pointer-events: none;
}
</style>
