<template>
  <div>
    <h3>用户治理</h3>
    <el-form :inline="true">
      <el-form-item label="昵称">
        <el-input v-model="keyword" clearable placeholder="搜索昵称" @keyup.enter="reload" />
      </el-form-item>
      <el-button @click="reload">搜索</el-button>
    </el-form>

    <el-table :data="items" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="campus" label="校区" width="120" />
      <el-table-column :formatter="userStatusFormatter" prop="status" label="状态" width="120" />
      <el-table-column :formatter="statusFormatter" prop="campusVerificationStatus" label="校园认证" width="120" />
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button size="small" @click="router.push({ path:'/audit', query:{targetType:'USER', targetId:row.id} })">审计记录</el-button>
          <el-button v-if="row.status !== 'RESTRICTED'" size="small" type="warning" @click="restrict(row)">限制</el-button>
          <el-button v-else size="small" type="success" @click="unrestrict(row)">解除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:10px" layout="prev, pager, next, total" :total="total"
      :page-size="pageSize" :current-page="page" @current-change="onPage" />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
const userStatusFormatter = (row, column, value) => value === 'ACTIVE' ? '正常' : label(value)
import { label, statusFormatter } from '../utils/labels'
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api'

const items = ref([])
const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const total = ref(0)
const pageSize = 20

async function load() {
  loading.value = true
  try {
    const data = await adminApi.listUsers({ keyword: keyword.value, page: page.value, pageSize })
    items.value = data.items || []
    total.value = data.total || 0
  } catch (e) { ElMessage.error(e?.message || '加载失败') } finally { loading.value = false }
}
function reload() { page.value = 1; load() }
function onPage(p) { page.value = p; load() }
async function restrict(row) {
  try {
    const { value } = await ElMessageBox.prompt('限制理由', '限制用户', { inputValidator: (v) => !!v || '请填写理由' })
    await adminApi.restrictUser(row.id, value)
    ElMessage.success('已限制'); load()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '限制失败')
  }
}
async function unrestrict(row) {
  try {
    const { value } = await ElMessageBox.prompt('解除理由', '解除限制', { inputValidator: (v) => !!v || '请填写理由' })
    await adminApi.unrestrictUser(row.id, value)
    ElMessage.success('已解除'); load()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '解除失败')
  }
}
onMounted(load)
</script>
