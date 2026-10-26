<template>
  <el-dialog
    v-model="visible"
    width="860px"
    align-center
    append-to-body
    class="about-dialog"
    :show-close="false"
  >
    <div class="about-shell">
      <button class="about-close" type="button" aria-label="关闭" @click="visible = false">
        <el-icon :size="16"><Close /></el-icon>
      </button>

      <!-- 品牌区 -->
      <section class="about-hero">
        <div class="hero-glow"></div>

        <div class="brand-row">
          <div class="brand-logo">
            <img src="@/assets/logo.svg" alt="EverNox" />
          </div>
          <div>
            <div class="brand-name">EverNox <span>Studio</span></div>
            <div class="brand-sub">永夜照相馆 · 个人数字空间</div>
          </div>
        </div>

        <h1>把散落的内容与工具，收进一个<span class="accent">安静、好用</span>的地方。</h1>
        <p class="hero-desc">
          一个持续生长的自托管平台：图片、话题、游戏资料、AI 工具与个人效率工具，都在同一个入口里。
        </p>

        <div class="hero-tags">
          <span class="pill">Self-hosted</span>
          <span class="pill">Anonymous browsing</span>
          <span class="pill">持续迭代</span>
        </div>
      </section>

      <div class="content-grid">
        <!-- 关于本站 -->
        <section class="card">
          <div class="card-eyebrow">ABOUT</div>
          <h2>关于本站</h2>
          <p class="card-text">
            EverNox Studio 是一个由个人开发与维护的自托管平台，目标是让日常会用到的内容与工具
            有一个统一、干净、可持续演进的入口。
          </p>

          <ul class="feature-list">
            <li>
              <span class="dot"></span>
              <div>
                <strong>内容与社区</strong>
                <p>图床、公开相册、网站分享、话题广场与圈子。</p>
              </div>
            </li>
            <li>
              <span class="dot"></span>
              <div>
                <strong>资料与工具</strong>
                <p>火影忍者资料、忍者测验、AI 资讯与模型排行榜。</p>
              </div>
            </li>
            <li>
              <span class="dot"></span>
              <div>
                <strong>个人效率</strong>
                <p>记事本、待办、记账、绩效与工资记录。</p>
              </div>
            </li>
          </ul>

          <p class="card-note">
            未登录也可以浏览公开内容；涉及个人数据的操作会在当前页面弹出登录。
          </p>
        </section>

        <!-- 开发者 -->
        <section class="card developer-card">
          <div class="card-eyebrow">DEVELOPER</div>

          <div class="developer-head">
            <div class="avatar">L</div>
            <div>
              <h2>li-jin</h2>
              <p>独立开发者 · 全栈</p>
            </div>
          </div>

          <p class="card-text">
            负责本站的设计、开发、部署与长期维护。有问题、建议或合作意向，欢迎联系。
          </p>

          <div class="contact-list">
            <a class="contact-item" href="tel:19863950869">
              <el-icon :size="16"><Phone /></el-icon>
              <span>19863950869</span>
            </a>
            <a class="contact-item" href="mailto:a3025863517@163.com">
              <el-icon :size="16"><Message /></el-icon>
              <span>a3025863517@163.com</span>
            </a>
          </div>

          <div class="stack">
            <span>Spring Boot</span>
            <span>Java 21</span>
            <span>MySQL</span>
            <span>Vue 3</span>
            <span>Vite</span>
            <span>Nginx</span>
          </div>
        </section>
      </div>

      <footer class="about-footer">
        <span>© 2026 EverNox Studio</span>
        <span class="dot-sep"></span>
        <span>Built &amp; maintained by li-jin</span>
      </footer>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Close, Message, Phone } from '@element-plus/icons-vue'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})
</script>

<!-- 弹窗外壳（非 scoped：类名只在本组件使用） -->
<style lang="scss">
.el-dialog.about-dialog {
  padding: 0 !important;
  max-width: 94vw;
  border-radius: 22px !important;
  overflow: hidden;

  .el-dialog__header {
    display: none;
  }

  .el-dialog__body {
    padding: 0 !important;
    background: transparent !important;
  }
}
</style>

<style scoped lang="scss">
.about-shell {
  position: relative;
  max-height: 80vh;
  overflow-y: auto;
  padding: 28px;
  display: flex;
  flex-direction: column;
  gap: 18px;

  /* 细滚动条，避免破坏整体观感 */
  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    border-radius: 3px;
    background: var(--ev-border-default);
  }
}

.about-close {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 3;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--ev-border-subtle);
  border-radius: 9px;
  background: var(--ev-bg-glass);
  color: var(--ev-text-muted);
  cursor: pointer;
  transition: all 0.2s var(--ev-ease-out);

  &:hover {
    color: var(--ev-primary);
    border-color: var(--ev-border-hover);
    background: var(--ev-bg-tint-strong);
  }
}

