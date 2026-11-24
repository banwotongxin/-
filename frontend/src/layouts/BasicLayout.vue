<template>
  <el-container class="app-shell">
    <el-aside width="232px" class="app-aside">
      <div class="brand">
        <div class="brand-title">校园活动报名与社团信息管理系统</div>
        <div class="brand-sub">活动发布 · 报名审核 · 签到归档</div>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        class="app-menu"
        background-color="transparent"
        text-color="#c3ccda"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/activities">活动中心</el-menu-item>
        <el-menu-item index="/clubs">社团信息</el-menu-item>
        <el-menu-item v-if="student" index="/club/apply">社团入驻</el-menu-item>
        <el-menu-item index="/my-signups">我的报名</el-menu-item>
        <el-menu-item index="/messages">
          <span class="menu-badge">
            消息中心
            <el-badge :value="unread" :max="99" :hidden="unread === 0" type="danger" />
          </span>
        </el-menu-item>
        <el-menu-item index="/profile">个人资料</el-menu-item>
        <el-sub-menu v-if="clubAdmin" index="/club">
          <template #title>社团管理</template>
          <el-menu-item index="/club/info">社团资料</el-menu-item>
          <el-menu-item index="/club/activities">活动管理</el-menu-item>
          <el-menu-item index="/club/review">报名审核</el-menu-item>
          <el-menu-item index="/club/signin">签到管理</el-menu-item>
          <el-menu-item index="/club/archive">资料归档</el-menu-item>
          <el-menu-item index="/club/statistics">社团统计</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="platformAdmin" index="/platform">
          <template #title>平台管理</template>
          <el-menu-item index="/platform/clubs">入驻审核</el-menu-item>
          <el-menu-item index="/platform/statistics">平台统计</el-menu-item>
          <el-menu-item index="/platform/audit">操作审计</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div class="header-title">{{ pageTitle }}</div>
        <div class="header-user">
          <span>{{ userName }}</span>
          <el-tag size="small" type="info">{{ roleName }}</el-tag>
          <el-button link type="primary" @click="logout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { session, roleText, clearSession, updateUser, isStudent, isClubAdmin, isPlatformAdmin } from '../store/session'
import { fetchUnreadCount } from '../api/message'
import { fetchMe } from '../api/auth'

const route = useRoute()
const router = useRouter()
const unread = ref(0)
let unreadTimer = null

const userName = computed(() => {
  const user = session.user || {}
  return user.realName || user.username || '未登录'
})
const roleName = computed(() => roleText())
const student = computed(() => isStudent())
const clubAdmin = computed(() => isClubAdmin())
const platformAdmin = computed(() => isPlatformAdmin())
const pageTitle = computed(() => route.meta.title || '工作台')
const activeMenu = computed(() => {
  if (route.path.startsWith('/activities')) return '/activities'
  return route.path
})

async function loadUnread() {
  if (!session.accessToken) return
  try {
    const data = await fetchUnreadCount()
    unread.value = Number((data && data.unreadCount) || 0)
  } catch (e) {
    unread.value = 0
  }
}

function logout() {
  clearSession()
  ElMessage.success('已退出登录')
  router.replace('/login')
}

async function refreshMe() {
  if (!session.accessToken) return
  try {
    const data = await fetchMe()
    updateUser(data)
  } catch (e) {
    // 令牌失效由请求拦截器统一处理
  }
}

onMounted(() => {
  refreshMe()
  loadUnread()
  unreadTimer = window.setInterval(loadUnread, 60000)
})
onUnmounted(() => {
  if (unreadTimer) {
    window.clearInterval(unreadTimer)
    unreadTimer = null
  }
})
watch(() => route.path, loadUnread)
</script>
