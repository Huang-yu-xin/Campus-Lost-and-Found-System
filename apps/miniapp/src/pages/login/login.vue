<template>
  <view class="wrap">
    <view class="title">校园失物招领</view>
    <view class="sub">登录后可发布、认领、提供线索</view>

    <view class="card">
      <view class="label">测试登录（开发环境）</view>
      <input class="input" v-model="testUser" placeholder="输入测试用户名，如 userA" />
      <button class="btn primary" :loading="loading" :disabled="loading" @click="doMockLogin">测试登录</button>

      <view class="divider">或</view>

      <button class="btn" :loading="loading" :disabled="loading" @click="doWechatLogin">微信一键登录</button>
      <view class="hint">微信登录需后端配置 AppSecret；未配置时请用测试登录。</view>
    </view>

    <view class="notice">登录仅代表持有系统登录态，不代表校园身份已认证。</view>
  </view>
</template>

<script>
import { authApi } from '../../api/index'
import { setToken } from '../../utils/request'

export default {
  data() {
    return { testUser: '' }
  },
  methods: {
    async doMockLogin() {
      if (!this.testUser) {
        uni.showToast({ title: '请输入测试用户名', icon: 'none' })
        return
      }
      try {
        const res = await authApi.mockLogin(this.testUser, '')
        setToken(res.accessToken)
        uni.showToast({ title: '登录成功', icon: 'success' })
        setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 500)
      } catch (e) { /* 已提示 */ }
    },
    doWechatLogin() {
      uni.login({
        provider: 'weixin',
        success: async (loginRes) => {
          try {
            const res = await authApi.wechatLogin(loginRes.code, '')
            setToken(res.accessToken)
            uni.switchTab({ url: '/pages/index/index' })
          } catch (e) { /* 已提示（未配置 AppSecret 会返回不可用） */ }
        },
        fail: () => uni.showToast({ title: '微信登录失败', icon: 'none' })
      })
    }
  }
}
</script>

<style scoped>
.wrap { padding: 60rpx 40rpx; }
.title { font-size: 44rpx; font-weight: 700; text-align: center; }
.sub { color: #909399; text-align: center; margin: 16rpx 0 50rpx; font-size: 26rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 40rpx; }
.label { font-size: 26rpx; color: #606266; margin-bottom: 16rpx; }
.input { border: 1rpx solid #dcdfe6; border-radius: 8rpx; padding: 18rpx; margin-bottom: 24rpx; }
.btn { margin-top: 12rpx; }
.btn.primary { background: #2b6cb0; color: #fff; }
.divider { text-align: center; color: #c0c4cc; margin: 30rpx 0; font-size: 24rpx; }
.hint { color: #c0c4cc; font-size: 22rpx; margin-top: 16rpx; }
.notice { color: #e6a23c; font-size: 22rpx; margin-top: 40rpx; text-align: center; }
</style>
