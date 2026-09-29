import axios from 'axios'

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

function handleUnauthorized() {
  localStorage.removeItem('clf_admin_token')
  if (!location.pathname.includes('/login')) {
    location.href = '/login'
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
    if (status === 401 || body?.code === 'UNAUTHENTICATED') {
      handleUnauthorized()
    }
    return Promise.reject(body || error)
  }
)

export default request
