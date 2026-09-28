import request from './request'

export const authApi = {
  login: (username, password) => request.post('/admin/auth/login', { username, password })
}

export const adminApi = {
  listPosts: (params) => request.get('/admin/posts', { params }),
  removePost: (id, reason) => request.post(`/admin/posts/${id}/remove`, { reason }),
  restorePost: (id, reason) => request.post(`/admin/posts/${id}/restore`, { reason }),
  listUsers: (params) => request.get('/admin/users', { params }),
  restrictUser: (id, reason) => request.post(`/admin/users/${id}/restrictions`, { reason }),
  unrestrictUser: (id, reason) => request.post(`/admin/users/${id}/unrestrict`, { reason }),
  listDisputes: (params) => request.get('/admin/disputes', { params }),
  getDispute: (id) => request.get(`/admin/disputes/${id}`),
  resolveDispute: (id, resolutionType, resolutionNote) =>
    request.post(`/admin/disputes/${id}/resolution`, { resolutionType, resolutionNote }),
  auditLogs: (params) => request.get('/admin/audit-logs', { params }),
  backups: (params) => request.get('/admin/maintenance/backups', { params }),
  runBackup: () => request.post('/admin/maintenance/backups')
}
