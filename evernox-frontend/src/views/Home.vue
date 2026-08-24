<template>
  <div class="home-container">
    <!-- Welcome Banner -->
    <div class="welcome-banner">
      <!-- Aurora background -->
      <div class="banner-aurora"></div>
      <!-- Decorative geometric shapes -->
      <div class="geo-shapes">
        <div class="geo-ring ring-1"></div>
        <div class="geo-ring ring-2"></div>
        <div class="geo-ring ring-3"></div>
        <div class="geo-dot dot-1"></div>
        <div class="geo-dot dot-2"></div>
      </div>

      <div class="banner-content">
        <div class="banner-pill">
          <span class="pill-dot"></span>
          极光幻境 · 智能相册系统
        </div>
        <h1>欢迎来到 <span class="aurora-text">EverNox 永夜照相馆</span></h1>
        <p>探索极简、安全与极致视觉效果的数字资产管理体验，为您的作品赋予流光华彩。</p>
      </div>
    </div>

    <!-- Stats Grid -->
    <div class="stats-grid">
      <div
        class="stat-card"
        v-for="(item, index) in statList"
        :key="item.label"
        :style="{ '--card-accent': item.accent, '--card-accent-rgb': item.accentRgb, animationDelay: `${index * 80}ms` }"
      >
        <!-- Top gradient border -->
        <div class="card-top-border"></div>
        <!-- Shimmer overlay -->
        <div class="card-shimmer"></div>
        <!-- Glow -->
        <div class="card-glow"></div>

        <div class="card-body">
          <div class="icon-box" :style="{ background: item.iconBg }">
            <el-icon :size="24"><component :is="item.icon" /></el-icon>
          </div>
          <div class="info-box">
            <div class="stat-value">{{ item.value }}</div>
            <div class="stat-label">{{ item.label }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Features Section -->
    <div class="feature-section">
      <div class="section-header">
        <div>
          <h2>核心引擎与特色服务</h2>
          <p>每一项设计都经过精心调优，只为带来极致的用户体验</p>
        </div>
      </div>

      <div class="features-grid">
        <div
          class="feature-card"
          v-for="(feature, index) in features"
          :key="feature.title"
          :style="{ '--feature-color': featureColors[index].color, '--feature-color-rgb': featureColors[index].rgb, animationDelay: `${index * 100}ms` }"
        >
          <!-- Left color bar -->
          <div class="feature-bar"></div>
          <!-- Hover glow -->
          <div class="feature-hover-glow"></div>

          <div class="feature-content">
            <div class="feature-icon-box" :style="{ background: featureColors[index].bg }">
              <el-icon :size="24"><component :is="feature.icon" /></el-icon>
            </div>
            <h3>{{ feature.title }}</h3>
            <p>{{ feature.description }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed } from 'vue'

const stats = reactive({
  users: 128,
  photos: 3450,
  albums: 42,
  points: 990,
})

const statList = computed(() => [
  {
    label: '注册用户',
    value: stats.users,
    icon: 'User',
    accent: '#2f7cf6',
    accentRgb: '47, 124, 246',
    iconBg: 'linear-gradient(135deg, rgba(47,124,246,0.16), rgba(47,124,246,0.04))',
  },
  {
    label: '照片数量',
    value: stats.photos,
    icon: 'Picture',
    accent: '#4fc3e8',
    accentRgb: '79, 195, 232',
    iconBg: 'linear-gradient(135deg, rgba(79,195,232,0.16), rgba(79,195,232,0.04))',
  },
  {
    label: '相册数量',
    value: stats.albums,
    icon: 'FolderOpened',
    accent: '#7fb2fb',
    accentRgb: '127, 178, 251',
    iconBg: 'linear-gradient(135deg, rgba(127,178,251,0.16), rgba(127,178,251,0.04))',
  },
  {
    label: '积分总量',
    value: stats.points,
    icon: 'Coin',
    accent: '#34c9a3',
    accentRgb: '52, 201, 163',
    iconBg: 'linear-gradient(135deg, rgba(52,201,163,0.16), rgba(52,201,163,0.04))',
  },
])

const features = [
  {
    icon: 'Lock',
    title: '顶级加密安全',
    description: '使用现代 Argon2id 密码哈希算法与 JWT Token 防窃取防护，全面保障您的隐私数据。',
  },
  {
    icon: 'PictureFilled',
    title: '智能沉浸相册',
    description: '支持多维度相册分类、批量极速上传与无损原图缩略展示，管理相册轻松省心。',
  },
  {
    icon: 'Medal',
    title: '尊享会员体系',
    description: '提供超级会员与高级特权服务，解锁专属高清备份通道与定制视觉体验。',
  },
  {
    icon: 'TrendCharts',
    title: '数据统计面板',
    description: '可视化展示个人照片库容量、访问记录与积分明细，数据一览无余。',
  },
]

const featureColors = [
  { color: '#2f7cf6', rgb: '47, 124, 246', bg: 'linear-gradient(135deg, rgba(47,124,246,0.16), rgba(47,124,246,0.04))' },
  { color: '#4fc3e8', rgb: '79, 195, 232', bg: 'linear-gradient(135deg, rgba(79,195,232,0.16), rgba(79,195,232,0.04))' },
  { color: '#7fb2fb', rgb: '127, 178, 251', bg: 'linear-gradient(135deg, rgba(127,178,251,0.16), rgba(127,178,251,0.04))' },
  { color: '#34c9a3', rgb: '52, 201, 163', bg: 'linear-gradient(135deg, rgba(52,201,163,0.16), rgba(52,201,163,0.04))' },
]
</script>

<style scoped lang="scss">
.home-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
  position: relative;
  z-index: 1;
}

