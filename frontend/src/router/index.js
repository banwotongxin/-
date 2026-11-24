import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { session, hasRole } from '../store/session'
import BasicLayout from '../layouts/BasicLayout.vue'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/Register.vue'),
    meta: { public: true, title: '注册' }
  },
  {
    path: '/',
    component: BasicLayout,
    redirect: '/activities',
    children: [
      {
        path: 'clubs',
        name: 'clubs',
        component: () => import('../views/ClubList.vue'),
        meta: { title: '社团信息' }
      },
      {
        path: 'activities',
        name: 'activities',
        component: () => import('../views/ActivityList.vue'),
        meta: { title: '活动中心' }
      },
      {
        path: 'activities/:id',
        name: 'activity-detail',
        component: () => import('../views/ActivityDetail.vue'),
        meta: { title: '活动详情' }
      },
      {
        path: 'my-signups',
        name: 'my-signups',
        component: () => import('../views/MySignups.vue'),
        meta: { title: '我的报名' }
      },
      {
        path: 'messages',
        name: 'messages',
        component: () => import('../views/Messages.vue'),
        meta: { title: '消息中心' }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('../views/Profile.vue'),
        meta: { title: '个人资料' }
      },
      {
        path: 'club/apply',
        name: 'club-apply',
        component: () => import('../views/club/ClubApply.vue'),
        meta: { title: '社团入驻申请', roles: ['STUDENT'] }
      },
      {
        path: 'club/info',
        name: 'club-info',
        component: () => import('../views/club/ClubInfo.vue'),
        meta: { title: '社团资料维护', roles: ['CLUB_ADMIN', 'PLATFORM_ADMIN'] }
      },
      {
        path: 'club/activities',
        name: 'club-activities',
        component: () => import('../views/club/ClubActivities.vue'),
        meta: { title: '社团活动管理', roles: ['CLUB_ADMIN', 'PLATFORM_ADMIN'] }
      },
      {
        path: 'club/review',
        name: 'club-review',
        component: () => import('../views/club/Review.vue'),
        meta: { title: '报名审核', roles: ['CLUB_ADMIN', 'PLATFORM_ADMIN'] }
      },
      {
        path: 'club/signin',
        name: 'club-signin',
        component: () => import('../views/club/Signin.vue'),
        meta: { title: '签到管理', roles: ['CLUB_ADMIN', 'PLATFORM_ADMIN'] }
      },
      {
        path: 'club/archive',
        name: 'club-archive',
        component: () => import('../views/club/Archive.vue'),
        meta: { title: '资料归档', roles: ['CLUB_ADMIN', 'PLATFORM_ADMIN'] }
      },
      {
        path: 'club/statistics',
        name: 'club-statistics',
        component: () => import('../views/platform/Statistics.vue'),
        meta: { title: '社团统计', roles: ['CLUB_ADMIN', 'PLATFORM_ADMIN'] }
      },
      {
        path: 'platform/clubs',
        name: 'platform-clubs',
        component: () => import('../views/platform/Clubs.vue'),
        meta: { title: '入驻审核', roles: ['PLATFORM_ADMIN'] }
      },
      {
        path: 'platform/statistics',
        name: 'platform-statistics',
        component: () => import('../views/platform/Statistics.vue'),
        meta: { title: '平台统计', roles: ['PLATFORM_ADMIN'] }
      },
      {
        path: 'platform/audit',
        name: 'platform-audit',
        component: () => import('../views/platform/AuditLogs.vue'),
        meta: { title: '操作审计', roles: ['PLATFORM_ADMIN'] }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/activities'
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

router.beforeEach((to) => {
  const loggedIn = !!session.accessToken
  if (to.meta.public) {
    return loggedIn ? { path: '/activities' } : true
  }
  if (!loggedIn) {
    return { path: '/login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
  }
  const roles = to.meta.roles
  if (Array.isArray(roles) && roles.length > 0 && !roles.some((role) => hasRole(role))) {
    ElMessage.warning('无权访问该页面')
    return { path: '/activities' }
  }
  return true
})

router.afterEach((to) => {
  const base = '校园活动报名与社团信息管理系统'
  document.title = to.meta.title ? `${to.meta.title} - ${base}` : base
})

export default router
