<template>
  <div class="main-layout">
    <el-container class="layout-container">
      <!-- Sidebar -->
      <el-aside :width="isCollapse ? '72px' : '248px'" class="sidebar">
        <!-- Logo area -->
        <div class="sidebar-logo" :class="{ 'is-collapse': isCollapse }">
          <div class="logo-icon">
            <img src="@/assets/logo.svg" alt="logo" class="logo-svg" />
          </div>
          <transition name="fade">
            <span v-if="!isCollapse" class="logo-text">EVERNOX</span>
          </transition>
        </div>

        <!-- Divider -->
        <div class="sidebar-divider"></div>

        <!-- Navigation -->
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          :collapse-transition="false"
          background-color="transparent"
          text-color="var(--ev-text-secondary)"
          active-text-color="var(--ev-primary)"
          class="sidebar-menu"
          router
          unique-opened
        >
          <!-- 1. 内容与资源 -->
          <el-sub-menu index="image-host">
            <template #title>
              <el-icon><Picture /></el-icon>
              <span>图床管理</span>
            </template>
            <el-menu-item index="/image-host">
              <el-icon><HomeFilled /></el-icon>
              <template #title>首页</template>
            </el-menu-item>
            <el-menu-item index="/image-host/public">
              <el-icon><View /></el-icon>
              <template #title>公开图床</template>
            </el-menu-item>
            <el-menu-item index="/image-host/public-albums">
              <el-icon><FolderOpened /></el-icon>
              <template #title>公开相册</template>
            </el-menu-item>
            <el-menu-item index="/image-host/my-images">
              <el-icon><PictureFilled /></el-icon>
              <template #title>我的图床</template>
            </el-menu-item>
            <el-menu-item index="/image-host/my-albums">
              <el-icon><Folder /></el-icon>
              <template #title>我的相册</template>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="site">
            <template #title>
              <el-icon><Link /></el-icon>
              <span>网站分享</span>
            </template>
            <el-menu-item index="/site/public">
              <el-icon><Compass /></el-icon>
              <template #title>网站导航</template>
            </el-menu-item>
            <el-menu-item index="/site/mine">
              <el-icon><Collection /></el-icon>
              <template #title>我的分享</template>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="topic">
            <template #title>
              <el-icon><ChatDotRound /></el-icon>
              <span>话题集中营</span>
            </template>
            <el-menu-item index="/topic/square">
              <el-icon><Compass /></el-icon>
              <template #title>广场</template>
            </el-menu-item>
            <el-menu-item index="/topic/circles">
              <el-icon><CirclePlusFilled /></el-icon>
              <template #title>圈子</template>
            </el-menu-item>
            <el-menu-item index="/topic/mine">
              <el-icon><User /></el-icon>
              <template #title>我的</template>
            </el-menu-item>
          </el-sub-menu>

          <!-- 2. 娱乐与查询 -->
          <el-sub-menu index="naruto">
            <template #title>
              <el-icon><Promotion /></el-icon>
              <span>火影忍者ol</span>
            </template>
            <el-menu-item index="/naruto/announcement">
              <el-icon><Bell /></el-icon>
              <template #title>公告</template>
            </el-menu-item>
            <el-menu-item index="/naruto/miao">
              <el-icon><ChatDotRound /></el-icon>
              <template #title>喵问答</template>
            </el-menu-item>
            <el-menu-item index="/naruto/quiz">
              <el-icon><QuestionFilled /></el-icon>
              <template #title>忍者测验</template>
            </el-menu-item>
            <el-menu-item index="/naruto/ninja">
              <el-icon><UserFilled /></el-icon>
              <template #title>忍者图鉴</template>
            </el-menu-item>
            <el-menu-item index="/naruto/org-points">
              <el-icon><DataAnalysis /></el-icon>
              <template #title>组织积分</template>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="ai">
            <template #title>
              <el-icon><Cpu /></el-icon>
              <span>AI 资讯</span>
            </template>
            <el-menu-item index="/ai/news">
              <el-icon><Document /></el-icon>
              <template #title>资讯动态</template>
            </el-menu-item>
            <el-menu-item index="/ai/leaderboard">
              <el-icon><Trophy /></el-icon>
              <template #title>模型排行</template>
            </el-menu-item>
            <el-menu-item index="/ai/ollama">
              <el-icon><Box /></el-icon>
              <template #title>Ollama 模型</template>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="games">
            <template #title>
              <el-icon><VideoPlay /></el-icon>
              <span>小游戏</span>
            </template>
            <el-menu-item index="/games/support-board">
              <el-icon><Brush /></el-icon>
              <template #title>应援板</template>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="utility">
            <template #title>
              <el-icon><Compass /></el-icon>
              <span>生活服务</span>
            </template>
            <el-menu-item index="/utility/earthquake">
              <el-icon><Location /></el-icon>
              <template #title>地震信息</template>
            </el-menu-item>
            <el-menu-item index="/utility/weather">
              <el-icon><PartlyCloudy /></el-icon>
              <template #title>天气查询</template>
            </el-menu-item>
          </el-sub-menu>

          <!-- 3. 个人效率 -->
          <el-sub-menu index="workspace">
            <template #title>
              <el-icon><Notebook /></el-icon>
              <span>个人工作台</span>
            </template>
            <el-menu-item index="/workspace/notes">
              <el-icon><EditPen /></el-icon>
              <template #title>记事本</template>
            </el-menu-item>
            <el-menu-item index="/workspace/notes/community">
              <el-icon><Reading /></el-icon>
              <template #title>公开笔记</template>
            </el-menu-item>
            <el-menu-item index="/workspace/todos">
              <el-icon><List /></el-icon>
              <template #title>待办</template>
            </el-menu-item>
            <el-menu-item index="/workspace/ledger">
              <el-icon><Wallet /></el-icon>
              <template #title>记账</template>
            </el-menu-item>
            <el-menu-item index="/workspace/performance">
              <el-icon><DataAnalysis /></el-icon>
              <template #title>记录绩效</template>
            </el-menu-item>
            <el-menu-item index="/workspace/salary">
              <el-icon><Money /></el-icon>
              <template #title>记录工资</template>
            </el-menu-item>
          </el-sub-menu>

          <!-- 4. 会员与管理：管理员固定最后，超级会员紧挨其上方 -->

          <el-sub-menu index="super-member">
            <template #title>
              <el-icon><StarFilled /></el-icon>
              <span class="admin-menu-title">
                <span>超级会员</span>
                <span v-if="orgPending > 0" class="menu-count">{{ orgPending }}</span>
              </span>
            </template>
            <el-menu-item index="/super-member/org">
              <el-icon><DataAnalysis /></el-icon>
              <template #title>组织管理</template>
            </el-menu-item>
            <!-- 项目发布：数据按账号隔离，超级会员 / 管理员都可用 -->
            <el-menu-item
              v-if="userStore.isAdmin || userStore.isSuperMember"
              index="/workspace/projects"
            >
              <el-icon><Box /></el-icon>
              <template #title>项目管理</template>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu v-if="userStore.isAdmin" index="admin">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span class="admin-menu-title">
                <span>管理员</span>
                <span v-if="approvalTotal > 0" class="menu-count">{{ approvalTotal }}</span>
              </span>
            </template>
            <el-menu-item index="/admin/users">
              <el-icon><UserFilled /></el-icon>
              <template #title>用户管理</template>
            </el-menu-item>

            <el-menu-item index="/admin/assets">

              <el-icon><PictureFilled /></el-icon>
              <template #title>相册图床</template>
            </el-menu-item>
            <el-menu-item index="/admin/sites">
              <el-icon><Link /></el-icon>
              <template #title>
                <span class="admin-menu-title">
                  <span>网站审批</span>
                  <span v-if="approval.sitePending > 0" class="menu-count">{{ approval.sitePending }}</span>
                </span>
              </template>
            </el-menu-item>
            <el-menu-item index="/admin/notes">
              <el-icon><Notebook /></el-icon>
              <template #title>
                <span class="admin-menu-title">
                  <span>笔记审批</span>
                  <span v-if="approval.notePending > 0" class="menu-count">{{ approval.notePending }}</span>
                </span>
              </template>
            </el-menu-item>
            <el-menu-item index="/admin/redemption">
              <el-icon><Ticket /></el-icon>
              <template #title>卡密管理</template>
            </el-menu-item>
            <el-menu-item index="/admin/visit">
              <el-icon><DataLine /></el-icon>
              <template #title>访问数据</template>
            </el-menu-item>
            <el-menu-item index="/admin/support-board">
              <el-icon><Brush /></el-icon>
              <template #title>应援板</template>
            </el-menu-item>
            <el-menu-item index="/admin/miao">
              <el-icon><ChatDotRound /></el-icon>
              <template #title>
                <span class="admin-menu-title">
                  <span>喵问答题库</span>
                  <span v-if="approval.miaoPending > 0" class="menu-count">{{ approval.miaoPending }}</span>
                </span>
              </template>
            </el-menu-item>
            <el-menu-item index="/admin/quiz">
              <el-icon><QuestionFilled /></el-icon>
              <template #title>
                <span class="admin-menu-title">
                  <span>测验审核</span>
                  <span v-if="approval.quizPending > 0" class="menu-count">{{ approval.quizPending }}</span>
                </span>
              </template>
            </el-menu-item>
            <el-menu-item index="/admin/topic">
              <el-icon><ChatDotRound /></el-icon>
              <template #title>话题管理</template>
            </el-menu-item>
            <el-menu-item index="/admin/points">
              <el-icon><Money /></el-icon>
              <template #title>积分会员</template>
            </el-menu-item>
            <el-menu-item index="/admin/announcement">
              <el-icon><Bell /></el-icon>
              <template #title>公告</template>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>

        <!-- Sidebar footer glow -->
        <div class="sidebar-footer-glow"></div>

        <!-- 底部：关于超链接 + 在线登录用户数，均不是菜单项 -->
        <div class="sidebar-foot" :class="{ 'is-collapse': isCollapse }">
          <a class="sidebar-about-link" @click.prevent="aboutVisible = true">
            <el-icon :size="13"><InfoFilled /></el-icon>
            <span v-if="!isCollapse">关于</span>
          </a>
          <span
            class="sidebar-online"
            :title="onlineCount === null ? '正在获取在线人数' : `当前在线登录用户 ${onlineCount} 人`"
          >
            <span class="online-dot"></span>
            <span class="online-text">
              {{ isCollapse ? (onlineCount ?? '–') : `在线 ${onlineCount ?? '–'}` }}
            </span>
          </span>
        </div>
      </el-aside>

      <!-- Main content area -->
      <el-container class="content-wrapper">
        <!-- Header -->
        <el-header class="header">
          <div class="header-left">
            <div class="collapse-btn" @click="toggleCollapse">
              <el-icon :size="18">
                <Expand v-if="isCollapse" />
                <Fold v-else />
              </el-icon>
            </div>
            <!-- Breadcrumb-like page indicator -->
            <div class="page-indicator">
              <span class="indicator-dot"></span>
              <span class="indicator-text">{{ currentPageTitle }}</span>
            </div>
          </div>

          <div class="header-right">
            <!-- 主题切换 -->
            <div class="theme-toggle" @click="themeDrawerVisible = true" title="主题设置">
              <el-icon :size="18"><Moon /></el-icon>
            </div>

            <!-- 公告铃铛 / 积分 / 用户菜单：仅登录态展示 -->
            <template v-if="userStore.isLoggedIn">
              <AnnouncementBell />

              <!-- 我的积分 -->
              <div class="points-badge" title="我的积分">
                <el-icon :size="16"><Coin /></el-icon>
                <span>{{ userStore.userInfo?.points ?? 0 }}</span>
              </div>

              <!-- User dropdown -->
              <el-dropdown @command="handleCommand" trigger="click">
                <div class="user-badge">
                  <div class="avatar-ring">
                    <el-avatar :size="32" icon="UserFilled" />
                  </div>
                  <div class="user-meta">
                    <span class="user-name">{{ userStore.userInfo?.username || '用户' }}</span>
                    <span class="user-role">{{ userRoleName }}</span>
                  </div>
                  <el-icon :size="14" class="dropdown-arrow"><ArrowDown /></el-icon>
                </div>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="profile">
                      <el-icon><User /></el-icon>个人信息
                    </el-dropdown-item>
                    <el-dropdown-item command="logout">
                      <el-icon><SwitchButton /></el-icon>退出登录
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>

            <!-- 匿名访问：仅保留登录入口，注册在登录弹窗底部切换 -->
            <template v-else>
              <el-button type="primary" class="login-entry-btn" @click="authDialog.open('login')">
                登录
              </el-button>
            </template>
          </div>
        </el-header>

        <TagsView />

        <!-- Content -->
        <el-main class="main-content">
          <router-view v-slot="{ Component }">
            <transition name="page-fade" mode="out-in">
              <!-- 只缓存 KEEP_ALIVE_NAMES 里的只读页；其余页面不匹配 include → 照旧每次重挂。
                   名字对不上时只是不缓存，不会报错（安全降级）。 -->
              <keep-alive :include="KEEP_ALIVE_NAMES" :max="6">
                <component :is="Component" />
              </keep-alive>
            </transition>
          </router-view>
        </el-main>
      </el-container>
    </el-container>

    <ThemeSettingDrawer v-model="themeDrawerVisible" />
    <AboutDialog v-model="aboutVisible" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { UserRoleMap, UserRole } from '@/types/user'