/* ============================================================
   Welcome Banner
   ============================================================ */
.welcome-banner {
  position: relative;
  width: 100%;
  padding: 48px 44px;
  border-radius: var(--ev-radius-xl);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: var(--ev-blur-md);
  -webkit-backdrop-filter: var(--ev-blur-md);
  overflow: hidden;
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
}

.banner-aurora {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg,
    rgba(47, 124, 246, 0.14) 0%,
    rgba(79, 195, 232, 0.08) 40%,
    rgba(165, 228, 242, 0.12) 70%,
    rgba(207, 230, 255, 0.16) 100%
  );
  background-size: 300% 300%;
  animation: aurora-drift 26s ease-in-out infinite;
}

/* Decorative geometric shapes */
.geo-shapes {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.geo-ring {
  position: absolute;
  border-radius: 50%;
  border: 1.5px solid;

  &.ring-1 {
    width: 200px;
    height: 200px;
    right: 60px;
    top: -40px;
    border-color: rgba(47, 124, 246, 0.16);
    animation: float-particle 16s ease-in-out infinite;
  }

  &.ring-2 {
    width: 140px;
    height: 140px;
    right: 140px;
    top: 20px;
    border-color: rgba(79, 195, 232, 0.16);
    animation: float-particle 18s ease-in-out infinite 2s;
  }

  &.ring-3 {
    width: 80px;
    height: 80px;
    right: 220px;
    bottom: 10px;
    border-color: rgba(165, 228, 242, 0.22);
    animation: float-particle 14s ease-in-out infinite 1s;
  }
}

.geo-dot {
  position: absolute;
  border-radius: 50%;

  &.dot-1 {
    width: 8px;
    height: 8px;
    right: 180px;
    top: 30px;
    background: rgba(47, 124, 246, 0.35);
    box-shadow: 0 0 12px rgba(47, 124, 246, 0.28);
    animation: glow-pulse 4s ease-in-out infinite;
  }

  &.dot-2 {
    width: 6px;
    height: 6px;
    right: 100px;
    bottom: 30px;
    background: rgba(79, 195, 232, 0.38);
    box-shadow: 0 0 12px rgba(79, 195, 232, 0.28);
    animation: glow-pulse 5s ease-in-out infinite 1s;
  }
}

.banner-content {
  position: relative;
  z-index: 2;

  .banner-pill {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.8px;
    color: var(--ev-primary);
    background: rgba(255, 255, 255, 0.7);
    border: 1px solid var(--ev-border-default);
    box-shadow: var(--ev-shadow-xs);
    padding: 5px 14px;
    border-radius: 20px;
    margin-bottom: 16px;

    .pill-dot {
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: var(--ev-aqua);
      box-shadow: 0 0 8px rgba(79, 195, 232, 0.6);
      animation: glow-pulse 3s ease-in-out infinite;
    }
  }

  h1 {
    font-size: 30px;
    font-weight: 800;
    color: var(--ev-text-primary);
    margin-bottom: 10px;
    letter-spacing: -0.5px;
    line-height: 1.3;
  }

  p {
    font-size: 14px;
    color: var(--ev-text-secondary);
    max-width: 560px;
    line-height: 1.7;
  }
}

.aurora-text {
  background: linear-gradient(135deg, #2f7cf6 0%, #4fc3e8 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* ============================================================
   Stats Grid
   ============================================================ */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 18px;
}

.stat-card {
  position: relative;
  padding: 22px 20px;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: var(--ev-radius-lg);
  backdrop-filter: var(--ev-blur-md);
  -webkit-backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
  overflow: hidden;
  transition: all 0.35s var(--ev-ease-out);
  animation: page-enter 0.5s var(--ev-ease-out) both;

  &:hover {
    transform: translateY(-3px);
    border-color: rgba(var(--card-accent-rgb), 0.34);
    box-shadow: 0 12px 32px -10px rgba(var(--card-accent-rgb), 0.28), var(--ev-inset-gloss);

    .card-shimmer {
      animation: shimmer-sweep 0.8s ease forwards;
    }

    .card-glow {
      opacity: 1;
    }
  }

  .card-top-border {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 2px;
    background: linear-gradient(90deg, var(--card-accent), transparent);
    opacity: 0.7;
  }

  .card-shimmer {
    position: absolute;
    inset: 0;
    background: linear-gradient(90deg, transparent, rgba(255,255,255,0.5), transparent);
    transform: translateX(-100%) skewX(-15deg);
    pointer-events: none;
  }

  .card-glow {
    position: absolute;
    top: -20px;
    right: -20px;
    width: 100px;
    height: 100px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(var(--card-accent-rgb), 0.18) 0%, transparent 70%);
    opacity: 0;
    transition: opacity 0.3s ease;
    pointer-events: none;
  }

  .card-body {
    position: relative;
    z-index: 1;
    display: flex;
    align-items: center;
    gap: 16px;

    .icon-box {
      width: 50px;
      height: 50px;
      border-radius: 14px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--card-accent);
      flex-shrink: 0;
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
    }

    .info-box {
      .stat-value {
        font-size: 28px;
        font-weight: 800;
        color: var(--ev-text-primary);
        letter-spacing: -0.5px;
        line-height: 1.2;
      }

      .stat-label {
        font-size: 13px;
        color: var(--ev-text-secondary);
        margin-top: 2px;
      }
    }
  }
}

