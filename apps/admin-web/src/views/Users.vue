<template>
  <div>
    <h3>用户治理</h3>
    <el-form :inline="true">
      <el-form-item label="昵称">
        <el-input v-model="keyword" clearable placeholder="搜索昵称" @keyup.enter="load" />
      </el-form-item>
      <el-button @click="load">搜索</el-button>
    </el-form>

    <el-table :data="items" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="campus" label="校区" width="120" />
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column prop="campusVerificationStatus" label="校园认证" width="120" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button v-if="row.status !== 'RESTRICTED'" size="small" type="warning" @click="restrict(row)">限制</el-button>
          <el-button v-else size="small" type="success" @click="unrestrict(row)">解除</el-button>
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
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    const data = await adminApi.listUsers({ keyword: keyword.value, page: 1, pageSize: 50 })
    items.value = data.items || []
  } catch (e) { ElMessage.error(e?.message || '加载失败') } finally { loading.value = false }
}
async function restrict(row) {
  const { value } = await ElMessageBox.prompt('限制理由', '限制用户', { inputValidator: (v) => !!v || '请填写理由' })
  await adminApi.restrictUser(row.id, value)
  ElMessage.success('已限制'); load()
}
async function unrestrict(row) {
  const { value } = await ElMessageBox.prompt('解除理由', '解除限制', { inputValidator: (v) => !!v || '请填写理由' })
  await adminApi.unrestrictUser(row.id, value)
  ElMessage.success('已解除'); load()
}
onMounted(load)
</script>
