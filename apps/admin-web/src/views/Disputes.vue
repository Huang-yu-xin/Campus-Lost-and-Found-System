<template>
  <div>
    <h3>争议处理</h3>
    <el-form :inline="true">
      <el-form-item label="状态">
        <el-select v-model="status" clearable placeholder="全部" style="width:140px" @change="reload">
          <el-option label="OPEN" value="OPEN" />
          <el-option label="RESOLVED" value="RESOLVED" />
          <el-option label="CLOSED" value="CLOSED" />
        </el-select>
      </el-form-item>
      <el-button @click="reload">刷新</el-button>
    </el-form>

    <el-table :data="items" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="claimId" label="申请ID" width="90" />
      <el-table-column prop="reason" label="原因" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column prop="assignedAdminId" label="受理人" width="90" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row.id)">查看/裁决</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:10px" layout="prev, pager, next, total" :total="total"
      :page-size="pageSize" :current-page="page" @current-change="onPage" />

    <el-dialog v-model="dialog" title="争议详情与裁决" width="620px">
      <div v-if="current">
        <p><b>争议ID：</b>{{ current.id }} · <b>申请ID：</b>{{ current.claimId }} · <b>状态：</b>{{ current.status }}</p>
        <p><b>原因：</b>{{ current.reason }}</p>
        <p><b>说明：</b>{{ current.description || '无' }}</p>
        <p><b>受理人：</b>{{ current.assignedAdminId || '未受理' }}</p>

        <el-alert v-if="current.status === 'OPEN' && !assigned" type="warning" :closable="false" show-icon
          title="需先受理本争议，才能查看受限证据并进行裁决。" style="margin:8px 0" />
        <el-button v-if="current.status === 'OPEN' && !assigned" type="primary" size="small" @click="assign">受理此争议</el-button>

        <div style="margin-top:10px">
          <b>证据文件：</b>
          <template v-if="current.evidenceFileIds && current.evidenceFileIds.length">
            <el-button v-for="fid in current.evidenceFileIds" :key="fid" size="small" text type="primary" @click="viewEvidence(fid)">查看#{{ fid }}</el-button>
          </template>
          <span v-else>无</span>
        </div>

        <template v-if="current.status === 'OPEN'">
          <el-form label-width="90px" style="margin-top:12px">
            <el-form-item label="裁决">
              <el-select v-model="resolutionType" style="width:260px">
                <el-option label="继续交接 CONTINUE" value="CONTINUE" />
                <el-option label="终止并重开 TERMINATE_REOPEN" value="TERMINATE_REOPEN" />
                <el-option label="关闭处理 CLOSE" value="CLOSE" />
              </el-select>
            </el-form-item>
            <el-form-item label="裁决理由">
              <el-input v-model="note" type="textarea" />
            </el-form-item>
          </el-form>
          <el-alert :closable="false" type="info" :title="previewText" />
        </template>
      </div>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="current && current.status === 'OPEN'" type="primary" @click="resolve">提交裁决</el-button>
      </template>
    </el-dialog>

    <el-image-viewer v-if="viewerUrl" :url-list="[viewerUrl]" @close="closeViewer" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, fetchFileObjectUrl } from '../api'

const items = ref([])
const loading = ref(false)
const status = ref('OPEN')
const dialog = ref(false)
const current = ref(null)
const resolutionType = ref('CONTINUE')
const note = ref('')
const viewerUrl = ref('')
const page = ref(1)
const total = ref(0)
const pageSize = 20

const assigned = computed(() => current.value && current.value.assignedAdminId != null)
const previewText = computed(() => ({
  CONTINUE: '结果：恢复交接，回到双方确认流程。',
  TERMINATE_REOPEN: '结果：本次交接关闭，招领重新开放可申请。',
  CLOSE: '结果：本次交接关闭，招领标记完成。'
}[resolutionType.value] || ''))

async function load() {
  loading.value = true
  try {
    const data = await adminApi.listDisputes({ status: status.value, page: page.value, pageSize })
    items.value = data.items || []
    total.value = data.total || 0
  } catch (e) { ElMessage.error(e?.message || '加载失败') } finally { loading.value = false }
}
function reload() { page.value = 1; load() }
function onPage(p) { page.value = p; load() }
async function openDetail(id) {
  current.value = await adminApi.getDispute(id)
  resolutionType.value = 'CONTINUE'; note.value = ''
  dialog.value = true
}
async function assign() {
  try {
    await adminApi.assignDispute(current.value.id)
    ElMessage.success('已受理')
    current.value = await adminApi.getDispute(current.value.id)
    load()
  } catch (e) {
    ElMessage.error(e?.message || '受理失败')
  }
}
async function viewEvidence(fid) {
  try {
    closeViewer() // 打开新图前先释放上一张的 objectURL
    viewerUrl.value = await fetchFileObjectUrl(fid)
  } catch (e) {
    ElMessage.error(e.message || '无权查看，请先受理')
  }
}
// E6：关闭预览时释放 objectURL，避免内存泄漏
function closeViewer() {
  if (viewerUrl.value) {
    URL.revokeObjectURL(viewerUrl.value)
    viewerUrl.value = ''
  }
}
async function resolve() {
  // E5：裁决前若未受理给提示
  if (!assigned.value) { ElMessage.warning('请先受理该争议再裁决'); return }
  if (!note.value) { ElMessage.warning('请填写裁决理由'); return }
  try {
    await ElMessageBox.confirm(previewText.value, '确认裁决', { type: 'warning' })
    await adminApi.resolveDispute(current.value.id, resolutionType.value, note.value)
    ElMessage.success('裁决已提交')
    dialog.value = false
    load()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '裁决失败')
  }
}
load()
</script>
