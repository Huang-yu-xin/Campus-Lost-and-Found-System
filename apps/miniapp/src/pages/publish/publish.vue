<template>
  <view class="page">
    <view class="tabs" v-if="!editId">
      <text :class="['tab', form.type === 'LOST' ? 'on' : '']" @click="form.type = 'LOST'">寻物</text>
      <text :class="['tab', form.type === 'FOUND' ? 'on' : '']" @click="form.type = 'FOUND'">招领</text>
    </view>

    <view class="field"><text class="lb">标题</text><input class="in" v-model="form.title" maxlength="128" placeholder="简要标题" /></view>
    <view class="field"><text class="lb">类别</text><picker class="in" mode="selector" :range="categoryOptions" @change="onPickCategory"><view class="in">{{ form.category || '请选择类别' }}</view></picker></view>
    <view class="field"><text class="lb">描述</text><textarea class="ta" v-model="form.publicDescription" maxlength="2000" placeholder="公开描述（招领请勿公开唯一性证明细节）" /></view>
    <view class="field"><text class="lb">校区</text><input class="in" v-model="form.campus" maxlength="64" placeholder="可选" /></view>
    <view class="field"><text class="lb">{{ form.type === 'LOST' ? '丢失地点' : '拾取地点' }}</text><input class="in" v-model="form.eventLocation" maxlength="128" placeholder="地点" /></view>
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
import { pickCheckedImages } from '../../utils/image'

// 类别字典规范子类（镜像 server/src/main/resources/matching/category-dictionary.v1.txt 的展示名）
const CATEGORIES = ['雨伞', '校园卡', '钥匙', '耳机', '水杯', '手机', '钱包', '充电宝', '书本教材', '证件', '手表饰品', '衣物', '笔记本电脑', '眼镜', '其他']
export default {
  data() {
    return {
      editId: null,
      dateStr: '',
      submitting: false,
      images: [], // { url, fileId?, localPath? }
      categoryOptions: CATEGORIES,
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
    onPickCategory(e) { this.form.category = this.categoryOptions[Number(e.detail.value)] },
    async loadForEdit(id) {
      let d
      try {
        d = await postApi.detail(id)
      } catch (e) {
        // E23：加载失败 → 提示并返回上一页
        uni.showToast({ title: '加载失败', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 800)
        return
      }
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
    async chooseImage() {
      // E20：复用公共选图预检
      const paths = await pickCheckedImages(6 - this.images.length)
      for (const p of paths) {
        if (this.images.length >= 6) break
        this.images.push({ url: p, localPath: p })
      }
    },
    removeImg(i) {
      this.images.splice(i, 1)
    },
    preview(i) {
      const urls = this.images.map((x) => x.url)
      uni.previewImage({ current: urls[i], urls }) // E13：current 传字符串
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
          // E22：编辑来源 → 返回上一页（详情页 onShow 会刷新）
          setTimeout(() => uni.navigateBack(), 600)
        } else {
          await postApi.create(payload)
          uni.showToast({ title: '发布成功', icon: 'success' })
          setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 600)
        }
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
