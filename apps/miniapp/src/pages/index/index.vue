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

    <!-- E29：失败态与空态区分 -->
    <view v-if="error && !loading" class="errstate">
      <text class="errmsg">加载失败，请稍后重试</text>
      <button class="rbtn" @click="reload">重试</button>
    </view>
    <view v-else-if="list.length === 0 && !loading" class="empty">暂无信息</view>

    <view class="card" v-for="p in list" :key="p.id" @click="openDetail(p.id)">
      <view class="ctop">
        <image v-if="p.imageFileIds && p.imageFileIds.length && !p._imgErr" class="cover" :src="cover(p)" mode="aspectFill" @error="p._imgErr = true" />
        <view v-else-if="p.imageFileIds && p.imageFileIds.length" class="cover ph-img" />
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
    return { list: [], type: '', page: 1, pageSize: 20, loading: false, noMore: false, error: false, _seq: 0 }
  },
  onShow() { this.reload() },
  onReachBottom() { if (!this.noMore && !this.loading) this.loadMore() },
  onPullDownRefresh() { this.reload().finally(() => uni.stopPullDownRefresh()) }, // E9
  methods: {
    cover(p) { return fileUrl(p.imageFileIds[0]) },
    async reload() {
      const seq = ++this._seq // E11：请求序号守卫，丢弃过期响应
      this.page = 1; this.noMore = false; this.loading = true; this.error = false
      try {
        const res = await postApi.list({ type: this.type, page: 1, pageSize: this.pageSize })
        if (seq !== this._seq) return
        this.list = res.items || []
        if (this.list.length < this.pageSize) this.noMore = true
      } catch (e) {
        if (seq !== this._seq) return
        this.error = true; this.list = []
      } finally {
        if (seq === this._seq) this.loading = false
      }
    },
    async loadMore() {
      this.page += 1; this.loading = true
      try {
        const res = await postApi.list({ type: this.type, page: this.page, pageSize: this.pageSize })
        const items = res.items || []
        this.list = this.list.concat(items)
        if (items.length < this.pageSize) this.noMore = true
      } catch (e) { this.page -= 1 /* E10：失败回退页码 */ } finally { this.loading = false }
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
.ph-img { background: #f0f0f0; }
.errstate { text-align: center; margin-top: 80rpx; }
.errmsg { display: block; color: #909399; font-size: 28rpx; margin-bottom: 24rpx; }
.rbtn { display: inline-block; background: #2b6cb0; color: #fff; font-size: 26rpx; padding: 8rpx 40rpx; border-radius: 8rpx; }
.fab { position: fixed; right: 40rpx; bottom: 60rpx; width: 96rpx; height: 96rpx; border-radius: 50%;
  background: #2b6cb0; color: #fff; font-size: 56rpx; text-align: center; line-height: 90rpx; }
</style>
