<template>
  <view class="page">
    <view class="card profile" v-if="me">
      <view class="phead">
        <view class="nick">{{ me.nickname }}</view>
        <text class="edit" @click="editProfile">编辑资料</text>
      </view>
      <view class="badge">{{ campusText }}</view>
    </view>
    <view class="card" v-else>
      <button class="btn primary" @click="goLogin">去登录</button>
    </view>

    <view class="seg" v-if="me">
      <text :class="['s', tab === 'posts' ? 'on' : '']" @click="switchTab('posts')">我的发布</text>
      <text :class="['s', tab === 'claims' ? 'on' : '']" @click="switchTab('claims')">我的申请</text>
      <text :class="['s', tab === 'rclaims' ? 'on' : '']" @click="switchTab('rclaims')">收到申请</text>
      <text :class="['s', tab === 'leads' ? 'on' : '']" @click="switchTab('leads')">我的线索</text>
      <text :class="['s', tab === 'rleads' ? 'on' : '']" @click="switchTab('rleads')">收到线索</text>
    </view>

    <view v-if="me">
      <view class="item" v-for="it in items" :key="it.id" @click="open(it)">
        <text class="t">{{ title(it) }}</text>
        <text class="st">{{ statusText(it) }}</text>
      </view>
      <view v-if="error && !loading" class="errstate">
        <text class="errmsg">加载失败，请稍后重试</text>
        <button class="rbtn" @click="switchTab(tab)">重试</button>
      </view>
      <view v-else-if="items.length === 0 && !loading" class="empty">暂无记录</view>
      <view v-if="noMore && items.length" class="tipc">没有更多了</view>
      <button class="btn" @click="logout">退出登录</button>
    </view>
  </view>
</template>

<script>
import { userApi, authApi } from '../../api/index'
import { getToken, clearToken } from '../../utils/request'
import { postStatusLabel, claimStatusLabel, leadStatusLabel } from '../../utils/labels'

