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

    <!-- E18：可见性由后端 lead.owner 控制；E17：按当前 status 收敛可用操作，CLOSED 隐藏全部 -->
    <view v-if="lead.owner && lead.status !== 'CLOSED'" class="card">
      <view class="mtitle">处理线索（不代表判定归属）</view>
      <button v-if="lead.status === 'SUBMITTED'" class="btn" :loading="acting" :disabled="acting" @click="review('VIEWED')">标为已查看</button>
      <button v-if="lead.status === 'SUBMITTED' || lead.status === 'VIEWED'" class="btn primary" :loading="acting" :disabled="acting" @click="review('HELPFUL')">标为有帮助</button>
      <button class="btn danger" :loading="acting" :disabled="acting" @click="closeLead">关闭线索</button>
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
    return { leadId: null, lead: null, eviImgs: [], error: false, acting: false }
  },
  onLoad(query) {
    this.leadId = query.leadId
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
    preview(i) { uni.previewImage({ current: this.eviImgs[i], urls: this.eviImgs }) }, // E13
    async closeLead() {
      // E17：关闭线索加确认弹窗
      const r = await new Promise((res) => uni.showModal({ title: '关闭线索', content: '关闭后将不可再处理该线索。', success: res }))
      if (!r.confirm) return
      this.review('CLOSED')
    },
    async review(status) {
      if (this.acting) return
      this.acting = true
      try {
        await leadApi.review(this.leadId, status)
        uni.showToast({ title: '已处理', icon: 'success' })
        this.load()
      } catch (e) { /* toasted */ } finally { this.acting = false }
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
.thumb { width: 150rpx; height: 150rpx; border-radius: 8rpx; margin: 6rpx; background: #f0f0f0; }
.mtitle { font-weight: 600; margin-bottom: 14rpx; }
.btn { margin-bottom: 12rpx; } .btn.primary { background: #2b6cb0; color: #fff; }
.btn.danger { background: #f56c6c; color: #fff; }
.errstate { padding-top: 160rpx; text-align: center; }
.errmsg { display: block; color: #909399; font-size: 28rpx; margin-bottom: 30rpx; }
</style>