import type { ApprovalSummary } from '@/types/user'
import { getApprovalSummary } from '@/api/admin'
import { getOrgApplicationCount } from '@/api/org'
import { getOnlineCount } from '@/api/points'
import AnnouncementBell from '@/components/AnnouncementBell.vue'
import TagsView from '@/components/TagsView.vue'
import ThemeSettingDrawer from '@/components/ThemeSettingDrawer.vue'
import AboutDialog from '@/components/AboutDialog.vue'
import { useTabsStore } from '@/stores/tabs'
import { useAuthDialogStore } from '@/stores/authDialog'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const tabsStore = useTabsStore()
const authDialog = useAuthDialogStore()

// 路由切换时登记多标签页
watch(
  () => route.fullPath,
  () => tabsStore.addTab(route.path, (route.meta?.title as string) || ''),
  { immediate: true }
)

const isCollapse = ref(false)
const themeDrawerVisible = ref(false)
const aboutVisible = ref(false)

// ---------- 在线登录用户数（复用积分 SSE 连接统计） ----------
// null = 还没取到：显示「–」，避免先闪一个错误的 0
const onlineCount = ref<number | null>(null)
let onlineTimer: number | undefined

const loadOnlineCount = async () => {
  try {
    const res = await getOnlineCount()
    if (typeof res?.data === 'number') onlineCount.value = res.data
  } catch {
    /* 静默失败，保留上一次数值，避免侧边栏常驻报错 */
  }
}

