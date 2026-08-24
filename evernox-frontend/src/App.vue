<template>
  <router-view v-slot="{ Component }">
    <transition name="page-fade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<script setup lang="ts">
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
  inset: -12%;
  z-index: -1;
  background: var(--ev-grad-mesh);
  background-size: 180% 180%;
  filter: blur(40px);
  animation: aurora-drift 28s ease-in-out infinite;
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
