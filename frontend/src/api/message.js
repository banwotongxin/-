import http from './http'
import { cleanParams } from './http'

export function listMessages(params) {
  return http.get('/messages', { params: cleanParams(params) })
}

export function markMessageRead(id) {
  return http.put(`/messages/${id}/read`)
}

export function markAllMessagesRead() {
  return http.put('/messages/read-all')
}

export function fetchUnreadCount() {
  return http.get('/messages/unread-count')
}
