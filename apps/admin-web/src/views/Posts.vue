<template>
  <div>
    <h3>信息治理</h3>
    <el-form :inline="true">
      <el-form-item label="状态">
        <el-select v-model="status" clearable placeholder="全部" style="width:140px" @change="reload">
          <el-option label="进行中" value="ACTIVE" />
          <el-option label="交接中" value="HANDOVER" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已下架" value="REMOVED" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="type" clearable placeholder="全部" style="width:120px" @change="reload">
          <el-option label="寻物" value="LOST" />
          <el-option label="招领" value="FOUND" />
        </el-select>
      </el-form-item>
      <el-button @click="reload">刷新</el-button>
    </el-form>

    <el-table :data="items" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column :formatter="statusFormatter" prop="type" label="类型" width="90" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="category" label="类别" width="110" />
      <el-table-column :formatter="statusFormatter" prop="status" label="状态" width="110" />
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button size="small" @click="showDetail(row.id)">详情 / 治理历史</el-button>
          <el-button v-if="row.status !== 'REMOVED'" size="small" type="danger" @click="remove(row)">下架</el-button>
          <el-button v-else size="small" type="success" @click="restore(row)">恢复</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:10px" layout="prev, pager, next, total" :total="total"
      :page-size="pageSize" :current-page="page" @current-change="onPage" />
    <el-dialog v-model="detailOpen" title="发布详情与治理历史" width="720px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <h4>{{ detail.post.title }}</h4>
          <p>{{ label(detail.post.type) }} · {{ label(detail.post.status) }} · {{ detail.post.category }}</p>
          <p>{{ detail.post.campus || '未填校区' }} · {{ detail.post.eventLocation || '未填地点' }}</p>
          <p style="white-space:pre-wrap">{{ detail.post.publicDescription }}</p>
          <el-button v-for="fid in detail.imageFileIds" :key="fid" @click="previewFile(fid)">查看图片 #{{ fid }}</el-button>
          <el-table :data="detail.history" style="margin-top:16px" empty-text="暂无治理记录">
            <el-table-column prop="adminId" label="管理员" />
            <el-table-column prop="action" label="操作" :formatter="statusFormatter" />
            <el-table-column prop="reason" label="理由" />
            <el-table-column prop="beforeState" label="处理前" :formatter="statusFormatter" />
            <el-table-column prop="afterState" label="处理后" :formatter="statusFormatter" />
            <el-table-column prop="createdAt" label="时间" />
          </el-table>
          <el-button @click="router.push({ path:'/audit', query:{targetType:'POST', targetId:detail.post.id} })">查看该发布审计</el-button>
        </template>
        <el-alert v-else-if="detailError" type="error" title="详情加载失败，请关闭后重试" />
      </div>
    </el-dialog>
    <el-image-viewer v-if="viewerUrl" :url-list="[viewerUrl]" @close="closeViewer" />
  </div>
</template>

<script setup>
import { label, statusFormatter } from '../utils/labels'
import { useRouter } from 'vue-router'
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, fetchFileObjectUrl } from '../api'

const router = useRouter()
const detail = ref(null), detailOpen = ref(false), detailLoading = ref(false), detailError = ref(false), viewerUrl = ref('')
async function showDetail(id) {
  detail.value=null; detailError.value=false; detailOpen.value=true; detailLoading.value=true
  try { detail.value=await adminApi.getPost(id) }
  catch { detailError.value=true } finally { detailLoading.value=false }
}
function closeViewer() { if(viewerUrl.value) URL.revokeObjectURL(viewerUrl.value); viewerUrl.value='' }
async function previewFile(fid) {
  try { closeViewer(); viewerUrl.value=await fetchFileObjectUrl(fid) } catch(e) { ElMessage.error(e.message || '图片加载失败') }
}
onUnmounted(closeViewer)
const items = ref([])
const loading = ref(false)
const status = ref('')
const type = ref('')
const page = ref(1)
const total = ref(0)
const pageSize = 20

async function load() {
  loading.value = true
  try {
    const data = await adminApi.listPosts({ status: status.value, type: type.value, page: page.value, pageSize })
    items.value = data.items || []
    total.value = data.total || 0
  } catch (e) { ElMessage.error(e?.message || '加载失败') } finally { loading.value = false }
}
// 切换筛选时重置页码
function reload() { page.value = 1; load() }
function onPage(p) { page.value = p; load() }

async function remove(row) {
  try {
    const { value } = await ElMessageBox.prompt('下架理由', '下架', { inputValidator: (v) => !!v || '请填写理由' })
    await adminApi.removePost(row.id, value)
    ElMessage.success('已下架')
    load()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return // 取消/关闭弹窗静默
    ElMessage.error(e?.message || '下架失败')
  }
}
async function restore(row) {
  try {
    const { value } = await ElMessageBox.prompt('恢复理由', '恢复', { inputValidator: (v) => !!v || '请填写理由' })
    await adminApi.restorePost(row.id, value)
    ElMessage.success('已恢复')
    load()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '恢复失败')
  }
}

onMounted(load)
</script>
