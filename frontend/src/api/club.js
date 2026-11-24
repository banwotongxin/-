import http from './http'
import { cleanParams } from './http'

export function listClubs(params) {
  return http.get('/clubs', { params: cleanParams(params) })
}

export function applyClub(payload) {
  return http.post('/clubs', payload)
}

export function getClub(id) {
  return http.get(`/clubs/${id}`)
}

export function listManagedClubs() {
  return http.get('/clubs/managed')
}

export function updateClub(id, payload) {
  return http.put(`/clubs/${id}`, payload)
}

export function freezeClub(id, reason) {
  return http.post(`/clubs/${id}/freeze`, { reason })
}

export function unfreezeClub(id) {
  return http.post(`/clubs/${id}/unfreeze`)
}

export function listClubApplications(params) {
  return http.get('/club-applications', { params: cleanParams(params) })
}

export function listMyClubApplications(params) {
  return http.get('/club-applications/my', { params: cleanParams(params) })
}

export function reviewClubApplication(id, payload) {
  return http.put(`/club-applications/${id}/review`, payload)
}
