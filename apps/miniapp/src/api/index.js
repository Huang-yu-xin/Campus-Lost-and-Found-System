import { http, getToken } from '../utils/request'
import { API_BASE } from '../utils/config'

export const authApi = {
  mockLogin: (testUser, nickname) => http.post('/auth/mock/login', { testUser, nickname }),
  wechatLogin: (code, nickname) => http.post('/auth/wechat/login', { code, nickname }),
  campusCapabilities: () => http.get('/auth/campus/capabilities'),
  logout: () => http.post('/auth/logout')
  // E31：authApi.refresh 死代码已删除（前端无续期入口，由重新登录处理）
}

export const userApi = {
  me: () => http.get('/users/me'),
  update: (data) => http.patch('/users/me', data),
  myPosts: (page = 1) => http.get('/users/me/posts', { page }),
  myClaims: (page = 1) => http.get('/users/me/claims', { page }),
  myLeads: (page = 1) => http.get('/users/me/leads', { page }),
  receivedClaims: (page = 1) => http.get('/users/me/received-claims', { page }),
  receivedLeads: (page = 1) => http.get('/users/me/received-leads', { page })
}

export const postApi = {
  list: (params) => http.get('/posts', params),
  search: (params) => http.get('/posts/search', params),
  detail: (id) => http.get('/posts/' + id),
  create: (data) => http.post('/posts', data),
  update: (id, data) => http.patch('/posts/' + id, data),
  matches: (id) => http.get('/posts/' + id + '/matches'),
  withdraw: (id) => http.post('/posts/' + id + '/withdraw'),
  markFound: (id) => http.post('/posts/' + id + '/mark-found')
}

export const claimApi = {
  submit: (postId, data) => http.post('/posts/' + postId + '/claims', data),
  detail: (claimId) => http.get('/claims/' + claimId),
  review: (claimId, decision, reason) => http.post('/claims/' + claimId + '/review', { decision, reason }),
  confirm: (claimId) => http.post('/claims/' + claimId + '/confirmations'),
  withdraw: (claimId) => http.post('/claims/' + claimId + '/withdraw'),
  cancelHandover: (claimId, reason) => http.post('/claims/' + claimId + '/cancel-handover', { reason }),
  messages: (claimId) => http.get('/claims/' + claimId + '/messages'),
  sendMessage: (claimId, body) => http.post('/claims/' + claimId + '/messages', { body }),
  raiseDispute: (claimId, data) => http.post('/claims/' + claimId + '/disputes', data),
  disputes: (claimId) => http.get('/claims/' + claimId + '/disputes'),
  // V4 闭环：认领完成后关联失主自己的寻物帖
  resolvedCandidates: (claimId) => http.get('/claims/' + claimId + '/resolved-candidates'),
  resolveLost: (claimId, lostPostId) => http.post('/claims/' + claimId + '/resolve-lost', { lostPostId })
}

export const leadApi = {
  submit: (postId, data) => http.post('/posts/' + postId + '/leads', data),
  detail: (leadId) => http.get('/leads/' + leadId),
  review: (leadId, status) => http.post('/leads/' + leadId + '/review', { status })
}

// 公开图片直链（PUBLIC_POST 无需登录即可访问）。
export const fileUrl = (fileId) => API_BASE + '/files/' + fileId

/**
 * 上传文件。purpose: PUBLIC_POST / PRIVATE_CLAIM / PRIVATE_LEAD / PRIVATE_DISPUTE。
 * 返回 fileId。
 */
export function uploadFile(filePath, purpose) {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: API_BASE + '/files',
      filePath,
      name: 'file',
      formData: { purpose },
      header: getToken() ? { Authorization: 'Bearer ' + getToken() } : {},
      success: (res) => {
        // E26：先校验 HTTP 状态码 2xx，再解析响应体
        if (res.statusCode < 200 || res.statusCode >= 300) {
          const msg = res.statusCode === 401 ? '请先登录' : '上传失败(' + res.statusCode + ')'
          uni.showToast({ title: msg, icon: 'none' })
          reject({ code: 'HTTP_' + res.statusCode, statusCode: res.statusCode, message: msg })
          return
        }
        let body
        try {
          body = JSON.parse(res.data)
        } catch (e) {
          reject({ message: '上传响应解析失败' })
          return
        }
        if (body.code !== 'OK') {
          uni.showToast({ title: body.message || '上传失败', icon: 'none' })
          reject(body)
          return
        }
        resolve(body.data.fileId)
      },
      fail: (err) => {
        uni.showToast({ title: '上传失败', icon: 'none' })
        reject(err)
      }
    })
  })
}

/**
 * 加载私密图片（认领/线索/争议证据）：带 Bearer 请求字节流，写入临时文件后返回可用于 <image> 的路径。
 * 无权时后端返回 404，reject。
 */
export function loadPrivateImage(fileId) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: API_BASE + '/files/' + fileId,
      method: 'GET',
      responseType: 'arraybuffer',
      header: getToken() ? { Authorization: 'Bearer ' + getToken() } : {},
      success: (res) => {
        if (res.statusCode !== 200) {
          reject(res)
          return
        }
        const fs = uni.getFileSystemManager()
        const path = `${uni.env.USER_DATA_PATH}/priv_${fileId}_${Date.now()}.img`
        fs.writeFile({
          filePath: path,
          data: res.data,
          success: () => resolve(path),
          fail: reject
        })
      },
      fail: reject
    })
  })
}