// 尽早发出首个请求：放在 setup 顶层，早于 SSE 建连，避免被长连接占满并发槽位后排队
void loadOnlineCount()

const handleVisibilityChange = () => {
  if (document.visibilityState === 'visible') void loadOnlineCount()
}

/**
 * 需要 keep-alive 缓存的页面组件名。
 *
 * 只放「只读、且不依赖实时数据」的页：模型排行榜与 Ollama 模型库。
 * 它们各自都有手动同步按钮，二次进入直接复用缓存组件——不重挂、也不重新发列表请求，
 * 所以切菜单几乎瞬时；其余页面照旧每次重挂，保证数据实时性。
 *
 * 名字由各页的 defineOptions({ name }) 声明。名字对不上时 keep-alive 只是不缓存
 * （不会报错），属于安全降级。
 */
const KEEP_ALIVE_NAMES = ['AiArenaLeaderboard', 'AiOllamaModels']

const activeMenu = computed(() => {
  if (route.path.startsWith('/workspace/projects')) return '/workspace/projects'
  return route.path
})



const userRoleName = computed(() => {
  return UserRoleMap[userStore.userRole as UserRole] || '普通成员'
})

const currentPageTitle = computed(() => {
  return (route.meta?.title as string) || '控制台'
})

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

const handleCommand = (command: string) => {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    // 退出后留在当前页：个人页会自动切到「登录后查看」空状态
    userStore.logout()
    approval.sitePending = 0
    approval.notePending = 0
    approval.quizPending = 0
    approval.miaoPending = 0
    orgPending.value = 0
  }
}

