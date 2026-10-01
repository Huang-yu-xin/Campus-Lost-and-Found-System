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
      <button class="btn" :disabled="hasOpenDispute" @click="showDisputeForm = !showDisputeForm">发起争议</button>
      <button class="btn danger" v-if="claim.publisher && !hasOpenDispute" @click="cancelHandover">取消本次交接</button>

      <view v-if="showDisputeForm && !hasOpenDispute" class="dform">
        <textarea class="ta" v-model="disputeReason" placeholder="请填写争议原因" />
        <text class="lb">证据图片（可选，最多3张）</text>
        <view class="imgs">
          <view class="imgbox" v-for="(p, i) in disputeImgs" :key="i">
            <image class="thumb" :src="p" mode="aspectFill" />
            <text class="del" @click="disputeImgs.splice(i,1)">×</text>
          </view>
          <view class="imgadd" v-if="disputeImgs.length < 3" @click="addDisputeImg">＋</view>
        </view>
        <button class="btn primary" :loading="submittingDispute" @click="submitDispute">提交争议</button>
      </view>
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

    <!-- V4 闭环：认领完成后，申请人关联自己的寻物帖 -->
    <view v-if="claim.status === 'COMPLETED' && claim.applicant" class="card">
      <view class="mtitle">关联我的寻物帖</view>
      <view v-if="resolvedDone" class="resolved">已关联寻物帖 ✓ 你的寻物信息已标记为「已找回」。</view>
      <block v-else>
        <view class="meta">把这次认领关联到你发布的寻物帖，系统会自动把它标记为「已找回」。</view>
        <!-- 推荐候选 -->
        <view v-if="candidates.length" class="cand-list">
          <view class="cand" v-for="c in candidates" :key="c.id">
            <view class="cand-main">
              <view class="cand-title">{{ c.title }}</view>
              <view class="meta">{{ c.campus || '—' }} · {{ fmt(c.eventTime) || '时间未填' }} · 文本重合 {{ c.overlap }}</view>
            </view>
            <button class="btn primary small" :loading="resolving" @click="doResolve(c.id)">关联并结束寻物</button>
          </view>
        </view>
        <!-- 无候选 → 手动选择 -->
        <view v-else class="cand-empty">
          <view class="meta">没有自动匹配到的寻物帖。</view>
          <button class="btn small" @click="toggleManual">手动选择我的寻物帖</button>
          <view v-if="showManual" class="cand-list">
            <view v-if="!manualPosts.length" class="meta">你当前没有进行中的寻物帖。</view>
            <view class="cand" v-for="p in manualPosts" :key="p.id">
              <view class="cand-main">
                <view class="cand-title">{{ p.title }}</view>
                <view class="meta">{{ p.campus || '—' }} · {{ fmt(p.eventTime) || '时间未填' }}</view>
              </view>
              <button class="btn primary small" :loading="resolving" @click="doResolve(p.id)">关联</button>
            </view>
          </view>
        </view>
      </block>
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
  <!-- P1-F2：加载失败错误态 + 重试 -->
  <view class="page errstate" v-else-if="error">
    <text class="errmsg">加载失败，请稍后重试</text>
    <button class="btn primary" @click="reloadAll">重试</button>
  </view>
</template>

<script>
import { claimApi, userApi, loadPrivateImage, uploadFile } from '../../api/index'
import { claimStatusLabel } from '../../utils/labels'

