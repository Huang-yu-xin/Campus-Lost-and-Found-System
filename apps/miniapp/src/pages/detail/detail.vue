<template>
  <view class="page" v-if="post">
    <view class="card">
      <view class="row">
        <text :class="['badge', post.type === 'LOST' ? 'lost' : 'found']">{{ typeLabel(post.type) }}</text>
        <text class="ptitle">{{ post.title }}</text>
      </view>
      <view class="status">状态：{{ postStatusLabel(post.status, post.type) }}</view>
      <view class="desc">{{ post.publicDescription }}</view>

      <view class="imgs" v-if="post.imageFileIds && post.imageFileIds.length">
        <image class="img" v-for="(fid, i) in post.imageFileIds" :key="fid" :src="imgUrl(fid)" mode="aspectFill" @click="previewPublic(i)" />
      </view>

      <view class="meta">类别：{{ post.category }}</view>
      <view class="meta">校区：{{ post.campus || '未填' }} · 地点：{{ post.eventLocation || '未填' }}</view>
      <view class="meta">事件时间：{{ formatTime(post.eventTime) }}</view>
      <view class="meta">发布者：{{ post.publisherNickname }}</view>
    </view>

    <view class="actions">
      <button v-if="post.type === 'FOUND' && post.status === 'ACTIVE' && !post.mine" class="btn primary" @click="showClaimForm = !showClaimForm">申请认领</button>
      <button v-if="post.type === 'LOST' && post.status === 'ACTIVE' && !post.mine" class="btn primary" @click="showLeadForm = !showLeadForm">提供线索</button>
      <button class="btn" @click="viewMatches">查看匹配候选</button>
      <button v-if="post.mine && post.status === 'ACTIVE'" class="btn" @click="goEdit">编辑</button>
      <button v-if="post.mine && post.type === 'LOST' && post.status === 'ACTIVE'" class="btn" @click="markFound">标记已找回</button>
      <button v-if="post.mine && post.status === 'ACTIVE'" class="btn danger" @click="withdraw">撤回发布</button>
    </view>

    <!-- 认领申请内联表单（含私密证据） -->
    <view v-if="showClaimForm" class="card">
      <view class="mtitle">认领申请</view>
      <textarea class="ta" v-model="claimDesc" placeholder="填写私密证明（物品特征、内含物等，仅发布者与你可见）" />
      <view class="imgs">
        <view class="imgbox" v-for="(p, i) in claimImgs" :key="i">
          <image class="thumb" :src="p" mode="aspectFill" />
          <text class="del" @click="claimImgs.splice(i,1)">×</text>
        </view>
        <view class="imgadd" v-if="claimImgs.length < 3" @click="addImg(claimImgs)">＋</view>
      </view>
      <button class="btn primary" :loading="submitting" @click="submitClaim">提交申请</button>
    </view>

    <!-- 线索内联表单 -->
    <view v-if="showLeadForm" class="card">
      <view class="mtitle">提供线索</view>
      <textarea class="ta" v-model="leadBody" placeholder="描述你看到的线索（仅寻物发布者与你可见）" />
      <view class="imgs">
        <view class="imgbox" v-for="(p, i) in leadImgs" :key="i">
          <image class="thumb" :src="p" mode="aspectFill" />
          <text class="del" @click="leadImgs.splice(i,1)">×</text>
        </view>
        <view class="imgadd" v-if="leadImgs.length < 3" @click="addImg(leadImgs)">＋</view>
      </view>
      <button class="btn primary" :loading="submitting" @click="submitLead">提交线索</button>
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
  <!-- P1-F2：加载失败错误态 + 重试 -->
  <view class="page errstate" v-else-if="error">
    <text class="errmsg">加载失败，请稍后重试</text>
    <button class="btn primary" @click="load">重试</button>
  </view>
</template>

<script>
import { postApi, claimApi, leadApi, uploadFile, fileUrl } from '../../api/index'
import { getToken } from '../../utils/request'
import { postStatusLabel, typeLabel } from '../../utils/labels'