// ---------- 管理员待审批红点 ----------
const approval = reactive<ApprovalSummary>({ sitePending: 0, notePending: 0, quizPending: 0, miaoPending: 0 })
const approvalTotal = computed(() =>
  approval.sitePending + approval.notePending + approval.quizPending + approval.miaoPending
)
const orgPending = ref(0)
let approvalTimer: number | undefined

const loadApproval = async () => {
  if (!userStore.isAdmin) return
  try {
    const res = await getApprovalSummary()
    const d = res?.data
    approval.sitePending = d?.sitePending ?? 0
    approval.notePending = d?.notePending ?? 0
    approval.quizPending = d?.quizPending ?? 0
    approval.miaoPending = d?.miaoPending ?? 0
  } catch {
    /* 忽略 */
  }
}

const loadOrgPending = async () => {
  if (!userStore.isAdmin && !userStore.isSuperMember) return
  try {
    const res = await getOrgApplicationCount()
    orgPending.value = res?.data ?? 0
  } catch {
    /* 忽略 */
  }
}

let pointsSource: EventSource | null = null
const connectPointsStream = () => {
  if (pointsSource) {
    pointsSource.close()
    pointsSource = null
  }
  const token = localStorage.getItem('accessToken')
  if (!token) return
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  pointsSource = new EventSource(`${base}/points/stream?token=${encodeURIComponent(token)}`)
  // 后端 register 成功后立刻推送 ready：此时本连接已计入在线数，马上刷新一次，
  // 否则要等 30 秒轮询才会把自己算进去
  pointsSource.addEventListener('ready', () => {
    void loadOnlineCount()
  })
  pointsSource.addEventListener('points-changed', (e) => {
    try {
      const data = JSON.parse((e as MessageEvent).data)
      if (typeof data?.points === 'number') {
        userStore.setPoints(data.points)
      }
    } catch {
      /* 忽略 */
    }
  })
}

