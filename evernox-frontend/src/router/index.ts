import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false, title: '登录' },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { requiresAuth: false, title: '注册' },
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/ForgotPassword.vue'),
    meta: { requiresAuth: false, title: '找回密码' },
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Home',
        redirect: '/image-host',
      },
      // 图床管理
      {
        path: 'image-host',
        name: 'ImageHostHome',
        component: () => import('@/views/image-host/ImageHostHome.vue'),
        meta: { title: '图床首页' },
      },
      {
        path: 'image-host/public',
        name: 'PublicImages',
        component: () => import('@/views/image-host/PublicImages.vue'),
        meta: { title: '公开图床' },
      },
      {
        path: 'image-host/public-albums',
        name: 'PublicAlbums',
        component: () => import('@/views/image-host/PublicAlbums.vue'),
        meta: { title: '公开相册' },
      },
      {
        path: 'image-host/my-images',
        name: 'MyImages',
        component: () => import('@/views/image-host/MyImages.vue'),
        meta: { title: '我的图床' },
      },
      {
        path: 'image-host/my-albums',
        name: 'MyAlbums',
        component: () => import('@/views/image-host/MyAlbums.vue'),
        meta: { title: '我的相册' },
      },
      {
        path: 'image/:id',
        name: 'ImageDetail',
        component: () => import('@/views/image-host/ImageDetail.vue'),
        meta: { title: '图片详情', requiresAuth: false },
      },
      // 网站分享
      {
        path: 'site/public',
        name: 'PublicSites',
        component: () => import('@/views/site/PublicSites.vue'),
        meta: { title: '网站导航' },
      },
      {
        path: 'site/mine',
        name: 'MySites',
        component: () => import('@/views/site/MySites.vue'),
        meta: { title: '我的分享' },
      },
      // 火影忍者ol
      {
        path: 'naruto/announcement',
        name: 'HyolAnnouncement',
        component: () => import('@/views/naruto/HyolAnnouncement.vue'),
        meta: { title: '公告' },
      },
      {
        path: 'naruto/ninja',
        name: 'NinjaGuide',
        component: () => import('@/views/naruto/NinjaGuide.vue'),
        meta: { title: '忍者图鉴' },
      },
      {
        path: 'naruto/quiz',
        name: 'Quiz',
        component: () => import('@/views/naruto/Quiz.vue'),
        meta: { title: '忍者测验' },
      },
      // 个人工作台
      {
        path: 'workspace/notes',
        name: 'Notes',
        component: () => import('@/views/workspace/Notes.vue'),
        meta: { title: '记事本' },
      },
      {
        path: 'workspace/todos',
        name: 'Todos',
        component: () => import('@/views/workspace/Todos.vue'),
        meta: { title: '待办' },
      },
      {
        path: 'workspace/ledger',
        name: 'Ledger',
        component: () => import('@/views/workspace/Ledger.vue'),
        meta: { title: '记账' },
      },
      {
        path: 'workspace/performance',
        name: 'Performance',
        component: () => import('@/views/workspace/Performance.vue'),
        meta: { title: '记录绩效' },
      },
      {
        path: 'workspace/salary',
        name: 'Salary',
        component: () => import('@/views/workspace/Salary.vue'),
        meta: { title: '记录工资' },
      },
      // 话题集中营
      {
        path: 'topic/square',
        name: 'TopicSquare',
        component: () => import('@/views/topic/TopicSquare.vue'),
        meta: { title: '广场' },
      },
      {
        path: 'topic/circles',
        name: 'TopicCircles',
        component: () => import('@/views/topic/TopicCircles.vue'),
        meta: { title: '圈子' },
      },
      {
        path: 'topic/mine',
        name: 'TopicMine',
        component: () => import('@/views/topic/TopicMine.vue'),
        meta: { title: '我的' },
      },
      {
        path: 'topic/circle/:id',
        name: 'TopicCircleDetail',
        component: () => import('@/views/topic/TopicCircleDetail.vue'),
        meta: { title: '圈子详情' },
      },
      // 管理员
      {
        path: 'admin/users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/AdminUsers.vue'),
        meta: { title: '用户管理', requiresAdmin: true },
      },
      {
        path: 'admin/assets',
        name: 'AdminAssets',
        component: () => import('@/views/admin/AdminAssets.vue'),
        meta: { title: '相册图床', requiresAdmin: true },
      },
      {
        path: 'admin/sites',
        name: 'AdminSites',
        component: () => import('@/views/admin/AdminSites.vue'),
        meta: { title: '网站审批', requiresAdmin: true },
      },
      {
        path: 'admin/notes',
        name: 'AdminNotes',
        component: () => import('@/views/admin/AdminNotes.vue'),
        meta: { title: '笔记审批', requiresAdmin: true },
      },
      {
        path: 'admin/announcement',
        name: 'AdminAnnouncement',
        component: () => import('@/views/admin/AdminAnnouncement.vue'),
        meta: { title: '公告', requiresAdmin: true },
      },
      {
        path: 'admin/topic',
        name: 'AdminTopic',
        component: () => import('@/views/admin/AdminTopic.vue'),
        meta: { title: '话题集中营管理', requiresAdmin: true },
      },
      {
        path: 'admin/quiz',
        name: 'AdminQuiz',
        component: () => import('@/views/admin/AdminQuiz.vue'),
        meta: { title: '忍者测验管理', requiresAdmin: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()

  // 有 token 但 userInfo 未加载（刷新后）→ 重新获取用户信息
  if (userStore.token && !userStore.userInfo) {
    await userStore.fetchUserInfo()
  }

  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    next('/login')
  } else if (to.meta.requiresAdmin && !userStore.isAdmin) {
    next('/')
  } else if ((to.path === '/login' || to.path === '/register') && userStore.isLoggedIn) {
    next('/')
  } else {
    next()
  }
})

router.afterEach((to) => {
  // 设置页面标题
  const baseTitle = 'EverNox - 永夜照相馆'
  if (to.meta.title) {
    document.title = `${to.meta.title} | ${baseTitle}`
  } else {
    document.title = baseTitle
  }
})

export default router