/* ============================================================
   Feature Section
   ============================================================ */
.feature-section {
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: var(--ev-radius-xl);
  padding: 32px;
  backdrop-filter: var(--ev-blur-md);
  -webkit-backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;

  h2 {
    font-size: 20px;
    font-weight: 700;
    color: var(--ev-text-primary);
    margin-bottom: 4px;
  }

  p {
    font-size: 13px;
    color: var(--ev-text-muted);
  }
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 18px;
}

.feature-card {
  position: relative;
  padding: 22px 20px 22px 24px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: var(--ev-radius-md);
  box-shadow: var(--ev-shadow-xs);
  overflow: hidden;
  transition: all 0.35s var(--ev-ease-out);
  animation: page-enter 0.5s var(--ev-ease-out) both;

  &:hover {
    transform: translateY(-3px);
    border-color: rgba(var(--feature-color-rgb), 0.34);
    box-shadow: 0 10px 26px -8px rgba(var(--feature-color-rgb), 0.24);

    .feature-hover-glow {
      opacity: 1;
    }
  }

  /* Left color bar */
  .feature-bar {
    position: absolute;
    left: 0;
    top: 12px;
    bottom: 12px;
    width: 3px;
    border-radius: 0 3px 3px 0;
    background: var(--feature-color);
    opacity: 0.5;
    transition: opacity 0.3s ease;
  }

  &:hover .feature-bar {
    opacity: 1;
    box-shadow: 0 0 8px rgba(var(--feature-color-rgb), 0.4);
  }

  .feature-hover-glow {
    position: absolute;
    inset: 0;
    background: radial-gradient(circle at 20% 50%, rgba(var(--feature-color-rgb), 0.10) 0%, transparent 60%);
    opacity: 0;
    transition: opacity 0.3s ease;
    pointer-events: none;
  }

  .feature-content {
    position: relative;
    z-index: 1;

    .feature-icon-box {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--feature-color);
      margin-bottom: 14px;
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
    }

    h3 {
      font-size: 16px;
      font-weight: 600;
      color: var(--ev-text-primary);
      margin-bottom: 8px;
    }

    p {
      font-size: 13px;
      color: var(--ev-text-secondary);
      line-height: 1.6;
    }
  }
}
</style>