onMounted(() => {
  if (userStore.isAdmin || userStore.isSuperMember) {
    void loadApproval()
    void loadOrgPending()
    approvalTimer = window.setInterval(() => {
      void loadApproval()
      void loadOrgPending()
    }, 60000)
  }
  connectPointsStream()
  onlineTimer = window.setInterval(() => void loadOnlineCount(), 30000)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

// token 变化（登录 / 登出 / 自动续期）时重连，避免 SSE 一直拿着旧 token 报 401
watch(() => userStore.token, () => connectPointsStream())

// 登录态刚变化时 SSE 还在建连/断开，稍等再取在线人数
watch(() => userStore.token, () => {
  window.setTimeout(() => void loadOnlineCount(), 1500)
})

// 登录后补拉管理员/会员角标（匿名进入时不会发起任何个人接口请求）
watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (!loggedIn) return
    if (userStore.isAdmin || userStore.isSuperMember) {
      void loadApproval()
      void loadOrgPending()
    }
  }
)

onBeforeUnmount(() => {
  if (approvalTimer) window.clearInterval(approvalTimer)
  if (onlineTimer) window.clearInterval(onlineTimer)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  if (pointsSource) pointsSource.close()
})
</script>

<style scoped lang="scss">
.main-layout {
  width: 100%;
  height: 100%;
  background: transparent;
  overflow: hidden;
}

.layout-container {
  height: 100%;
  width: 100%;
}

/* ============================================================
   Sidebar
   ============================================================ */
.sidebar {
  position: relative;
  /* 不再使用 backdrop-filter：背景是平滑静止的渐变，模糊肉眼无差，
     却会让整条侧栏在每次合成时重新采样背景（高 DPR 屏上逐帧开销显著）。 */
  background: linear-gradient(180deg,
    var(--ev-bg-glass-strong) 0%,
    var(--ev-bg-glass) 60%,
    var(--ev-bg-glass-light) 100%
  );
  border-right: 1px solid var(--ev-border-subtle);
  box-shadow: inset -1px 0 0 var(--ev-border-gloss), var(--ev-shadow-sm);
  transition: width 0.35s var(--ev-ease-out);
  display: flex;
  flex-direction: column;
  z-index: 20;
  overflow: hidden;
}

/* Logo */
.sidebar-logo {
  height: 64px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
  transition: all 0.35s var(--ev-ease-out);

  &.is-collapse {
    justify-content: center;
    padding: 0;
  }
}

.logo-icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(var(--ev-primary-rgb), 0.14), rgba(var(--ev-aqua-rgb), 0.14));
  border: 1px solid var(--ev-border-default);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: var(--ev-shadow-xs),     var(--ev-inset-gloss);
  transition: all 0.3s ease;

  &:hover {
    box-shadow: var(--ev-glow-violet),     var(--ev-inset-gloss);
    border-color: var(--ev-border-hover);
  }

  .logo-svg {
    width: 24px;
    height: 24px;
  }
}

