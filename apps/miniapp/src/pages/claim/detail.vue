<template>
  <view class="page" v-if="claim">
    <view class="card">
      <view class="ptitle">认领申请 #{{ claim.id }}</view>
      <view class="status">状态：{{ claimStatusLabel(claim.status) }}</view>
      <view class="desc">证明说明：{{ claim.description }}</view>

      <view class="evi" v-if="claim.evidenceFileIds && claim.evidenceFileIds.length">
        <text class="lb">证据图片：</text>
        <view class="imgs">
          <image class="thumb" v-for="(p, i) in eviImgs" :key="i" :src="p" mode="aspectFill" @click="previewEvi(i)" />
        </view>
      </view>

      <view class="tl">
        <text class="lb">进度</text>
        <view class="tli">提交：{{ fmt(claim.createdAt) || '—' }}</view>
        <view class="tli">审核：{{ fmt(claim.reviewedAt) || '待处理' }}</view>
        <view class="tli">接受：{{ fmt(claim.acceptedAt) || '—' }}</view>
        <view class="tli">申请人确认：{{ claim.applicantConfirmed ? '已确认' : '未确认' }} · 发布者确认：{{ claim.publisherConfirmed ? '已确认' : '未确认' }}</view>
        <view class="tli">完成：{{ fmt(claim.completedAt) || '—' }}</view>
      </view>
    </view>

    <!-- 发布者审核 -->
    <view v-if="claim.publisher && claim.status === 'PENDING'" class="card">
      <view class="mtitle">审核申请</view>
      <button class="btn primary" @click="review('ACCEPT')">接受</button>
      <button class="btn danger" @click="review('REJECT')">拒绝</button>
    </view>

    <!-- 申请人撤销 -->
    <view v-if="claim.applicant && claim.status === 'PENDING'" class="card">
      <button class="btn danger" @click="withdraw">撤销申请</button>
    </view>

    <!-- 交接确认 / 争议 -->
    <view v-if="claim.status === 'WAITING_HANDOVER'" class="card">
      <view class="mtitle">交接</view>
      <view v-if="hasOpenDispute" class="paused">存在未决争议，交接已暂停，请等待管理员裁决</view>
      <button class="btn primary" :disabled="hasOpenDispute" @click="confirm">我已完成交接</button>
      <button class="btn" :disabled="hasOpenDispute" @click="raiseDispute">发起争议</button>
      <button class="btn danger" v-if="!hasOpenDispute" @click="cancelHandover">取消本次交接</button>
    </view>

    <!-- 争议进度 -->
    <view v-if="disputes.length" class="card">
      <view class="mtitle">争议进度</view>
      <view class="dsp" v-for="d in disputes" :key="d.id">
        <view>#{{ d.id }} · {{ disputeStatusText(d.status) }}</view>
        <view class="meta">原因：{{ d.reason }}</view>
        <view class="meta" v-if="d.resolutionType">裁决：{{ resolutionText(d.resolutionType) }}（{{ d.resolutionNote }}）</view>
      </view>
    </view>

    <!-- 留言 -->
    <view class="card">
      <view class="mtitle">留言</view>
      <view class="msg" v-for="m in messages" :key="m.id" :class="{ mine: m.mine }">
        <text>{{ m.body }}</text>
      </view>
      <view v-if="messages.length === 0" class="meta">暂无留言</view>
      <view class="msgbar">
        <input class="in" v-model="msgText" placeholder="输入留言" />
        <button class="btn small" @click="send">发送</button>
      </view>
    </view>
  </view>
</template>

<script>
import { claimApi, loadPrivateImage } from '../../api/index'
import { claimStatusLabel } from '../../utils/labels'

