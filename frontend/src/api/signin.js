import http from './http'
import { cleanParams } from './http'

export function registerSignin(activityId, payload) {
  return http.post(`/activities/${activityId}/signins`, payload)
}

export function selfSignin(signupId) {
  return http.post(`/signups/${signupId}/signin`)
}

export function listSignins(activityId, params) {
  return http.get(`/activities/${activityId}/signins`, { params: cleanParams(params) })
}

export function getSigninSummary(activityId) {
  return http.get(`/activities/${activityId}/signins/summary`)
}

export function exportSignins(activityId) {
  return http.get(`/activities/${activityId}/signins/export`, {
    responseType: 'blob',
    raw: true
  })
}

export function applySignin(activityId, payload) {
  return http.post(`/activities/${activityId}/signin-applications`, payload)
}

export function listMySigninApplications(params) {
  return http.get('/signin-applications/my', { params: cleanParams(params) })
}

export function listSigninApplications(activityId, params) {
  return http.get(`/activities/${activityId}/signin-applications`, { params: cleanParams(params) })
}

export function reviewSigninApplication(id, payload) {
  return http.put(`/signin-applications/${id}/review`, payload)
}
