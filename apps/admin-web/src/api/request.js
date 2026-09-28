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

request.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && body.code && body.code !== 'OK') {
      return Promise.reject(body)
    }
    return body?.data
  },
  (error) => Promise.reject(error?.response?.data || error)
)

export default request