export default {
  data() {
    return { me: null, campusText: '校园身份未认证', tab: 'posts', items: [], page: 1, pageSize: 20, loading: false, noMore: false, error: false, _seq: 0 }
  },
  onShow() {
    if (getToken()) { this.loadMe() } else { this.me = null }
  },
  onReachBottom() { if (!this.noMore && !this.loading) this.loadMore() },
  onPullDownRefresh() { // E9
    const done = () => uni.stopPullDownRefresh()
    if (getToken()) { this.loadMe().finally(done) } else { this.me = null; done() }
  },
  methods: {
    async loadMe() {
      try {
        this.me = await userApi.me()
        try {
          const cap = await authApi.campusCapabilities()
          this.campusText = cap.verificationEnabled ? '校园身份认证已开启' : '校园身份未认证（学校统一认证未接入）'
        } catch (e) { /* keep default */ }
        this.switchTab(this.tab)
      } catch (e) { this.me = null }
    },
    fetch(page) {
      if (this.tab === 'posts') return userApi.myPosts(page)
      if (this.tab === 'claims') return userApi.myClaims(page)
      if (this.tab === 'rclaims') return userApi.receivedClaims(page)
      if (this.tab === 'leads') return userApi.myLeads(page)
      return userApi.receivedLeads(page)
    },
    async switchTab(t) {
      const seq = ++this._seq // E11
      this.tab = t; this.page = 1; this.noMore = false; this.loading = true; this.error = false
      try {
        const res = await this.fetch(1)
        if (seq !== this._seq) return
        this.items = res.items || []
        if (this.items.length < this.pageSize) this.noMore = true
      } catch (e) {
        if (seq !== this._seq) return
        this.error = true; this.items = []
      } finally {
        if (seq === this._seq) this.loading = false
      }
    },
    async loadMore() {
      this.page += 1; this.loading = true
      try {
        const res = await this.fetch(this.page)
        const items = res.items || []
        this.items = this.items.concat(items)
        if (items.length < this.pageSize) this.noMore = true
      } catch (e) { this.page -= 1 /* E10 */ } finally { this.loading = false }
    },
    title(it) {
      if (this.tab === 'posts') return it.title
      if (this.tab === 'claims') return '申请 · ' + (it.postTitle || ('招领#' + it.postId))
      if (this.tab === 'rclaims') return '收到申请 · ' + (it.postTitle || ('招领#' + it.postId))
      if (this.tab === 'leads') return '线索 · 寻物#' + it.lostPostId
      return '收到线索 · ' + (it.postTitle || ('寻物#' + it.lostPostId))
    },
    statusText(it) {
      if (this.tab === 'posts') return postStatusLabel(it.status, it.type)
      if (this.tab === 'claims' || this.tab === 'rclaims') return claimStatusLabel(it.status)
      return leadStatusLabel(it.status)
    },
    open(it) {
      if (this.tab === 'posts') uni.navigateTo({ url: '/pages/detail/detail?id=' + it.id })
      else if (this.tab === 'claims' || this.tab === 'rclaims') uni.navigateTo({ url: '/pages/claim/detail?claimId=' + it.id })
      else uni.navigateTo({ url: '/pages/lead/detail?leadId=' + it.id }) // E18：owner 由后端 lead.owner 决定，不再传 URL 参数
    },
    editProfile() {
      // uni 单输入弹窗限制：昵称、校区分两步录入，一并 PATCH
      uni.showModal({
        title: '修改昵称', editable: true, maxlength: 64, placeholderText: this.me.nickname || '昵称',
        success: (r1) => {
          if (!r1.confirm) return
          const nickname = (r1.content || '').trim() || this.me.nickname
          uni.showModal({
            title: '修改校区（可留空）', editable: true, maxlength: 64, placeholderText: this.me.campus || '校区',
            success: async (r2) => {
              if (!r2.confirm) return
              const payload = { nickname }
              // B3(P1-F3)：校区留空则不下发 campus 键，避免把已有校区静默清空
              const campus = (r2.content || '').trim()
              if (campus) payload.campus = campus
              await userApi.update(payload)
              uni.showToast({ title: '已保存', icon: 'success' })
              this.loadMe()
            }
          })
        }
      })
    },
    goLogin() { uni.navigateTo({ url: '/pages/login/login' }) },
    async logout() {
      const r = await new Promise((res) => uni.showModal({ title: '确认退出?', success: res }))
      if (!r.confirm) return
      try { await authApi.logout() } catch (e) { /* ignore */ }
      clearToken(); this.me = null
      uni.showToast({ title: '已退出', icon: 'none' })
    }
  }
}
</script>

<style scoped>
.page { padding: 20rpx; }
.card { background: #fff; border-radius: 12rpx; padding: 30rpx; margin-bottom: 16rpx; }
.phead { display: flex; justify-content: space-between; align-items: center; }
.nick { font-size: 34rpx; font-weight: 700; }
.edit { color: #2b6cb0; font-size: 26rpx; }
.badge { display: inline-block; margin-top: 14rpx; font-size: 22rpx; color: #e6a23c; border: 1rpx solid #e6a23c; border-radius: 6rpx; padding: 2rpx 12rpx; }
.seg { display: flex; background: #fff; border-radius: 12rpx; margin-bottom: 16rpx; overflow-x: auto; }
.s { flex: none; padding: 20rpx 18rpx; color: #606266; font-size: 24rpx; white-space: nowrap; }
.s.on { color: #2b6cb0; font-weight: 700; }
.item { background: #fff; border-radius: 10rpx; padding: 22rpx; margin-bottom: 12rpx; display: flex; justify-content: space-between; }
.t { font-size: 28rpx; } .st { color: #909399; font-size: 24rpx; }
.empty, .tipc { text-align: center; color: #c0c4cc; margin: 40rpx 0; }
.errstate { text-align: center; margin: 60rpx 0; }
.errmsg { display: block; color: #909399; font-size: 28rpx; margin-bottom: 24rpx; }
.rbtn { display: inline-block; background: #2b6cb0; color: #fff; font-size: 26rpx; padding: 8rpx 40rpx; border-radius: 8rpx; }
.btn { margin-top: 20rpx; } .btn.primary { background: #2b6cb0; color: #fff; }
</style>
