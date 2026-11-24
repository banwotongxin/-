import { reactive } from 'vue'

const TOKEN_KEY = 'accessToken'
const USER_KEY = 'user'

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch (e) {
    return null
  }
}

export const session = reactive({
  accessToken: localStorage.getItem(TOKEN_KEY) || '',
  user: readUser()
})

export function isLoggedIn() {
  return !!session.accessToken
}

export function hasRole(role) {
  const roles = session.user && session.user.roles
  return Array.isArray(roles) && roles.includes(role)
}

export function isPlatformAdmin() {
  return hasRole('PLATFORM_ADMIN')
}

export function isClubAdmin() {
  return hasRole('CLUB_ADMIN') || isPlatformAdmin()
}

export function isStudent() {
  return hasRole('STUDENT')
}

export function setSession(accessToken, user) {
  session.accessToken = accessToken || ''
  session.user = user || null
  if (session.accessToken) {
    localStorage.setItem(TOKEN_KEY, session.accessToken)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
  if (session.user) {
    localStorage.setItem(USER_KEY, JSON.stringify(session.user))
  } else {
    localStorage.removeItem(USER_KEY)
  }
}

export function updateUser(user) {
  if (!user) return
  session.user = user
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearSession() {
  session.accessToken = ''
  session.user = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export const ROLE_LABELS = {
  STUDENT: '学生',
  CLUB_ADMIN: '社团管理员',
  PLATFORM_ADMIN: '平台管理员'
}

export function roleText() {
  const roles = session.user && session.user.roles
  if (!Array.isArray(roles) || roles.length === 0) return '未分配角色'
  return roles.map((role) => ROLE_LABELS[role] || role).join(' / ')
}