.logo-text {
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 2.5px;
  background: linear-gradient(135deg, var(--ev-primary) 0%, var(--ev-aqua) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  white-space: nowrap;
}

/* Divider */
.sidebar-divider {
  height: 1px;
  margin: 0 16px 8px;
  background: linear-gradient(90deg,
    transparent,
    rgba(var(--ev-primary-rgb), 0.22),
    rgba(var(--ev-aqua-rgb), 0.22),
    transparent
  );
  flex-shrink: 0;
}

/* Menu */
.sidebar-menu {
  border-right: none !important;
  padding: 8px 10px;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;

  /* 收起态：EP 固定 64px 宽 + 菜单项 20px 内边距 + 此处 10px 内边距叠加，图标会右偏；
     改为撑满 aside 宽度、去掉左右内边距，并让菜单项用 flex 居中图标 */
  &.el-menu--collapse {
    width: 100% !important;
    padding-left: 0 !important;
    padding-right: 0 !important;

    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      justify-content: center !important;
      padding-left: 0 !important;
      padding-right: 0 !important;
    }

    /* 自定义标题必须退出布局，否则图标无法居中。
       EP 的收起样式只把 `.el-menu-item > span` / `.el-sub-menu__title > span` 置为
       visibility:hidden + 宽高 0，并没有重置 flex；而 .admin-menu-title 自带 flex:1
       （flex-basis:0 且可增长），于是这个「看不见」的标题仍然吃掉整行剩余宽度，
       把图标挤到最左边（超级会员 / 管理员 / 网站审批 / 笔记审批 / 忍者测验管理 都受影响）。
       改成不伸缩、不占位即可；刻意不用 display:none，避免连带影响收起态弹出层里的标题。 */
    .admin-menu-title {
      flex: 0 0 auto !important;
      min-width: 0 !important;
    }
  }

  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    border-radius: 10px !important;
    margin: 3px 0 !important;
    height: 44px !important;
    line-height: 44px !important;
    transition: all 0.15s var(--ev-ease-out) !important;
    position: relative;

    &:hover {
      background: var(--ev-bg-tint) !important;
      color: var(--ev-primary) !important;
    }
  }

  :deep(.el-menu-item.is-active) {
    background: linear-gradient(135deg,
      rgba(var(--ev-primary-rgb), 0.12) 0%,
      rgba(var(--ev-aqua-rgb), 0.08) 100%
    ) !important;
    color: var(--ev-primary) !important;
    font-weight: 600 !important;
    box-shadow: var(--ev-inset-gloss) !important;

    /* Active indicator bar */
    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 20px;
      border-radius: 0 3px 3px 0;
      background: var(--ev-grad-aurora);
      box-shadow: 0 0 8px rgba(var(--ev-primary-rgb), 0.35);
    }
  }

  :deep(.el-sub-menu .el-menu-item) {
    min-width: auto !important;
  }

  :deep(.el-sub-menu__icon-arrow) {
    color: var(--ev-text-muted) !important;
  }
}

/* 侧边栏最底部：关于超链接 + 在线人数，贴在最底部，无边框无底色 */
.sidebar-foot {
  position: relative;
  z-index: 2;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 16px 14px;

  &.is-collapse {
    flex-direction: column;
    gap: 6px;
    padding: 4px 0 12px;
  }
}

.sidebar-about-link {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 11.5px;
  letter-spacing: 0.6px;
  color: var(--ev-text-muted);
  text-decoration: none;
  cursor: pointer;
  user-select: none;
  transition: color 0.2s var(--ev-ease-out);

  &:hover {
    color: var(--ev-primary);

    span {
      text-decoration: underline;
      text-underline-offset: 3px;
    }
  }
}

.sidebar-online {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  color: var(--ev-text-muted);
  user-select: none;
  white-space: nowrap;
}

.online-dot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--ev-aqua);
  box-shadow: 0 0 6px rgba(var(--ev-aqua-rgb), 0.7);
  animation: online-breathe 2.4s ease-in-out infinite;
}

.online-text {
  letter-spacing: 0.3px;
}

@keyframes online-breathe {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.35;
  }
}

/* Footer glow：铺在侧栏底部，作为「关于」链接的背景 */
.sidebar-footer-glow {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 96px;
  z-index: 1;
  pointer-events: none;
  background: radial-gradient(ellipse at 50% 100%, rgba(var(--ev-aqua-rgb), 0.12) 0%, transparent 70%);
}

/* ============================================================
   Header
   ============================================================ */
