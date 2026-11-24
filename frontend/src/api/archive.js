import http from './http'
import { cleanParams } from './http'

export function uploadArchive(activityId, formData, onUploadProgress) {
  return http.post(`/activities/${activityId}/archives`, formData,
    onUploadProgress ? { onUploadProgress } : undefined)
}

export function listArchives(activityId, params) {
  return http.get(`/activities/${activityId}/archives`, { params: cleanParams(params) })
}

export function deleteArchive(activityId, archiveId) {
  return http.delete(`/activities/${activityId}/archives/${archiveId}`)
}

export function downloadFile(fileId) {
  return http.get(`/files/${fileId}`, {
    responseType: 'blob',
    raw: true
  })
}
