import http from './http'
import { cleanParams } from './http'

export function submitSignup(activityId, remark) {
  const payload = {}
  if (remark) payload.remark = remark
  return http.post(`/activities/${activityId}/signups`, payload)
}

export function listActivitySignups(activityId, params) {
  return http.get(`/activities/${activityId}/signups`, { params: cleanParams(params) })
}

export function getActivitySignupSummary(activityId) {
  return http.get(`/activities/${activityId}/signups/summary`)
}

export function getMySignup(activityId) {
  return http.get(`/activities/${activityId}/signups/my`)
}

export function listMySignups(params) {
  return http.get('/signups/my', { params: cleanParams(params) })
}

export function reviewSignup(id, payload) {
  return http.put(`/signups/${id}/review`, payload)
}

export function cancelSignup(id) {
  return http.post(`/signups/${id}/cancel`)
}
