<template>
  <view class="page">
    <view class="filters">
      <input class="in" v-model="q.keyword" placeholder="关键词（标题/描述）" />
      <view class="segline">
        <text class="lb">类型</text>
        <text :class="['seg', q.type === '' ? 'on' : '']" @click="q.type = ''">全部</text>
        <text :class="['seg', q.type === 'LOST' ? 'on' : '']" @click="q.type = 'LOST'">寻物</text>
        <text :class="['seg', q.type === 'FOUND' ? 'on' : '']" @click="q.type = 'FOUND'">招领</text>
      </view>
      <view class="rowline"><text class="lb">类别</text><input class="in2" v-model="q.category" placeholder="如 钱包/钥匙" /></view>
      <view class="rowline"><text class="lb">校区</text><input class="in2" v-model="q.campus" placeholder="校区" /></view>
      <view class="rowline"><text class="lb">起</text>
        <picker mode="date" :value="fromStr" @change="e => fromStr = e.detail.value"><view class="in2">{{ fromStr || '事件起始日' }}</view></picker>
      </view>
      <view class="rowline"><text class="lb">止</text>
        <picker mode="date" :value="toStr" @change="e => toStr = e.detail.value"><view class="in2">{{ toStr || '事件结束日' }}</view></picker>
      </view>
      <view class="btns">
        <button class="btn primary" @click="reload">搜索</button>
        <button class="btn" @click="reset">重置</button>
      </view>
    </view>

    <view v-if="list.length === 0 && !loading" class="empty">无匹配结果</view>
    <view class="card" v-for="p in list" :key="p.id" @click="open(p.id)">
      <view class="row">
        <text :class="['badge', p.type === 'LOST' ? 'lost' : 'found']">{{ p.type === 'LOST' ? '寻物' : '招领' }}</text>
        <text class="ptitle">{{ p.title }}</text>
      </view>
      <view class="meta">{{ p.category }} · {{ p.campus || '未填校区' }} · {{ p.eventLocation || '地点未填' }}</view>
    </view>
    <view v-if="loading" class="tipc">加载中...</view>
    <view v-if="noMore && list.length" class="tipc">没有更多了</view>
  </view>
</template>

<script>
import { postApi } from '../../api/index'

export default {
  data() {
    return {
      q: { keyword: '', type: '', category: '', campus: '' },
      fromStr: '', toStr: '',
      list: [], page: 1, pageSize: 20, loading: false, noMore: false
    }
  },
  onLoad(query) {
    if (query && query.keyword) this.q.keyword = query.keyword
    this.reload()
  },
  onReachBottom() {
    if (!this.noMore && !this.loading) this.loadMore()
  },
  methods: {
    params() {
      const p = { keyword: this.q.keyword, type: this.q.type, category: this.q.category, campus: this.q.campus, page: this.page, pageSize: this.pageSize }
      if (this.fromStr) p.eventFrom = this.fromStr + 'T00:00:00'
      if (this.toStr) p.eventTo = this.toStr + 'T23:59:59'
      return p
    },
    async reload() {
      this.page = 1; this.noMore = false; this.loading = true
      try {
        const res = await postApi.search(this.params())
        this.list = res.items || []
        if (this.list.length < this.pageSize) this.noMore = true
      } catch (e) { /* toasted */ } finally { this.loading = false }
    },
    async loadMore() {
      this.page += 1; this.loading = true
      try {
        const res = await postApi.search(this.params())
        const items = res.items || []
        this.list = this.list.concat(items)
        if (items.length < this.pageSize) this.noMore = true
      } catch (e) { /* toasted */ } finally { this.loading = false }
    },
    reset() {
      this.q = { keyword: '', type: '', category: '', campus: '' }
      this.fromStr = ''; this.toStr = ''
      this.reload()
    },
    open(id) { uni.navigateTo({ url: '/pages/detail/detail?id=' + id }) }
  }
}
</script>

<style scoped>
.page { padding: 20rpx; }
.filters { background: #fff; border-radius: 12rpx; padding: 20rpx; margin-bottom: 16rpx; }
.in { border: 1rpx solid #dcdfe6; border-radius: 8rpx; padding: 16rpx; margin-bottom: 14rpx; }
.segline, .rowline { display: flex; align-items: center; margin-bottom: 14rpx; }
.lb { font-size: 24rpx; color: #909399; width: 70rpx; }
.seg { padding: 8rpx 20rpx; margin-right: 12rpx; border: 1rpx solid #dcdfe6; border-radius: 30rpx; font-size: 24rpx; color: #606266; }
.seg.on { background: #2b6cb0; color: #fff; border-color: #2b6cb0; }
.in2 { flex: 1; border: 1rpx solid #dcdfe6; border-radius: 8rpx; padding: 14rpx; }
.btns { display: flex; }
.btn { flex: 1; margin: 6rpx; }
.btn.primary { background: #2b6cb0; color: #fff; }
.card { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; }
.row { display: flex; align-items: center; }
.badge { font-size: 22rpx; padding: 2rpx 12rpx; border-radius: 6rpx; margin-right: 14rpx; color: #fff; }
.badge.lost { background: #e6a23c; } .badge.found { background: #2b6cb0; }
.ptitle { font-size: 30rpx; font-weight: 600; }
.meta { color: #909399; font-size: 24rpx; margin-top: 10rpx; }
.empty, .tipc { text-align: center; color: #c0c4cc; margin: 40rpx 0; }
</style>