.content-wrapper {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  height: 60px;
  /* 同 .sidebar：去掉 backdrop-filter，避免大块表面每次合成重新采样背景 */
  background: var(--ev-bg-glass);
  border-bottom: 1px solid var(--ev-border-subtle);
  box-shadow: var(--ev-inset-gloss), var(--ev-shadow-xs);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
    background: var(--ev-bg-glass);
  border: 1px solid var(--ev-border-default);
  box-shadow: var(--ev-shadow-xs),     var(--ev-inset-gloss);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--ev-text-secondary);
  transition: all 0.25s var(--ev-ease-out);

  &:hover {
    color: var(--ev-primary);
    border-color: var(--ev-border-hover);
    background: var(--ev-bg-elevated);
    box-shadow: var(--ev-glow-violet);
  }
}

.page-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.indicator-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ev-aqua);
  box-shadow: 0 0 8px rgba(var(--ev-aqua-rgb), 0.55);
}

.indicator-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--ev-text-secondary);
  letter-spacing: 0.3px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.login-entry-btn {
  border-radius: 10px;
}

.points-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  height: 32px;
  border-radius: 999px;
  background: var(--ev-bg-glass);
  border: 1px solid var(--ev-border-default);
  box-shadow: var(--ev-shadow-xs), var(--ev-inset-gloss);
  font-size: 13px;
  font-weight: 600;
  color: var(--ev-warning);
  cursor: default;

  .el-icon {
    color: var(--ev-warning);
  }
}

.theme-toggle {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--ev-bg-glass);
  border: 1px solid var(--ev-border-default);
  box-shadow: var(--ev-shadow-xs), var(--ev-inset-gloss);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--ev-text-secondary);
  transition: all 0.25s var(--ev-ease-out);

  &:hover {
    color: var(--ev-primary);
    border-color: var(--ev-border-hover);
    background: var(--ev-bg-glass-strong);
  }
}

.header-icon-btn {
  position: relative;
  width: 36px;
  height: 36px;
  border-radius: 10px;
    background: var(--ev-bg-glass);
  border: 1px solid var(--ev-border-default);
  box-shadow: var(--ev-shadow-xs),     var(--ev-inset-gloss);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--ev-text-secondary);
  transition: all 0.25s var(--ev-ease-out);

  &:hover {
    color: var(--ev-primary);
    border-color: var(--ev-border-hover);
    background: var(--ev-bg-elevated);
  }

  .notification-dot {
    position: absolute;
    top: 7px;
    right: 7px;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--ev-danger);
    box-shadow: 0 0 6px rgba(242, 99, 127, 0.55);
  }
}

/* User badge */
.user-badge {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 12px 4px 4px;
    background: var(--ev-bg-glass);
  border: 1px solid var(--ev-border-default);
  box-shadow: var(--ev-shadow-xs),     var(--ev-inset-gloss);
  border-radius: 30px;
  cursor: pointer;
  transition: all 0.25s var(--ev-ease-out);

  &:hover {
    border-color: var(--ev-border-hover);
    background: var(--ev-bg-elevated);
    box-shadow: var(--ev-shadow-sm);
  }
}

.avatar-ring {
  padding: 2px;
  border-radius: 50%;
  background: var(--ev-grad-aurora);

  :deep(.el-avatar) {
    background: var(--ev-grad-aurora) !important;
    color: var(--ev-text-on-accent) !important;
    border: 2px solid var(--ev-bg-elevated) !important;
  }
}

.user-meta {
  display: flex;
  flex-direction: column;

  .user-name {
    font-size: 13px;
    font-weight: 600;
    color: var(--ev-text-primary);
    line-height: 1.2;
  }

  .user-role {
    font-size: 11px;
    color: var(--ev-primary);
    line-height: 1.2;
  }
}

.dropdown-arrow {
  color: var(--ev-text-muted);
  transition: transform 0.2s ease;
}

/* 管理员菜单待审批红点 */
.admin-menu-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.menu-count {
  flex-shrink: 0;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--ev-danger);
  color: #ffffff;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
}

/* ============================================================
   Main Content
   ============================================================ */
.main-content {
  flex: 1;
  padding: 24px;
  background: transparent;
  overflow-y: auto;
  overflow-x: hidden;
  position: relative;

  /* Subtle aqua wash in the top-right corner */
  &::before {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    width: 60%;
    height: 45%;
    background: radial-gradient(ellipse at top right, rgba(var(--ev-aqua-rgb), 0.10) 0%, transparent 62%);
    pointer-events: none;
    z-index: 0;
  }
}

/* Transition */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
