<template>
  <view class="page">
    <view class="tabs">
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

    <view v-if="form.type === 'FOUND'" class="tip">提示：请勿在公开描述里写出仅失主才知道的唯一性特征。</view>
    <button class="btn primary" @click="submit">发布</button>
  </view>
</template>

<script>
import { postApi } from '../../api/index'

export default {
  data() {
    return {
      dateStr: '',
      form: { type: 'FOUND', title: '', category: '', publicDescription: '', campus: '', eventLocation: '', eventTime: null }
    }
  },
  methods: {
    onDate(e) {
      this.dateStr = e.detail.value
      this.form.eventTime = e.detail.value + 'T00:00:00'
    },
    async submit() {
      if (!this.form.title || !this.form.category || !this.form.publicDescription) {
        uni.showToast({ title: '请填写标题/类别/描述', icon: 'none' })
        return
      }
      try {
        await postApi.create({ ...this.form, imageFileIds: [] })
        uni.showToast({ title: '发布成功', icon: 'success' })
        setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 500)
      } catch (e) { /* 已提示 */ }
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
.tip { color: #e6a23c; font-size: 22rpx; margin: 16rpx 0; }
.btn.primary { background: #2b6cb0; color: #fff; margin-top: 20rpx; }
</style>
