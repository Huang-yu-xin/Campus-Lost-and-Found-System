// 状态中文映射（A5）。发布 COMPLETED 依类型区分"已找回/已归还"。

export function postStatusLabel(status, type) {
  if (status === 'COMPLETED') return type === 'LOST' ? '已找回' : '已归还'
  const map = { ACTIVE: '进行中', HANDOVER: '交接中', WITHDRAWN: '已撤回', REMOVED: '已下架' }
  return map[status] || status
}

export function claimStatusLabel(status) {
  const map = {
    PENDING: '待审核', WAITING_HANDOVER: '待交接', COMPLETED: '已完成',
    REJECTED: '已拒绝', WITHDRAWN: '已撤销', CLOSED: '已关闭'
  }
  return map[status] || status
}

export function leadStatusLabel(status) {
  const map = { SUBMITTED: '待查看', VIEWED: '已查看', HELPFUL: '有帮助', CLOSED: '已关闭' }
  return map[status] || status
}

export function typeLabel(type) {
  return type === 'LOST' ? '寻物' : '招领'
}
