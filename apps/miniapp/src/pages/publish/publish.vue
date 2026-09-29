<template>
  <view class="page">
    <view class="tabs" v-if="!editId">
      <text :class="['tab', form.type === 'LOST' ? 'on' : '']" @click="form.type = 'LOST'">寻物</text>
      <text :class="['tab', form.type === 'FOUND' ? 'on' : '']" @click="form.type = 'FOUND'">招领</text>
    </view>

    <view class="field"><text class="lb">标题</text><input class="in" v-model="form.title" placeholder="简要标题" /></view>
    <view class="field"><text class="lb">类别</text><input class="in" v-model="form.category" placeholder="如 钱包/钥匙/证件" /></view>
    <view class="field"><text class="lb">描述</text><textarea class="ta" v-model="form.publicDescription" placeholder="公开描述（招领请勿公开唯一性证明细节）" /></view>
    <view class="field"><text class="lb">校区</text><input class="in" v-model="form.campus" placeholder="可选" /></view>
    <view class="field"><text class="lb">{{ form.type === 'LOST' ? '丢失地点' : '拾取地点' }}</text><input class="in" v-model="form.eventLocation" placeholder="地点" /></view>
    <view class="field"><text class="lb">{{ form.type === 'LOST' ? '丢失时间' : '拾取时间' }}</text>
      <picker mode="date" :value="dateStr" @change="onDate"><view class="in">{{ dateStr || '选择日期' }}</view></picker>
    </view>

    <view class="field">
      <text class="lb">公开图片（最多6张，jpg/png/webp，单张≤5MB）</text>
      <view class="imgs">
        <view class="imgbox" v-for="(img, i) in images" :key="i">
          <image class="img" :src="img.url" mode="aspectFill" @click="preview(i)" />
          <text class="del" @click="removeImg(i)">×</text>
        </view>
        <view class="imgadd" v-if="images.length < 6" @click="chooseImage">＋</view>
      </view>
    </view>

    <view v-if="editId" class="tip">提示：存在有效申请后，类别/时间/地点/核心描述/图片不可修改。</view>
    <view v-else-if="form.type === 'FOUND'" class="tip">提示：请勿在公开描述里写出仅失主才知道的唯一性特征。</view>
    <button class="btn primary" :loading="submitting" @click="submit">{{ editId ? '保存修改' : '发布' }}</button>
  </view>
</template>

<script>
import { postApi, uploadFile, fileUrl } from '../../api/index'

export default {
  data() {
    return {
      editId: null,
      dateStr: '',
      submitting: false,
      images: [], // { url, fileId?, localPath? }
      form: { type: 'FOUND', title: '', category: '', publicDescription: '', campus: '', eventLocation: '', eventTime: null }
    }
  },
  onLoad(query) {
    if (query && query.id) {
      this.editId = query.id
      this.loadForEdit(query.id)
    }
  },
  methods: {
    async loadForEdit(id) {
      const d = await postApi.detail(id)
      if (!d.mine) {
        uni.showToast({ title: '只能编辑自己的发布', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 800)
        return
      }
      this.form = {
        type: d.type, title: d.title, category: d.category, publicDescription: d.publicDescription,
        campus: d.campus || '', eventLocation: d.eventLocation || '', eventTime: d.eventTime || null
      }
      this.dateStr = d.eventTime ? d.eventTime.slice(0, 10) : ''
      this.images = (d.imageFileIds || []).map((fid) => ({ url: fileUrl(fid), fileId: fid }))
    },
    onDate(e) {
      this.dateStr = e.detail.value
      this.form.eventTime = e.detail.value + 'T00:00:00'
    },
    chooseImage() {
      uni.chooseImage({
        count: 6 - this.images.length,
        success: async (r) => {
          const files = (r.tempFiles && r.tempFiles.length)
            ? r.tempFiles
            : (r.tempFilePaths || []).map((p) => ({ path: p, size: 0 }))
          for (const f of files) {
            if (this.images.length >= 6) break
            const path = f.path || f
            if (f.size && f.size > 5 * 1024 * 1024) {
              uni.showToast({ title: '单张图片不能超过 5MB', icon: 'none' })
              continue
            }
            const ok = await this.checkImageType(path)
            if (!ok) {
              uni.showToast({ title: '仅支持 jpg/png/webp', icon: 'none' })
              continue
            }
            this.images.push({ url: path, localPath: path })
          }
        }
      })
    },
    checkImageType(path) {
      // 客户端预校验；后端仍做 magic-byte 权威校验
      return new Promise((resolve) => {
        uni.getImageInfo({
          src: path,
          success: (info) => {
            const t = (info.type || '').toLowerCase()
            resolve(!t || ['jpg', 'jpeg', 'png', 'webp'].includes(t))
          },
          fail: () => resolve(true)
        })
      })
    },
    removeImg(i) {
      this.images.splice(i, 1)
    },
    preview(i) {
      uni.previewImage({ current: i, urls: this.images.map((x) => x.url) })
    },
    async submit() {
      if (!this.form.title || !this.form.category || !this.form.publicDescription) {
        uni.showToast({ title: '请填写标题/类别/描述', icon: 'none' })
        return
      }
      this.submitting = true
      try {
        // 上传新增本地图片，得到 fileId
        const imageFileIds = []
        for (const img of this.images) {
          if (img.fileId) {
            imageFileIds.push(img.fileId)
          } else if (img.localPath) {
            const fid = await uploadFile(img.localPath, 'PUBLIC_POST')
            imageFileIds.push(fid)
          }
        }
        const payload = { ...this.form, imageFileIds }
        if (this.editId) {
          await postApi.update(this.editId, payload)
          uni.showToast({ title: '已保存', icon: 'success' })
        } else {
          await postApi.create(payload)
          uni.showToast({ title: '发布成功', icon: 'success' })
        }
        setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 600)
      } catch (e) {
        // 错误 message 已由 request 层 toast（如 POST_EDIT_LOCKED）
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.page { padding: 24rpx; }
.tabs { display: flex; margin-bottom: 24rpx; }
.tab { flex: 1; text-align: center; padding: 20rpx; background: #fff; color: #606266; }
.tab.on { background: #2b6cb0; color: #fff; }
.field { background: #fff; padding: 20rpx; margin-bottom: 14rpx; border-radius: 10rpx; }
.lb { font-size: 24rpx; color: #909399; }
.in { border: 1rpx solid #dcdfe6; border-radius: 8rpx; padding: 16rpx; margin-top: 10rpx; }
.ta { border: 1rpx solid #dcdfe6; border-radius: 8rpx; padding: 16rpx; margin-top: 10rpx; height: 160rpx; width: 100%; box-sizing: border-box; }
.imgs { display: flex; flex-wrap: wrap; margin-top: 12rpx; }
.imgbox { position: relative; width: 160rpx; height: 160rpx; margin: 8rpx; }
.img { width: 160rpx; height: 160rpx; border-radius: 8rpx; }
.del { position: absolute; top: -10rpx; right: -10rpx; background: #f56c6c; color: #fff; width: 36rpx; height: 36rpx; border-radius: 50%; text-align: center; line-height: 36rpx; font-size: 28rpx; }
.imgadd { width: 160rpx; height: 160rpx; margin: 8rpx; border: 1rpx dashed #c0c4cc; border-radius: 8rpx; text-align: center; line-height: 160rpx; font-size: 60rpx; color: #c0c4cc; }
.tip { color: #e6a23c; font-size: 22rpx; margin: 16rpx 0; }
.btn.primary { background: #2b6cb0; color: #fff; margin-top: 20rpx; }
</style>
