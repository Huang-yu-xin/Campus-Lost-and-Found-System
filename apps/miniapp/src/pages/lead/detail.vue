<template>
  <view class="page" v-if="lead">
    <view class="card">
      <view class="ptitle">线索 #{{ lead.id }}</view>
      <view class="status">状态：{{ leadStatusLabel(lead.status) }}</view>
      <view class="desc">{{ lead.body }}</view>
      <view class="evi" v-if="lead.evidenceFileIds && lead.evidenceFileIds.length">
        <text class="lb">证据图片：</text>
        <view class="imgs">
          <image class="thumb" v-for="(p, i) in eviImgs" :key="i" :src="p" mode="aspectFill" @click="preview(i)" />
        </view>
      </view>
    </view>

    <view v-if="owner" class="card">
      <view class="mtitle">处理线索（不代表判定归属）</view>
      <button class="btn" @click="review('VIEWED')">标为已查看</button>
      <button class="btn primary" @click="review('HELPFUL')">标为有帮助</button>
      <button class="btn danger" @click="review('CLOSED')">关闭线索</button>
    </view>
  </view>
  <!-- P1-F2：加载失败错误态 + 重试 -->
  <view class="page errstate" v-else-if="error">
    <text class="errmsg">加载失败，请稍后重试</text>
    <button class="btn primary" @click="load">重试</button>
  </view>
</template>

<script>
import { leadApi, loadPrivateImage } from '../../api/index'
import { leadStatusLabel } from '../../utils/labels'

export default {
  data() {
    return { leadId: null, owner: false, lead: null, eviImgs: [], error: false }
  },
  onLoad(query) {
    this.leadId = query.leadId
    this.owner = query.owner === '1'
    this.load()
  },
  methods: {
    leadStatusLabel,
    async load() {
      this.error = false
      try {
        this.lead = await leadApi.detail(this.leadId)
      } catch (e) {
        this.lead = null
        this.error = true
        if (e && (e.statusCode === 401 || e.code === 'UNAUTHENTICATED')) {
          uni.navigateTo({ url: '/pages/login/login' })
        }
        return
      }
      this.eviImgs = []
      for (const fid of (this.lead.evidenceFileIds || [])) {
        try { this.eviImgs.push(await loadPrivateImage(fid)) } catch (e) { /* skip */ }
      }
    },
    preview(i) { uni.previewImage({ current: i, urls: this.eviImgs }) },
    async review(status) {
      await leadApi.review(this.leadId, status)
      uni.showToast({ title: '已处理', icon: 'success' })
      this.load()
    }
  }
}
</script>

<style scoped>
.page { padding: 20rpx; }
.card { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; }
.ptitle { font-size: 30rpx; font-weight: 600; }
.status { color: #2b6cb0; font-size: 24rpx; margin: 12rpx 0; }
.desc { font-size: 28rpx; margin: 12rpx 0; }
.lb { font-size: 24rpx; color: #909399; }
.imgs { display: flex; flex-wrap: wrap; margin-top: 8rpx; }
.thumb { width: 150rpx; height: 150rpx; border-radius: 8rpx; margin: 6rpx; }
.mtitle { font-weight: 600; margin-bottom: 14rpx; }
.btn { margin-bottom: 12rpx; } .btn.primary { background: #2b6cb0; color: #fff; }
.btn.danger { background: #f56c6c; color: #fff; }
.errstate { padding-top: 160rpx; text-align: center; }
.errmsg { display: block; color: #909399; font-size: 28rpx; margin-bottom: 30rpx; }
</style>
