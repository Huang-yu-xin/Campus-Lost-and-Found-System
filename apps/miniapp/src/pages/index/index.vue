<template>
  <view class="page">
    <view class="searchbar">
      <input class="search" v-model="keyword" placeholder="搜索标题/描述" @confirm="reload" />
      <view class="tabs">
        <text :class="['tab', type === '' ? 'on' : '']" @click="switchType('')">全部</text>
        <text :class="['tab', type === 'LOST' ? 'on' : '']" @click="switchType('LOST')">寻物</text>
        <text :class="['tab', type === 'FOUND' ? 'on' : '']" @click="switchType('FOUND')">招领</text>
      </view>
    </view>

    <view v-if="list.length === 0 && !loading" class="empty">暂无信息</view>

    <view class="card" v-for="p in list" :key="p.id" @click="openDetail(p.id)">
      <view class="row">
        <text :class="['badge', p.type === 'LOST' ? 'lost' : 'found']">{{ p.type === 'LOST' ? '寻物' : '招领' }}</text>
        <text class="ptitle">{{ p.title }}</text>
      </view>
      <view class="meta">{{ p.category }} · {{ p.campus || '未填校区' }} · {{ p.eventLocation || '地点未填' }}</view>
      <view class="meta">事件时间：{{ formatTime(p.eventTime) }}</view>
    </view>

    <view v-if="loading" class="loading">加载中...</view>
    <view class="fab" @click="goPublish">＋</view>
  </view>
</template>

<script>
import { postApi } from '../../api/index'
import { getToken } from '../../utils/request'

export default {
  data() {
    return { list: [], keyword: '', type: '', page: 1, loading: false }
  },
  onShow() {
    this.reload()
  },
  methods: {
    async reload() {
      this.page = 1
      this.loading = true
      try {
        const res = await postApi.list({ keyword: this.keyword, type: this.type, page: 1, pageSize: 20 })
        this.list = res.items || []
      } catch (e) { /* 已提示 */ } finally {
        this.loading = false
      }
    },
    switchType(t) {
      this.type = t
      this.reload()
    },
    openDetail(id) {
      uni.navigateTo({ url: '/pages/detail/detail?id=' + id })
    },
    goPublish() {
      if (!getToken()) {
        uni.navigateTo({ url: '/pages/login/login' })
        return
      }
      uni.navigateTo({ url: '/pages/publish/publish' })
    },
    formatTime(t) {
      return t ? t.replace('T', ' ').slice(0, 16) : '未填写'
    }
  }
}
</script>

<style scoped>
.page { padding: 20rpx; }
.searchbar { background: #fff; border-radius: 12rpx; padding: 20rpx; margin-bottom: 20rpx; }
.search { border: 1rpx solid #dcdfe6; border-radius: 8rpx; padding: 16rpx; }
.tabs { display: flex; margin-top: 20rpx; }
.tab { margin-right: 30rpx; color: #606266; font-size: 28rpx; }
.tab.on { color: #2b6cb0; font-weight: 700; }
.card { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; }
.row { display: flex; align-items: center; }
.badge { font-size: 22rpx; padding: 2rpx 12rpx; border-radius: 6rpx; margin-right: 14rpx; color: #fff; }
.badge.lost { background: #e6a23c; }
.badge.found { background: #2b6cb0; }
.ptitle { font-size: 30rpx; font-weight: 600; }
.meta { color: #909399; font-size: 24rpx; margin-top: 10rpx; }
.empty, .loading { text-align: center; color: #c0c4cc; margin-top: 80rpx; }
.fab { position: fixed; right: 40rpx; bottom: 60rpx; width: 96rpx; height: 96rpx; border-radius: 50%;
  background: #2b6cb0; color: #fff; font-size: 56rpx; text-align: center; line-height: 90rpx; }
</style>
