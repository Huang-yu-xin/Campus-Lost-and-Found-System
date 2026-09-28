<template>
  <view class="page" v-if="claim">
    <view class="card">
      <view class="ptitle">认领申请 #{{ claim.id }}</view>
      <view class="status">状态：{{ statusText(claim.status) }}</view>
      <view class="desc">证明说明：{{ claim.description }}</view>
      <view class="meta">申请人确认：{{ claim.applicantConfirmed ? '已确认' : '未确认' }} · 发布者确认：{{ claim.publisherConfirmed ? '已确认' : '未确认' }}</view>
    </view>

    <!-- 发布者审核 -->
    <view v-if="claim.publisher && claim.status === 'PENDING'" class="card">
      <view class="mtitle">审核申请</view>
      <button class="btn primary" @click="review('ACCEPT')">接受</button>
      <button class="btn danger" @click="review('REJECT')">拒绝</button>
    </view>

    <!-- 交接确认 -->
    <view v-if="claim.status === 'WAITING_HANDOVER'" class="card">
      <view class="mtitle">交接确认</view>
      <button class="btn primary" @click="confirm">我已完成交接</button>
      <button class="btn" @click="raiseDispute">发起争议</button>
    </view>

    <!-- 留言 -->
    <view class="card">
      <view class="mtitle">留言</view>
      <view class="msg" v-for="m in messages" :key="m.id" :class="{ mine: m.mine }">
        <text>{{ m.body }}</text>
      </view>
      <view class="msgbar">
        <input class="in" v-model="msgText" placeholder="输入留言" />
        <button class="btn small" @click="send">发送</button>
      </view>
    </view>
  </view>
</template>

<script>
import { claimApi } from '../../api/index'

export default {
  data() {
    return { claimId: null, claim: null, messages: [], msgText: '' }
  },
  onLoad(query) {
    this.claimId = query.claimId
    this.load()
    this.loadMessages()
  },
  methods: {
    async load() { this.claim = await claimApi.detail(this.claimId) },
    async loadMessages() {
      try { this.messages = await claimApi.messages(this.claimId) } catch (e) { /* ignore */ }
    },
    async review(decision) {
      let reason = ''
      if (decision === 'REJECT') {
        const r = await new Promise((res) => uni.showModal({ title: '拒绝理由', editable: true, success: res }))
        if (!r.confirm) return
        reason = r.content
      }
      await claimApi.review(this.claimId, decision, reason)
      uni.showToast({ title: '已处理', icon: 'success' })
      this.load()
    },
    async confirm() {
      try {
        const s = await claimApi.confirm(this.claimId)
        uni.showToast({ title: s.claimStatus === 'COMPLETED' ? '交接完成' : '已确认，等待对方', icon: 'none' })
        this.load()
      } catch (e) { /* 已提示（争议暂停等） */ }
    },
    raiseDispute() {
      uni.showModal({
        title: '发起争议', editable: true, placeholderText: '请填写争议原因',
        success: async (r) => {
          if (r.confirm && r.content) {
            await claimApi.raiseDispute(this.claimId, { reason: r.content, description: '', evidenceFileIds: [] })
            uni.showToast({ title: '争议已提交，交接暂停', icon: 'none' })
            this.load()
          }
        }
      })
    },
    async send() {
      if (!this.msgText) return
      await claimApi.sendMessage(this.claimId, this.msgText)
      this.msgText = ''
      this.loadMessages()
    },
    statusText(s) {
      const map = { PENDING: '待审核', WAITING_HANDOVER: '待交接', COMPLETED: '已完成', REJECTED: '已拒绝', WITHDRAWN: '已撤销', CLOSED: '已关闭' }
      return map[s] || s
    }
  }
}
</script>

<style scoped>
.page { padding: 20rpx; }
.card { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; }
.ptitle { font-size: 30rpx; font-weight: 600; }
.status { color: #2b6cb0; font-size: 24rpx; margin: 12rpx 0; }
.desc { font-size: 26rpx; margin: 10rpx 0; }
.meta { color: #909399; font-size: 24rpx; }
.mtitle { font-weight: 600; margin-bottom: 14rpx; }
.btn { margin-bottom: 12rpx; } .btn.primary { background: #2b6cb0; color: #fff; }
.btn.danger { background: #f56c6c; color: #fff; } .btn.small { display: inline-block; }
.msg { padding: 12rpx; background: #f5f6f8; border-radius: 8rpx; margin-bottom: 10rpx; font-size: 26rpx; }
.msg.mine { background: #e8f0fe; text-align: right; }
.msgbar { display: flex; margin-top: 14rpx; }
.in { flex: 1; border: 1rpx solid #dcdfe6; border-radius: 8rpx; padding: 14rpx; margin-right: 12rpx; }
</style>
