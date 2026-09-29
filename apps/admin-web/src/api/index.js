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
  assignDispute: (id) => request.post(`/admin/disputes/${id}/assign`),
  resolveDispute: (id, resolutionType, resolutionNote) =>
    request.post(`/admin/disputes/${id}/resolution`, { resolutionType, resolutionNote }),
  auditLogs: (params) => request.get('/admin/audit-logs', { params }),
  backups: (params) => request.get('/admin/maintenance/backups', { params }),
  runBackup: () => request.post('/admin/maintenance/backups')
}

/** 带鉴权拉取文件为可预览的 objectURL（私密证据，受理后才有权，无权 404）。 */
export async function fetchFileObjectUrl(fileId) {
  const token = localStorage.getItem('clf_admin_token')
  const res = await fetch(`/api/v1/files/${fileId}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!res.ok) {
    throw new Error(res.status === 404 ? '无权查看或文件不存在' : '加载失败')
  }
  const blob = await res.blob()
  return URL.createObjectURL(blob)
}
