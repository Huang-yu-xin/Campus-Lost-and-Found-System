import { http } from '../utils/request'
import { API_BASE } from '../utils/config'

export const authApi = {
  mockLogin: (testUser, nickname) => http.post('/auth/mock/login', { testUser, nickname }),
  wechatLogin: (code, nickname) => http.post('/auth/wechat/login', { code, nickname }),
  campusCapabilities: () => http.get('/auth/campus/capabilities')
}

export const userApi = {
  me: () => http.get('/users/me'),
  update: (data) => http.patch('/users/me', data),
  myPosts: (page = 1) => http.get('/users/me/posts', { page }),
  myClaims: (page = 1) => http.get('/users/me/claims', { page }),
  myLeads: (page = 1) => http.get('/users/me/leads', { page })
}

export const postApi = {
  list: (params) => http.get('/posts', params),
  search: (params) => http.get('/posts/search', params),
  detail: (id) => http.get('/posts/' + id),
  create: (data) => http.post('/posts', data),
  matches: (id) => http.get('/posts/' + id + '/matches'),
  withdraw: (id) => http.post('/posts/' + id + '/withdraw'),
  markFound: (id) => http.post('/posts/' + id + '/mark-found')
}

export const claimApi = {
  submit: (postId, data) => http.post('/posts/' + postId + '/claims', data),
  received: (postId) => http.get('/posts/' + postId + '/claims'),
  detail: (claimId) => http.get('/claims/' + claimId),
  review: (claimId, decision, reason) => http.post('/claims/' + claimId + '/review', { decision, reason }),
  confirm: (claimId) => http.post('/claims/' + claimId + '/confirmations'),
  withdraw: (claimId) => http.post('/claims/' + claimId + '/withdraw'),
  cancelHandover: (claimId, reason) => http.post('/claims/' + claimId + '/cancel-handover', { reason }),
  messages: (claimId) => http.get('/claims/' + claimId + '/messages'),
  sendMessage: (claimId, body) => http.post('/claims/' + claimId + '/messages', { body }),
  raiseDispute: (claimId, data) => http.post('/claims/' + claimId + '/disputes', data),
  disputes: (claimId) => http.get('/claims/' + claimId + '/disputes')
}

export const leadApi = {
  submit: (postId, data) => http.post('/posts/' + postId + '/leads', data),
  received: (postId) => http.get('/posts/' + postId + '/leads'),
  review: (leadId, status) => http.post('/leads/' + leadId + '/review', { status })
}

// 文件下载直链（图片用途为公开时可直接展示；私密文件需带鉴权，此处仅公开图片）
export const fileUrl = (fileId) => API_BASE + '/files/' + fileId
