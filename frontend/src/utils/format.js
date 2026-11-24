export const ACTIVITY_STATUS = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  FULL: '名额已满',
  CLOSED: '已关闭',
  ARCHIVED: '已归档'
}

export const SIGNUP_STATUS = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已撤销'
}

export const CLUB_STATUS = {
  PENDING: '待审核',
  ACTIVE: '正常',
  FROZEN: '已冻结',
  REJECTED: '已驳回'
}

export const CLUB_APPLICATION_STATUS = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回'
}

export const SIGNIN_SOURCE = {
  NORMAL: '正常签到',
  MANUAL: '补签',
  SUPPLEMENT: '补签'
}

export const VISIBILITY = {
  PUBLIC: '公开',
  CLUB_ONLY: '仅社团成员',
  PRIVATE: '仅管理员'
}

export const MESSAGE_TYPE = {
  SIGNUP_PENDING: '报名待审',
  SIGNUP_RESULT: '报名结果',
  ACTIVITY_CHANGE: '活动变更',
  SIGNIN_RESULT: '签到结果',
  CLUB_APPLICATION: '社团入驻'
}

export function labelOf(map, value) {
  if (value === null || value === undefined || value === '') return '—'
  return map[value] || String(value)
}

export function activityTagType(status) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'FULL') return 'warning'
  return 'info'
}

export function signupTagType(status) {
  if (status === 'APPROVED') return 'success'
  if (status === 'PENDING') return 'warning'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}

export function clubTagType(status) {
  if (status === 'ACTIVE' || status === 'APPROVED') return 'success'
  if (status === 'FROZEN' || status === 'REJECTED') return 'danger'
  if (status === 'PENDING') return 'warning'
  return 'info'
}

export function formatDateTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

export function formatDate(value) {
  if (!value) return '—'
  return String(value).slice(0, 10)
}

export function toIso(value) {
  if (!value) return null
  return String(value).replace(' ', 'T').slice(0, 19)
}

export function toDateInput(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}

export function pad2(value) {
  return String(value).padStart(2, '0')
}

export function dateText(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

export function extractPage(data) {
  if (Array.isArray(data)) {
    return { records: data, total: data.length, pageNum: 1, pageSize: data.length || 10 }
  }
  if (data && Array.isArray(data.records)) {
    return {
      records: data.records,
      total: Number(data.total || 0),
      pageNum: Number(data.pageNum || 1),
      pageSize: Number(data.pageSize || 10)
    }
  }
  if (data && Array.isArray(data.list)) {
    return { records: data.list, total: data.list.length, pageNum: 1, pageSize: data.list.length || 10 }
  }
  return { records: [], total: 0, pageNum: 1, pageSize: 10 }
}

export function remainingSlots(activity) {
  if (!activity) return 0
  const capacity = Number(activity.capacity || 0)
  const signed = Number(activity.signedCount || 0)
  return Math.max(capacity - signed, 0)
}

export function formatSize(bytes) {
  const size = Number(bytes || 0)
  if (size <= 0) return '—'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename || 'download'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
