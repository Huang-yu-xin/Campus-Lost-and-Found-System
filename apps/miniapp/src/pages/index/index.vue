<template>
  <view class="page">
    <view class="searchbar">
      <view class="searchbox" @click="goSearch">
        <text class="ph">搜索标题/描述、按类别/校区/日期筛选</text>
      </view>
      <view class="tabs">
        <text :class="['tab', type === '' ? 'on' : '']" @click="switchType('')">全部</text>
        <text :class="['tab', type === 'LOST' ? 'on' : '']" @click="switchType('LOST')">寻物</text>
        <text :class="['tab', type === 'FOUND' ? 'on' : '']" @click="switchType('FOUND')">招领</text>
      </view>
    </view>

    <view v-if="list.length === 0 && !loading" class="empty">暂无信息</view>

    <view class="card" v-for="p in list" :key="p.id" @click="openDetail(p.id)">
      <view class="ctop">
        <image v-if="p.imageFileIds && p.imageFileIds.length" class="cover" :src="cover(p)" mode="aspectFill" />
        <view class="cbody">
          <view class="row">
            <text :class="['badge', p.type === 'LOST' ? 'lost' : 'found']">{{ p.type === 'LOST' ? '寻物' : '招领' }}</text>
            <text class="ptitle">{{ p.title }}</text>
          </view>
          <view class="meta">{{ p.category }} · {{ p.campus || '未填校区' }}</view>
          <view class="meta">事件时间：{{ formatTime(p.eventTime) }}</view>
        </view>
      </view>
    </view>

    <view v-if="loading" class="tipc">加载中...</view>
    <view v-if="noMore && list.length" class="tipc">没有更多了</view>
    <view class="fab" @click="goPublish">＋</view>
  </view>
</template>

<script>
import { postApi, fileUrl } from '../../api/index'
import { getToken } from '../../utils/request'

export default {
  data() {
    return { list: [], type: '', page: 1, pageSize: 20, loading: false, noMore: false }
  },
  onShow() { this.reload() },
  onReachBottom() { if (!this.noMore && !this.loading) this.loadMore() },
  methods: {
    cover(p) { return fileUrl(p.imageFileIds[0]) },
    async reload() {
      this.page = 1; this.noMore = false; this.loading = true
      try {
        const res = await postApi.list({ type: this.type, page: 1, pageSize: this.pageSize })
        this.list = res.items || []
        if (this.list.length < this.pageSize) this.noMore = true
      } catch (e) { /* toasted */ } finally { this.loading = false }
    },
    async loadMore() {
      this.page += 1; this.loading = true
      try {
        const res = await postApi.list({ type: this.type, page: this.page, pageSize: this.pageSize })
        const items = res.items || []
        this.list = this.list.concat(items)
        if (items.length < this.pageSize) this.noMore = true
      } catch (e) { /* toasted */ } finally { this.loading = false }
    },
    switchType(t) { this.type = t; this.reload() },
    goSearch() { uni.navigateTo({ url: '/pages/search/search' }) },
    openDetail(id) { uni.navigateTo({ url: '/pages/detail/detail?id=' + id }) },
    goPublish() {
      if (!getToken()) { uni.navigateTo({ url: '/pages/login/login' }); return }
      uni.navigateTo({ url: '/pages/publish/publish' })
    },
    formatTime(t) { return t ? t.replace('T', ' ').slice(0, 16) : '未填写' }
  }
}
</script>

<style scoped>
.page { padding: 20rpx; }
.searchbar { background: #fff; border-radius: 12rpx; padding: 20rpx; margin-bottom: 20rpx; }
.searchbox { border: 1rpx solid #dcdfe6; border-radius: 30rpx; padding: 18rpx 24rpx; background: #f7f8fa; }
.ph { color: #c0c4cc; font-size: 26rpx; }
.tabs { display: flex; margin-top: 20rpx; }
.tab { margin-right: 30rpx; color: #606266; font-size: 28rpx; }
.tab.on { color: #2b6cb0; font-weight: 700; }
.card { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; }
.ctop { display: flex; }
.cover { width: 140rpx; height: 140rpx; border-radius: 8rpx; margin-right: 16rpx; }
.cbody { flex: 1; }
.row { display: flex; align-items: center; }
.badge { font-size: 22rpx; padding: 2rpx 12rpx; border-radius: 6rpx; margin-right: 14rpx; color: #fff; }
.badge.lost { background: #e6a23c; }
.badge.found { background: #2b6cb0; }
.ptitle { font-size: 30rpx; font-weight: 600; }
.meta { color: #909399; font-size: 24rpx; margin-top: 10rpx; }
.empty, .tipc { text-align: center; color: #c0c4cc; margin-top: 40rpx; }
.fab { position: fixed; right: 40rpx; bottom: 60rpx; width: 96rpx; height: 96rpx; border-radius: 50%;
  background: #2b6cb0; color: #fff; font-size: 56rpx; text-align: center; line-height: 90rpx; }
</style>
