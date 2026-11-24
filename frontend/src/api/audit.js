import http from './http'
import { cleanParams } from './http'

export function listAuditLogs(params) {
  return http.get('/audit-logs', { params: cleanParams(params) })
}
