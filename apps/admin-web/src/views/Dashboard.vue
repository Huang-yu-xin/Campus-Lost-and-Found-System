<template>
  <div>
    <h3>后台总览</h3>
    <el-row :gutter="16">
      <el-col :span="6"><el-card shadow="hover"><div class="k">进行中发布</div><div class="v">{{ stats.active }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover"><div class="k">交接中</div><div class="v">{{ stats.handover }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover"><div class="k">未决争议</div><div class="v warn">{{ stats.openDisputes }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover"><div class="k">备份记录</div><div class="v">{{ stats.backups }}</div><div class="sub">最新：{{ stats.lastBackup }}</div></el-card></el-col>
    </el-row>

    <el-card style="margin-top:16px">
      <template #header>最近操作动态</template>
      <el-table :data="recent" v-loading="loading" size="small">
        <el-table-column prop="actorType" label="操作者" width="90" />
        <el-table-column prop="action" label="动作" width="170" />
        <el-table-column prop="targetType" label="对象" width="100" />
        <el-table-column prop="targetId" label="ID" width="90" />
        <el-table-column prop="result" label="结果" width="90" />
        <el-table-column prop="createdAt" label="时间" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api'

const stats = reactive({ active: 0, handover: 0, openDisputes: 0, backups: 0, lastBackup: '-' })
const recent = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [a, h, d, b, logs] = await Promise.all([
      adminApi.listPosts({ status: 'ACTIVE', page: 1, pageSize: 1 }),
      adminApi.listPosts({ status: 'HANDOVER', page: 1, pageSize: 1 }),
      adminApi.listDisputes({ status: 'OPEN', page: 1, pageSize: 1 }),
      adminApi.backups({ page: 1, pageSize: 1 }),
      adminApi.auditLogs({ page: 1, pageSize: 10 })
    ])
    stats.active = a.total ?? 0
    stats.handover = h.total ?? 0
    stats.openDisputes = d.total ?? 0
    stats.backups = b.total ?? 0
    stats.lastBackup = (b.items && b.items[0]) ? b.items[0].status : '-'
    recent.value = logs.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.k { color: #909399; font-size: 13px; }
.v { font-size: 30px; font-weight: 700; margin-top: 6px; }
.v.warn { color: #e6a23c; }
.sub { color: #c0c4cc; font-size: 12px; margin-top: 4px; }
</style>
