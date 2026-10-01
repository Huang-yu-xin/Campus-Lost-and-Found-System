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
        <el-table-column :formatter="statusFormatter" prop="actorType" label="操作者" width="90" />
        <el-table-column :formatter="statusFormatter" prop="action" label="动作" width="170" />
        <el-table-column :formatter="statusFormatter" prop="targetType" label="对象" width="100" />
        <el-table-column prop="targetId" label="ID" width="90" />
        <el-table-column :formatter="statusFormatter" prop="result" label="结果" width="90" />
        <el-table-column prop="createdAt" label="时间" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { label, statusFormatter } from '../utils/labels'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api'

const stats = reactive({ active: 0, handover: 0, openDisputes: 0, backups: 0, lastBackup: '-' })
const recent = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  // E30：Promise.allSettled 部分降级——单卡片失败显示"—"，不整页失败
  const results = await Promise.allSettled([
    adminApi.listPosts({ status: 'ACTIVE', page: 1, pageSize: 1 }),
    adminApi.listPosts({ status: 'HANDOVER', page: 1, pageSize: 1 }),
    adminApi.listDisputes({ status: 'OPEN', page: 1, pageSize: 1 }),
    adminApi.backups({ page: 1, pageSize: 1 }),
    adminApi.auditLogs({ page: 1, pageSize: 10 })
  ])
  const pick = (r, f) => (r.status === 'fulfilled' ? f(r.value) : '—')
  stats.active = pick(results[0], v => v.total ?? 0)
  stats.handover = pick(results[1], v => v.total ?? 0)
  stats.openDisputes = pick(results[2], v => v.total ?? 0)
  stats.backups = pick(results[3], v => v.total ?? 0)
  // E3：最新备份显示完成时间，未完成则回退显示状态
  stats.lastBackup = pick(results[3], v => {
    const it = v.items && v.items[0]
    return it ? (it.finishedAt || label(it.status)) : '-'
  })
  recent.value = results[4].status === 'fulfilled' ? (results[4].value.items || []) : []
  if (results.some(r => r.status === 'rejected')) {
    ElMessage.error('部分数据加载失败')
  }
  loading.value = false
}
onMounted(load)
</script>

<style scoped>
.k { color: #909399; font-size: 13px; }
.v { font-size: 30px; font-weight: 700; margin-top: 6px; }
.v.warn { color: #e6a23c; }
.sub { color: #c0c4cc; font-size: 12px; margin-top: 4px; }
</style>
