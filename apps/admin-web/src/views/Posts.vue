<template>
  <div>
    <h3>信息治理</h3>
    <el-form :inline="true">
      <el-form-item label="状态">
        <el-select v-model="status" clearable placeholder="全部" style="width:140px" @change="load">
          <el-option label="ACTIVE" value="ACTIVE" />
          <el-option label="HANDOVER" value="HANDOVER" />
          <el-option label="COMPLETED" value="COMPLETED" />
          <el-option label="REMOVED" value="REMOVED" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="type" clearable placeholder="全部" style="width:120px" @change="load">
          <el-option label="LOST" value="LOST" />
          <el-option label="FOUND" value="FOUND" />
        </el-select>
      </el-form-item>
      <el-button @click="load">刷新</el-button>
    </el-form>

    <el-table :data="items" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="type" label="类型" width="90" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="category" label="类别" width="110" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button v-if="row.status !== 'REMOVED'" size="small" type="danger" @click="remove(row)">下架</el-button>
          <el-button v-else size="small" type="success" @click="restore(row)">恢复</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api'

const items = ref([])
const loading = ref(false)
const status = ref('')
const type = ref('')

async function load() {
  loading.value = true
  try {
    const data = await adminApi.listPosts({ status: status.value, type: type.value, page: 1, pageSize: 50 })
    items.value = data.items || []
  } catch (e) { ElMessage.error(e?.message || '加载失败') } finally { loading.value = false }
}

async function remove(row) {
  const { value } = await ElMessageBox.prompt('下架理由', '下架', { inputValidator: (v) => !!v || '请填写理由' })
  await adminApi.removePost(row.id, value)
  ElMessage.success('已下架')
  load()
}
async function restore(row) {
  const { value } = await ElMessageBox.prompt('恢复理由', '恢复', { inputValidator: (v) => !!v || '请填写理由' })
  await adminApi.restorePost(row.id, value)
  ElMessage.success('已恢复')
  load()
}

onMounted(load)
</script>
