import { API_BASE } from './config'

const TOKEN_KEY = 'clf_token'

export function getToken() {
  try {
    return uni.getStorageSync(TOKEN_KEY) || ''
  } catch (e) {
    return ''
  }
}

export function setToken(token) {
  uni.setStorageSync(TOKEN_KEY, token)
}

export function clearToken() {
  uni.removeStorageSync(TOKEN_KEY)
}

/**
 * 统一请求。约定响应 { code, message, data, requestId }。
 * 成功返回 data；失败 reject 一个含 code/message 的对象。
 */
export function request(options) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: API_BASE + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'Content-Type': 'application/json',
        ...(getToken() ? { Authorization: 'Bearer ' + getToken() } : {})
      },
      success: (res) => {
        const body = res.data || {}
        const sc = res.statusCode
        if (sc === 401) {
          clearToken()
          uni.showToast({ title: '请先登录', icon: 'none' })
          reject({ code: 'UNAUTHENTICATED', statusCode: sc, message: '未登录' })
          return
        }
        // P1-F1：先校验 HTTP 状态码，非 2xx 一律 reject（携带 statusCode 与 body 摘要），
        // 不再因缺少 body.code 而静默 resolve(undefined)（如 5xx 网关返回非业务 JSON）
        if (sc < 200 || sc >= 300) {
          const msg = body.message || ('请求失败(' + sc + ')')
          uni.showToast({ title: msg, icon: 'none' })
          reject({ code: body.code || ('HTTP_' + sc), statusCode: sc, message: msg })
          return
        }
        if (body.code && body.code !== 'OK') {
          uni.showToast({ title: body.message || '操作失败', icon: 'none' })
          reject(body)
          return
        }
        resolve(body.data)
      },
      fail: (err) => {
        uni.showToast({ title: '网络错误', icon: 'none' })
        reject(err)
      }
    })
  })
}

export const http = {
  get: (url, data) => request({ url, method: 'GET', data }),
  post: (url, data) => request({ url, method: 'POST', data }),
  patch: (url, data) => request({ url, method: 'PATCH', data })
}
