import http from './http'
import { cleanParams } from './http'

export function listActivities(params) {
  return http.get('/activities', { params: cleanParams(params) })
}

export function createActivity(payload) {
  return http.post('/activities', payload)
}

export function getActivity(id) {
  return http.get(`/activities/${id}`)
}

export function updateActivity(id, payload) {
  return http.put(`/activities/${id}`, payload)
}

export function publishActivity(id) {
  return http.post(`/activities/${id}/publish`)
}

export function closeActivity(id, reason) {
  return http.post(`/activities/${id}/close`, { reason })
}

export function takedownActivity(id, reason) {
  return http.post(`/activities/${id}/takedown`, { reason })
}

export function restoreActivity(id) {
  return http.post(`/activities/${id}/restore`)
}

export function deleteActivity(id) {
  return http.delete(`/activities/${id}`)
}
