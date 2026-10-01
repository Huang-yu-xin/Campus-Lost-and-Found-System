const labels = {
 ACTIVE:'进行中', HANDOVER:'交接中', COMPLETED:'已完成', WITHDRAWN:'已撤回', REMOVED:'已下架',
 LOST:'寻物', FOUND:'招领', RESTRICTED:'已限制', DISABLED:'已停用', UNVERIFIED:'未认证', VERIFIED:'已认证',
 OPEN:'待处理', RESOLVED:'已裁决', CLOSED:'已关闭', RUNNING:'执行中', FAILED:'失败', SUCCESS:'成功',
 USER:'用户', ADMIN:'管理员', POST:'发布', CLAIM:'认领申请', LEAD:'线索', DISPUTE:'争议', BACKUP:'备份',
 CLAIM_REVIEW:'申请审核', DISPUTE_RAISE:'发起争议', DISPUTE_ASSIGN:'受理争议', DISPUTE_RESOLVE:'争议裁决',
 POST_REMOVE:'下架发布', POST_RESTORE:'恢复发布', USER_RESTRICT:'限制用户', USER_UNRESTRICT:'解除限制',
 BACKUP_RUN:'执行备份', LOST_RESOLVED:'关联找回', HANDOVER_CANCELLED:'取消交接', LOST_MARK_FOUND:'标记找回',
 LEAD_REVIEW:'处理线索', REMOVE:'下架', RESTORE:'恢复'
}
export const label = value => labels[value] || value || '—'
export const statusFormatter = (row, column, value) => value === 'COMPLETED' && row.type
  ? (row.type === 'LOST' ? '已找回' : '已归还') : label(value)
export function currentAdminId() {
  try {
    const payload = localStorage.getItem('clf_admin_token').split('.')[1].replace(/-/g,'+').replace(/_/g,'/')
    return Number(JSON.parse(atob(payload)).sub)
  } catch { return null }
}
