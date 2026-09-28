<template>
  <div>
    <h3>争议处理</h3>
    <el-form :inline="true">
      <el-form-item label="状态">
        <el-select v-model="status" clearable placeholder="全部" style="width:140px" @change="load">
          <el-option label="OPEN" value="OPEN" />
          <el-option label="RESOLVED" value="RESOLVED" />
          <el-option label="CLOSED" value="CLOSED" />
        </el-select>
      </el-form-item>
      <el-button @click="load">刷新</el-button>
    </el-form>

    <el-table :data="items" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="claimId" label="申请ID" width="90" />
      <el-table-column prop="reason" label="原因" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row.id)">查看/裁决</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="争议详情与裁决" width="560px">
      <div v-if="current">
        <p><b>争议ID：</b>{{ current.id }} · <b>申请ID：</b>{{ current.claimId }}</p>
        <p><b>原因：</b>{{ current.reason }}</p>
        <p><b>说明：</b>{{ current.description || '无' }}</p>
        <p><b>证据文件：</b>{{ (current.evidenceFileIds || []).join(', ') || '无' }}</p>
        <p><b>状态：</b>{{ current.status }}</p>
        <template v-if="current.status === 'OPEN'">
          <el-form label-width="90px" style="margin-top:12px">
            <el-form-item label="裁决">
              <el-select v-model="resolutionType" style="width:220px">
                <el-option label="继续交接 CONTINUE" value="CONTINUE" />
                <el-option label="终止并重开 TERMINATE_REOPEN" value="TERMINATE_REOPEN" />
                <el-option label="关闭处理 CLOSE" value="CLOSE" />
              </el-select>
            </el-form-item>
            <el-form-item label="裁决理由">
              <el-input v-model="note" type="textarea" />
            </el-form-item>
          </el-form>
        </template>
      </div>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="current && current.status === 'OPEN'" type="primary" @click="resolve">提交裁决</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api'

const items = ref([])
const loading = ref(false)
const status = ref('OPEN')
const dialog = ref(false)
const current = ref(null)
const resolutionType = ref('CONTINUE')
const note = ref('')

async function load() {
  loading.value = true
  try {
    const data = await adminApi.listDisputes({ status: status.value, page: 1, pageSize: 50 })
    items.value = data.items || []
  } catch (e) { ElMessage.error(e?.message || '加载失败') } finally { loading.value = false }
}
async function openDetail(id) {
  current.value = await adminApi.getDispute(id)
  resolutionType.value = 'CONTINUE'
  note.value = ''
  dialog.value = true
}
async function resolve() {
  if (!note.value) { ElMessage.warning('请填写裁决理由'); return }
  await adminApi.resolveDispute(current.value.id, resolutionType.value, note.value)
  ElMessage.success('裁决已提交')
  dialog.value = false
  load()
}
onMounted(load)
</script>