export default {
  data() {
    return {
      id: null, post: null, matches: [], error: false,
      showClaimForm: false, claimDesc: '', claimImgs: [],
      showLeadForm: false, leadBody: '', leadImgs: [],
      submitting: false
    }
  },
  onLoad(query) {
    this.id = query.id
    this.load()
  },
  methods: {
    postStatusLabel, typeLabel,
    imgUrl: (fid) => fileUrl(fid),
    async load() {
      this.error = false
      try {
        this.post = await postApi.detail(this.id)
      } catch (e) {
        this.post = null
        this.error = true
        if (e && (e.statusCode === 401 || e.code === 'UNAUTHENTICATED')) {
          uni.navigateTo({ url: '/pages/login/login' })
        }
      }
    },
    requireLogin() {
      if (!getToken()) { uni.navigateTo({ url: '/pages/login/login' }); return false }
      return true
    },
    previewPublic(i) {
      uni.previewImage({ current: i, urls: this.post.imageFileIds.map((fid) => fileUrl(fid)) })
    },
    addImg(arr) {
      uni.chooseImage({ count: 3 - arr.length, success: (r) => r.tempFilePaths.forEach((p) => { if (arr.length < 3) arr.push(p) }) })
    },
    goEdit() { uni.navigateTo({ url: '/pages/publish/publish?id=' + this.id }) },
    async submitClaim() {
      if (!this.requireLogin()) return
      if (!this.claimDesc.trim()) { uni.showToast({ title: '请填写证明说明', icon: 'none' }); return }
      this.submitting = true
      try {
        const evidenceFileIds = []
        for (const p of this.claimImgs) evidenceFileIds.push(await uploadFile(p, 'PRIVATE_CLAIM'))
        const c = await claimApi.submit(this.id, { description: this.claimDesc.trim(), evidenceFileIds })
        uni.showToast({ title: '申请已提交', icon: 'success' })
        setTimeout(() => uni.navigateTo({ url: '/pages/claim/detail?claimId=' + c.id }), 600)
      } catch (e) { /* toasted */ } finally { this.submitting = false }
    },
    async submitLead() {
      if (!this.requireLogin()) return
      if (!this.leadBody.trim()) { uni.showToast({ title: '请填写线索', icon: 'none' }); return }
      this.submitting = true
      try {
        const evidenceFileIds = []
        for (const p of this.leadImgs) evidenceFileIds.push(await uploadFile(p, 'PRIVATE_LEAD'))
        await leadApi.submit(this.id, { body: this.leadBody.trim(), evidenceFileIds })
        uni.showToast({ title: '线索已提交', icon: 'success' })
        this.showLeadForm = false; this.leadBody = ''; this.leadImgs = []
      } catch (e) { /* toasted */ } finally { this.submitting = false }
    },
    async viewMatches() {
      const res = await postApi.matches(this.id)
      this.matches = res.items || []
      if (!this.matches.length) uni.showToast({ title: '暂无匹配候选', icon: 'none' })
    },
    async markFound() {
      // P1-F4：标记已找回不可逆，二次确认
      const r = await new Promise((res) => uni.showModal({
        title: '确认标记已找回？',
        content: '标记后该寻物信息将关闭，无法撤销。',
        success: res
      }))
      if (!r.confirm) return
      await postApi.markFound(this.id)
      uni.showToast({ title: '已标记找回', icon: 'success' })
      this.load()
    },
    async withdraw() {
      const r = await new Promise((res) => uni.showModal({ title: '确认撤回?', success: res }))
      if (r.confirm) { await postApi.withdraw(this.id); uni.showToast({ title: '已撤回', icon: 'success' }); this.load() }
    },
    openDetail(id) { uni.navigateTo({ url: '/pages/detail/detail?id=' + id }) },
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
.imgs { display: flex; flex-wrap: wrap; margin: 10rpx 0; }
.img { width: 200rpx; height: 200rpx; border-radius: 8rpx; margin: 6rpx; }
.imgbox { position: relative; width: 150rpx; height: 150rpx; margin: 6rpx; }
.thumb { width: 150rpx; height: 150rpx; border-radius: 8rpx; }
.del { position: absolute; top: -10rpx; right: -10rpx; background: #f56c6c; color: #fff; width: 34rpx; height: 34rpx; border-radius: 50%; text-align: center; line-height: 34rpx; }
.imgadd { width: 150rpx; height: 150rpx; margin: 6rpx; border: 1rpx dashed #c0c4cc; border-radius: 8rpx; text-align: center; line-height: 150rpx; font-size: 50rpx; color: #c0c4cc; }
.meta { color: #909399; font-size: 24rpx; margin-top: 8rpx; }
.actions { margin: 20rpx 0; }
.btn { margin-bottom: 14rpx; }
.btn.primary { background: #2b6cb0; color: #fff; }
.btn.danger { background: #f56c6c; color: #fff; }
.ta { border: 1rpx solid #dcdfe6; border-radius: 8rpx; padding: 16rpx; height: 140rpx; width: 100%; box-sizing: border-box; margin-bottom: 12rpx; }
.mtitle { font-weight: 600; margin-bottom: 14rpx; }
.mitem { border-top: 1rpx solid #f0f0f0; padding: 16rpx 0; }
.score { color: #67c23a; margin-left: 14rpx; }
.reasons { color: #909399; font-size: 22rpx; margin-top: 8rpx; }
.errstate { padding-top: 160rpx; text-align: center; }
.errmsg { display: block; color: #909399; font-size: 28rpx; margin-bottom: 30rpx; }
</style>
