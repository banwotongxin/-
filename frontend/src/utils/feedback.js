import { ElMessage } from 'element-plus'

/**
 * 统一错误提示：401 由请求层处理；403 提示无权；409 提示冲突并刷新；500 展示 traceId。
 */
export function notifyError(err, reload) {
  if (!err) return
  if (err.status === 401 || err.code === 'AUTH_REQUIRED') return
  if (err.status === 403 || err.code === 'AUTH_FORBIDDEN' || err.code === 'USER_DISABLED') {
    ElMessage.warning(err.message || '无权执行该操作')
    return
  }
  if (err.status === 409) {
    ElMessage.warning(err.message ? `操作冲突：${err.message}` : '状态已被他人变更，请刷新后重试')
    if (typeof reload === 'function') reload()
    return
  }
  if (err.status >= 500 || err.code === 'SYSTEM_ERROR' || err.code === 'FILE_STORE_FAILED') {
    const trace = err.traceId ? `（traceId：${err.traceId}）` : ''
    ElMessage.error({ message: `${err.message || '系统异常，请稍后重试'}${trace}`, duration: 6000 })
    return
  }
  ElMessage.error(err.message || '操作失败')
}
