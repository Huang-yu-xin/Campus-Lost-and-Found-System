import axios from 'axios'
import { ElMessage } from 'element-plus'

// 统一请求封装。响应约定 { code, message, data, requestId }。
const request = axios.create({
  baseURL: '/api/v1',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('clf_admin_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// E4：去重标志——并发多请求同时 401 时只弹一次提示、只跳一次登录
let unauthorizedHandled = false
let handledToken = null
function handleUnauthorized() {
  const token = localStorage.getItem('clf_admin_token')
  if (unauthorizedHandled && (!token || token === handledToken)) return
  handledToken = token
  unauthorizedHandled = true
  localStorage.removeItem('clf_admin_token')
  if (!location.pathname.includes('/login')) {
    ElMessage.warning('登录已失效，请重新登录')
    setTimeout(() => { location.href = '/login' }, 800)
  }
}

request.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && body.code === 'UNAUTHENTICATED') {
      handleUnauthorized()
      return Promise.reject(body)
    }
    if (body && body.code && body.code !== 'OK') {
      return Promise.reject(body)
    }
    return body?.data
  },
  (error) => {
    const status = error?.response?.status
    const body = error?.response?.data
    if ((status === 401 && body?.code !== 'ADMIN_LOGIN_FAILED') || body?.code === 'UNAUTHENTICATED') {
      handleUnauthorized()
    }
    return Promise.reject(body || error)
  }
)

export default request
