// 选图公共预检（E20）：单张 ≤5MB + 类型 jpg/png/webp。
// publish / detail(认领·线索) / claim(争议) 选图统一复用，后端仍做 magic-byte 权威校验。
const MAX_SIZE = 5 * 1024 * 1024
const ALLOWED = ['jpg', 'jpeg', 'png', 'webp']

function checkImageType(path) {
  return new Promise((resolve) => {
    uni.getImageInfo({
      src: path,
      success: (info) => {
        const t = (info.type || '').toLowerCase()
        resolve(!t || ALLOWED.includes(t))
      },
      fail: () => resolve(true)
    })
  })
}

/** 选图并做 5MB + 类型预检；返回通过校验的本地临时路径数组（count: 最多可选张数）。 */
export function pickCheckedImages(count) {
  return new Promise((resolve) => {
    uni.chooseImage({
      count,
      success: async (r) => {
        const files = (r.tempFiles && r.tempFiles.length)
          ? r.tempFiles
          : (r.tempFilePaths || []).map((p) => ({ path: p, size: 0 }))
        const paths = []
        for (const f of files) {
          const path = f.path || f
          if (f.size && f.size > MAX_SIZE) {
            uni.showToast({ title: '单张图片不能超过 5MB', icon: 'none' })
            continue
          }
          const ok = await checkImageType(path)
          if (!ok) {
            uni.showToast({ title: '仅支持 jpg/png/webp', icon: 'none' })
            continue
          }
          paths.push(path)
        }
        resolve(paths)
      },
      fail: () => resolve([])
    })
  })
}
