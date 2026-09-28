<template>
  <view class="page">
    <view class="card profile" v-if="me">
      <view class="nick">{{ me.nickname }}</view>
      <view class="badge">校园身份未认证</view>
    </view>
    <view class="card" v-else>
      <button class="btn primary" @click="goLogin">去登录</button>
    </view>

    <view class="seg" v-if="me">
      <text :class="['s', tab === 'posts' ? 'on' : '']" @click="switchTab('posts')">我的发布</text>
      <text :class="['s', tab === 'claims' ? 'on' : '']" @click="switchTab('claims')">我的申请</text>
      <text :class="['s', tab === 'leads' ? 'on' : '']" @click="switchTab('leads')">我的线索</text>
    </view>

    <view v-if="me">
      <view class="item" v-for="it in items" :key="it.id" @click="open(it)">
        <text class="t">{{ it.title || ('申请#' + it.id) || ('线索#' + it.id) }}</text>
        <text class="st">{{ it.status }}</text>
      </view>
      <view v-if="items.length === 0" class="empty">暂无记录</view>
      <button class="btn" @click="logout">退出登录</button>
    </view>
  </view>
</template>

<script>
import { userApi } from '../../api/index'
import { getToken, clearToken } from '../../utils/request'

export default {
  data() {
    return { me: null, tab: 'posts', items: [] }
  },
  onShow() {
    if (getToken()) {
      this.loadMe()
    } else {
      this.me = null
    }
  },
  methods: {
    async loadMe() {
      try {
        this.me = await userApi.me()
        this.switchTab(this.tab)
      } catch (e) { this.me = null }
    },
    async switchTab(t) {
      this.tab = t
      let res
      if (t === 'posts') res = await userApi.myPosts(1)
      else if (t === 'claims') res = await userApi.myClaims(1)
      else res = await userApi.myLeads(1)
      this.items = res.items || []
    },
    open(it) {
      if (this.tab === 'posts') uni.navigateTo({ url: '/pages/detail/detail?id=' + it.id })
      else if (this.tab === 'claims') uni.navigateTo({ url: '/pages/claim/detail?claimId=' + it.id })
    },
    goLogin() { uni.navigateTo({ url: '/pages/login/login' }) },
    logout() {
      clearToken()
      this.me = null
      uni.showToast({ title: '已退出', icon: 'none' })
    }
  }
}
</script>

<style scoped>
.page { padding: 20rpx; }
.card { background: #fff; border-radius: 12rpx; padding: 30rpx; margin-bottom: 16rpx; }
.nick { font-size: 34rpx; font-weight: 700; }
.badge { display: inline-block; margin-top: 14rpx; font-size: 22rpx; color: #e6a23c; border: 1rpx solid #e6a23c; border-radius: 6rpx; padding: 2rpx 12rpx; }
.seg { display: flex; background: #fff; border-radius: 12rpx; margin-bottom: 16rpx; }
.s { flex: 1; text-align: center; padding: 20rpx; color: #606266; font-size: 26rpx; }
.s.on { color: #2b6cb0; font-weight: 700; }
.item { background: #fff; border-radius: 10rpx; padding: 22rpx; margin-bottom: 12rpx; display: flex; justify-content: space-between; }
.t { font-size: 28rpx; } .st { color: #909399; font-size: 24rpx; }
.empty { text-align: center; color: #c0c4cc; margin: 60rpx 0; }
.btn { margin-top: 20rpx; } .btn.primary { background: #2b6cb0; color: #fff; }
</style>