export default {
  data() {
    return {
      claimId: null, claim: null, error: false, messages: [], disputes: [], eviImgs: [], msgText: '',
      showDisputeForm: false, disputeReason: '', disputeImgs: [], submittingDispute: false,
      // V4 闭环：关联寻物帖
      candidates: [], resolvedDone: false, showManual: false, manualPosts: [], resolving: false
    }
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
      const ok = await this.load()
      if (!ok) return
      this.loadMessages()
      this.loadDisputes()
      this.loadEvidence()
      this.loadCandidates()
    },
    async loadCandidates() {
      if (!this.claim || this.claim.status !== 'COMPLETED' || !this.claim.applicant) return
      if (this.resolvedDone) return
      try {
        const r = await claimApi.resolvedCandidates(this.claimId)
        this.candidates = (r && r.items) || []
      } catch (e) { this.candidates = [] }
    },
    toggleManual() {
      this.showManual = !this.showManual
      if (this.showManual && !this.manualPosts.length) this.loadManualPosts()
    },
    async loadManualPosts() {
      try {
        const r = await userApi.myPosts(1)
        const items = (r && r.items) || []
        this.manualPosts = items.filter((p) => p.type === 'LOST' && p.status === 'ACTIVE')
      } catch (e) { this.manualPosts = [] }
    },
    async doResolve(lostPostId) {
      this.resolving = true
      try {
        await claimApi.resolveLost(this.claimId, lostPostId)
        uni.showToast({ title: '已关联，寻物帖已标记为已找回', icon: 'none' })
        this.resolvedDone = true
        this.candidates = []
        this.showManual = false
        await this.load()
      } catch (e) { /* toasted by request.js */ } finally { this.resolving = false }
    },
    async load() {
      this.error = false
      try {
        this.claim = await claimApi.detail(this.claimId)
        // B7/R4：从后端持久字段恢复"已关联"态，重进页面仍保持
        this.resolvedDone = !!(this.claim && this.claim.resolvedLostPostId)
        return true
      } catch (e) {
        this.claim = null
        this.error = true
        if (e && (e.statusCode === 401 || e.code === 'UNAUTHENTICATED')) {
          uni.navigateTo({ url: '/pages/login/login' })
        }
        return false
      }
    },
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
    addDisputeImg() {
      uni.chooseImage({
        count: 3 - this.disputeImgs.length,
        success: (r) => r.tempFilePaths.forEach((p) => { if (this.disputeImgs.length < 3) this.disputeImgs.push(p) })
      })
    },
    async submitDispute() {
      if (!this.disputeReason.trim()) { uni.showToast({ title: '请填写争议原因', icon: 'none' }); return }
      this.submittingDispute = true
      try {
        const evidenceFileIds = []
        for (const p of this.disputeImgs) evidenceFileIds.push(await uploadFile(p, 'PRIVATE_DISPUTE'))
        await claimApi.raiseDispute(this.claimId, { reason: this.disputeReason.trim(), description: '', evidenceFileIds })
        uni.showToast({ title: '争议已提交，交接暂停', icon: 'none' })
        this.showDisputeForm = false; this.disputeReason = ''; this.disputeImgs = []
        this.reloadAll()
      } catch (e) { /* toasted */ } finally { this.submittingDispute = false }
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
.dform { margin-top: 14rpx; border-top: 1rpx solid #f0f0f0; padding-top: 14rpx; }
.ta { border: 1rpx solid #dcdfe6; border-radius: 8rpx; padding: 16rpx; height: 130rpx; width: 100%; box-sizing: border-box; margin-bottom: 12rpx; }
.imgbox { position: relative; width: 150rpx; height: 150rpx; margin: 6rpx; }
.del { position: absolute; top: -10rpx; right: -10rpx; background: #f56c6c; color: #fff; width: 34rpx; height: 34rpx; border-radius: 50%; text-align: center; line-height: 34rpx; }
.imgadd { width: 150rpx; height: 150rpx; margin: 6rpx; border: 1rpx dashed #c0c4cc; border-radius: 8rpx; text-align: center; line-height: 150rpx; font-size: 50rpx; color: #c0c4cc; }
.resolved { color: #2e7d32; font-size: 26rpx; }
.cand-list { margin-top: 12rpx; }
.cand { display: flex; align-items: center; justify-content: space-between; border-top: 1rpx solid #f0f0f0; padding: 14rpx 0; }
.cand-main { flex: 1; margin-right: 12rpx; }
.cand-title { font-size: 26rpx; font-weight: 600; }
.cand-empty { margin-top: 12rpx; }
.errstate { padding-top: 160rpx; text-align: center; }
.errmsg { display: block; color: #909399; font-size: 28rpx; margin-bottom: 30rpx; }
</style>
