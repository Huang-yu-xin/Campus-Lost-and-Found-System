export function localDateTime(value) {
  if (!value) return '未填写'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '时间格式无效'
  const pad = n => String(n).padStart(2, '0')
  return [d.getFullYear(), pad(d.getMonth()+1), pad(d.getDate())].join('-') + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
}
export function localDateBoundary(date, end = false) {
  return new Date(date + (end ? 'T23:59:59' : 'T00:00:00')).toISOString()
}
