<template>
  <view class="page" v-if="post">
    <view class="card">
      <view class="row">
        <text :class="['badge', post.type === 'LOST' ? 'lost' : 'found']">{{ post.type === 'LOST' ? '寻物' : '招领' }}</text>
        <text class="ptitle">{{ post.title }}</text>
      </view>
      <view class="status">状态：{{ statusText(post.status) }}</view>
      <view class="desc">{{ post.publicDescription }}</view>
      <view class="meta">类别：{{ post.category }}</view>
      <view class="meta">校区：{{ post.campus || '未填' }} · 地点：{{ post.eventLocation || '未填' }}</view>
      <view class="meta">事件时间：{{ formatTime(post.eventTime) }}</view>
      <view class="meta">发布者：{{ post.publisherNickname }}</view>
    </view>

    <view class="actions">
      <button v-if="post.type === 'FOUND' && post.status === 'ACTIVE' && !post.mine" class="btn primary" @click="submitClaim">申请认领</button>
      <button v-if="post.type === 'LOST' && post.status === 'ACTIVE' && !post.mine" class="btn primary" @click="submitLead">提供线索</button>
      <button class="btn" @click="viewMatches">查看匹配候选</button>
      <button v-if="post.mine && post.type === 'LOST' && post.status === 'ACTIVE'" class="btn" @click="markFound">标记已找回</button>
      <button v-if="post.mine && post.status === 'ACTIVE'" class="btn danger" @click="withdraw">撤回发布</button>
    </view>

    <view v-if="matches.length" class="card">
      <view class="mtitle">匹配候选（仅供参考，不证明归属）</view>
      <view class="mitem" v-for="m in matches" :key="m.postId" @click="openDetail(m.postId)">
        <text class="ptitle">{{ m.title }}</text>
        <text class="score">{{ Math.round(m.score * 100) }}分</text>
        <view class="reasons">{{ (m.reasons || []).join('；') }}</view>
      </view>
    </view>
  </view>
</template>

<script>
import { postApi, claimApi, leadApi } from '../../api/index'
import { getToken } from '../../utils/request'

export default {
  data() {
    return { id: null, post: null, matches: [] }
  },
  onLoad(query) {
    this.id = query.id
    this.load()
  },
  methods: {
    async load() {
      this.post = await postApi.detail(this.id)
    },
    requireLogin() {
      if (!getToken()) {
        uni.navigateTo({ url: '/pages/login/login' })
        return false
      }
      return true
    },
    submitClaim() {
      if (!this.requireLogin()) return
      uni.showModal({
        title: '认领申请',
        editable: true,
        placeholderText: '请填写私密证明（物品特征等）',
        success: async (r) => {
          if (r.confirm && r.content) {
            try {
              const c = await claimApi.submit(this.id, { description: r.content, evidenceFileIds: [] })
              uni.showToast({ title: '申请已提交', icon: 'success' })
              uni.navigateTo({ url: '/pages/claim/detail?claimId=' + c.id })
            } catch (e) { /* 已提示 */ }
          }
        }
      })
    },
    submitLead() {
      if (!this.requireLogin()) return
      uni.showModal({
        title: '提供线索',
        editable: true,
        placeholderText: '请描述你看到的线索',
        success: async (r) => {
          if (r.confirm && r.content) {
            try {
              await leadApi.submit(this.id, { body: r.content, evidenceFileIds: [] })
              uni.showToast({ title: '线索已提交', icon: 'success' })
            } catch (e) { /* 已提示 */ }
          }
        }
      })
    },
    async viewMatches() {
      const res = await postApi.matches(this.id)
      this.matches = res.items || []
      if (!this.matches.length) uni.showToast({ title: '暂无匹配候选', icon: 'none' })
    },
    async markFound() {
      await postApi.markFound(this.id)
      uni.showToast({ title: '已标记找回', icon: 'success' })
      this.load()
    },
    async withdraw() {
      const r = await new Promise((res) => uni.showModal({ title: '确认撤回?', success: res }))
      if (r.confirm) {
        await postApi.withdraw(this.id)
        uni.showToast({ title: '已撤回', icon: 'success' })
        this.load()
      }
    },
    openDetail(id) {
      uni.navigateTo({ url: '/pages/detail/detail?id=' + id })
    },
    statusText(s) {
      const map = { ACTIVE: '进行中', HANDOVER: '交接中', COMPLETED: '已完成', WITHDRAWN: '已撤回', REMOVED: '已下架' }
      return map[s] || s
    },
    formatTime(t) { return t ? t.replace('T', ' ').slice(0, 16) : '未填写' }
  }
}
</script>

<style scoped>
.page { padding: 20rpx; }
.card { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; }
.row { display: flex; align-items: center; }
.badge { font-size: 22rpx; padding: 2rpx 12rpx; border-radius: 6rpx; margin-right: 14rpx; color: #fff; }
.badge.lost { background: #e6a23c; } .badge.found { background: #2b6cb0; }
.ptitle { font-size: 30rpx; font-weight: 600; }
.status { color: #2b6cb0; font-size: 24rpx; margin: 14rpx 0; }
.desc { font-size: 28rpx; margin: 16rpx 0; }
.meta { color: #909399; font-size: 24rpx; margin-top: 8rpx; }
.actions { margin: 20rpx 0; }
.btn { margin-bottom: 14rpx; }
.btn.primary { background: #2b6cb0; color: #fff; }
.btn.danger { background: #f56c6c; color: #fff; }
.mtitle { font-weight: 600; margin-bottom: 14rpx; }
.mitem { border-top: 1rpx solid #f0f0f0; padding: 16rpx 0; }
.score { color: #67c23a; margin-left: 14rpx; }
.reasons { color: #909399; font-size: 22rpx; margin-top: 8rpx; }
</style>