export default {
  data() {
    return { claimId: null, claim: null, messages: [], disputes: [], eviImgs: [], msgText: '' }
  },
  computed: {
    hasOpenDispute() { return this.disputes.some((d) => d.status === 'OPEN') }
  },
  onLoad(query) {
    this.claimId = query.claimId
    this.reloadAll()
  },
  methods: {
    claimStatusLabel,
    async reloadAll() {
      await this.load()
      this.loadMessages()
      this.loadDisputes()
      this.loadEvidence()
    },
    async load() { this.claim = await claimApi.detail(this.claimId) },
    async loadMessages() { try { this.messages = await claimApi.messages(this.claimId) } catch (e) { /* */ } },
    async loadDisputes() { try { this.disputes = await claimApi.disputes(this.claimId) } catch (e) { this.disputes = [] } },
    async loadEvidence() {
      this.eviImgs = []
      if (!this.claim || !this.claim.evidenceFileIds) return
      for (const fid of this.claim.evidenceFileIds) {
        try { this.eviImgs.push(await loadPrivateImage(fid)) } catch (e) { /* skip */ }
      }
    },
    previewEvi(i) { uni.previewImage({ current: i, urls: this.eviImgs }) },
    async review(decision) {
      let reason = ''
      if (decision === 'REJECT') {
        const r = await new Promise((res) => uni.showModal({ title: '拒绝理由', editable: true, success: res }))
        if (!r.confirm) return
        reason = r.content
      }
      await claimApi.review(this.claimId, decision, reason)
      uni.showToast({ title: '已处理', icon: 'success' })
      this.reloadAll()
    },
    async withdraw() {
      const r = await new Promise((res) => uni.showModal({ title: '确认撤销申请?', success: res }))
      if (!r.confirm) return
      await claimApi.withdraw(this.claimId)
      uni.showToast({ title: '已撤销', icon: 'success' })
      this.load()
    },
    async confirm() {
      try {
        const s = await claimApi.confirm(this.claimId)
        uni.showToast({ title: s.claimStatus === 'COMPLETED' ? '交接完成' : '已确认，等待对方', icon: 'none' })
        this.reloadAll()
      } catch (e) { this.loadDisputes() }
    },
    async cancelHandover() {
      const r = await new Promise((res) => uni.showModal({ title: '取消交接', editable: true, placeholderText: '请填写理由', success: res }))
      if (!r.confirm || !r.content) { if (r.confirm) uni.showToast({ title: '需填写理由', icon: 'none' }); return }
      await claimApi.cancelHandover(this.claimId, r.content)
      uni.showToast({ title: '已取消交接', icon: 'success' })
      this.reloadAll()
    },
    raiseDispute() {
      uni.showModal({
        title: '发起争议', editable: true, placeholderText: '请填写争议原因',
        success: async (r) => {
          if (r.confirm && r.content) {
            await claimApi.raiseDispute(this.claimId, { reason: r.content, description: '', evidenceFileIds: [] })
            uni.showToast({ title: '争议已提交，交接暂停', icon: 'none' })
            this.reloadAll()
          }
        }
      })
    },
    async send() {
      if (!this.msgText) return
      try {
        await claimApi.sendMessage(this.claimId, this.msgText)
        this.msgText = ''
        this.loadMessages()
      } catch (e) { /* toasted (e.g. USER_RESTRICTED) */ }
    },
    disputeStatusText(s) { return { OPEN: '处理中', RESOLVED: '已裁决', CLOSED: '已关闭' }[s] || s },
    resolutionText(t) { return { CONTINUE: '继续交接', TERMINATE_REOPEN: '终止并重开', CLOSE: '关闭处理' }[t] || t },
    fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '' }
  }
}
</script>

<style scoped>
.page { padding: 20rpx; }
.card { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; }
.ptitle { font-size: 30rpx; font-weight: 600; }
.status { color: #2b6cb0; font-size: 24rpx; margin: 12rpx 0; }
.desc { font-size: 26rpx; margin: 10rpx 0; }
.lb { font-size: 24rpx; color: #909399; }
.evi { margin-top: 12rpx; }
.imgs { display: flex; flex-wrap: wrap; margin-top: 8rpx; }
.thumb { width: 150rpx; height: 150rpx; border-radius: 8rpx; margin: 6rpx; }
.tl { margin-top: 16rpx; }
.tli { font-size: 24rpx; color: #606266; margin-top: 8rpx; }
.mtitle { font-weight: 600; margin-bottom: 14rpx; }
.paused { color: #e6a23c; font-size: 24rpx; margin-bottom: 12rpx; }
.btn { margin-bottom: 12rpx; } .btn.primary { background: #2b6cb0; color: #fff; }
.btn.danger { background: #f56c6c; color: #fff; } .btn.small { display: inline-block; }
.btn[disabled] { opacity: 0.5; }
.dsp { border-top: 1rpx solid #f0f0f0; padding: 12rpx 0; font-size: 26rpx; }
.meta { color: #909399; font-size: 22rpx; margin-top: 6rpx; }
.msg { padding: 12rpx; background: #f5f6f8; border-radius: 8rpx; margin-bottom: 10rpx; font-size: 26rpx; }
.msg.mine { background: #e8f0fe; text-align: right; }
.msgbar { display: flex; margin-top: 14rpx; }
.in { flex: 1; border: 1rpx solid #dcdfe6; border-radius: 8rpx; padding: 14rpx; margin-right: 12rpx; }
</style>