/* ---------- Hero ---------- */
.about-hero {
  position: relative;
  overflow: hidden;
  padding: 26px 28px;
  border: 1px solid var(--ev-border-subtle);
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(47, 124, 246, 0.08), rgba(79, 195, 232, 0.06) 55%, transparent);
}

.hero-glow {
  position: absolute;
  top: -150px;
  right: -110px;
  width: 340px;
  height: 340px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(79, 195, 232, 0.18), transparent 68%);
  pointer-events: none;
}

.brand-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding-right: 44px;
}

.brand-logo {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 13px;
  background: linear-gradient(135deg, rgba(47, 124, 246, 0.16), rgba(79, 195, 232, 0.16));
  border: 1px solid var(--ev-border-default);
  box-shadow: var(--ev-inset-gloss);

  img {
    width: 27px;
    height: 27px;
  }
}

.brand-name {
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 1.5px;
  color: var(--ev-text-primary);

  span {
    background: linear-gradient(135deg, #2f7cf6, #4fc3e8);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
}

.brand-sub {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--ev-text-muted);
  letter-spacing: 0.4px;
}

h1 {
  position: relative;
  margin: 22px 0 10px;
  font-size: 23px;
  line-height: 1.5;
  font-weight: 800;
  letter-spacing: -0.3px;
  color: var(--ev-text-primary);
}

.accent {
  background: linear-gradient(135deg, #2f7cf6, #4fc3e8);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-desc {
  position: relative;
  max-width: 600px;
  margin: 0;
  font-size: 13px;
  line-height: 1.85;
  color: var(--ev-text-secondary);
}

.hero-tags {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.pill {
  padding: 3px 11px;
  border-radius: 999px;
  border: 1px solid var(--ev-border-subtle);
  background: var(--ev-bg-glass);
  font-size: 11.5px;
  color: var(--ev-text-muted);
}

/* ---------- Content ---------- */
.content-grid {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 18px;
}

.card {
  padding: 22px 24px;
  border: 1px solid var(--ev-border-subtle);
  border-radius: 16px;
  background: var(--ev-bg-glass-strong);
}

.card-eyebrow {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2.5px;
  color: var(--ev-primary);
  margin-bottom: 8px;
}

.card h2 {
  margin: 0 0 10px;
  font-size: 18px;
  font-weight: 700;
  color: var(--ev-text-primary);
}

.card-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.85;
  color: var(--ev-text-secondary);
}

.card-note {
  margin: 18px 0 0;
  padding-top: 14px;
  border-top: 1px solid var(--ev-border-subtle);
  font-size: 12px;
  line-height: 1.75;
  color: var(--ev-text-muted);
}

.feature-list {
  list-style: none;
  margin: 18px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;

  li {
    display: flex;
    gap: 12px;
  }

  strong {
    display: block;
    margin-bottom: 3px;
    font-size: 13px;
    color: var(--ev-text-primary);
  }

  p {
    margin: 0;
    font-size: 12px;
    line-height: 1.65;
    color: var(--ev-text-muted);
  }
}

.dot {
  width: 7px;
  height: 7px;
  margin-top: 7px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--ev-grad-aurora);
  box-shadow: 0 0 0 4px rgba(47, 124, 246, 0.08);
}

/* ---------- Developer ---------- */
.developer-card {
  display: flex;
  flex-direction: column;
}

.developer-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;

  h2 {
    margin: 0 0 3px;
  }

  p {
    margin: 0;
    font-size: 12px;
    color: var(--ev-primary);
  }
}

.avatar {
  width: 46px;
  height: 46px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  font-size: 18px;
  font-weight: 800;
  color: #fff;
  background: linear-gradient(135deg, #2f7cf6, #4fc3e8);
  box-shadow: 0 8px 20px -8px rgba(47, 124, 246, 0.6);
}

.contact-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin-top: 18px;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 13px;
  border-radius: 11px;
  border: 1px solid var(--ev-border-subtle);
  background: var(--ev-bg-tint);
  font-size: 12.5px;
  color: var(--ev-text-secondary);
  text-decoration: none;
  transition: all 0.2s ease;

  &:hover {
    color: var(--ev-primary);
    border-color: var(--ev-border-hover);
    background: var(--ev-bg-tint-strong);
  }
}

.stack {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: auto;
  padding-top: 18px;

  span {
    padding: 3px 9px;
    border-radius: 8px;
    border: 1px solid var(--ev-border-subtle);
    font-size: 11px;
    color: var(--ev-text-muted);
    background: var(--ev-bg-glass);
  }
}

/* ---------- Footer ---------- */
.about-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 2px 0 6px;
  font-size: 11.5px;
  color: var(--ev-text-muted);
}

.dot-sep {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--ev-text-muted);
  opacity: 0.5;
}

@media (max-width: 820px) {
  .about-shell {
    padding: 20px;
  }

  .content-grid {
    grid-template-columns: 1fr;
  }

  .about-hero {
    padding: 22px;
  }

  h1 {
    font-size: 19px;
  }
}
</style>
