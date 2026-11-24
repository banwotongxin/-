import http from './http'
import { cleanParams } from './http'

export function getStatisticsOverview(params) {
  return http.get('/statistics/overview', { params: cleanParams(params) })
}

export function getStatisticsTrend(params) {
  return http.get('/statistics/trend', { params: cleanParams(params) })
}

export function exportStatistics(params) {
  return http.get('/statistics/export', {
    params: cleanParams(params),
    responseType: 'blob',
    raw: true
  })
}
