import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { session, clearSession } from '../store/session'

const http = axios.create({
  baseURL: '/api',
  timeout: 20000
})

let redirecting = false

function buildError(message, code, traceId, status) {
  const err = new Error(message || '请求失败')
  err.code = code
  err.traceId = traceId
  err.status = status
  return err
}

function handleUnauthorized(message) {
  clearSession()
  if (redirecting) return
  redirecting = true
  ElMessage.warning(message || '登录状态已失效，请重新登录')
  const current = router.currentRoute.value
  const redirect = current.fullPath && current.path !== '/login' ? current.fullPath : ''
  router.replace({ path: '/login', query: redirect ? { redirect } : {} })
  setTimeout(() => {
    redirecting = false
  }, 800)
}

http.interceptors.request.use((config) => {
  if (session.accessToken) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${session.accessToken}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const config = response.config || {}
    const body = response.data
    const contentType = response.headers['content-type'] || ''
    const isJson = contentType.includes('application/json') ||
      (body && typeof body === 'object' && !(body instanceof Blob))

    if (isJson && body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return config.raw ? response : body.data
      }
      const err = buildError(body.message, body.code, body.traceId, response.status)
      if (body.code === 'AUTH_REQUIRED' && !config.skipAuthRedirect) {
        handleUnauthorized(body.message)
      }
      throw err
    }
    return config.raw ? response : body
  },
  (error) => {
    const response = error.response
    const status = response ? response.status : 0
    let body = response ? response.data : null
    if (body instanceof Blob) body = null
    const message = (body && body.message) || (status ? `请求失败（HTTP ${status}）` : '网络连接异常，请检查服务是否可用')
    const err = buildError(message, body && body.code, body && body.traceId, status)
    const skipRedirect = error.config && error.config.skipAuthRedirect
    if ((status === 401 || err.code === 'AUTH_REQUIRED') && !skipRedirect) {
      handleUnauthorized(message)
    }
    return Promise.reject(err)
  }
)

/**
 * 去掉空查询参数，保留 false / 0 等有效值。
 */
export function cleanParams(params) {
  const result = {}
  Object.keys(params || {}).forEach((key) => {
    const value = params[key]
    if (value === null || value === undefined || value === '') return
    result[key] = value
  })
  return result
}

export default http
