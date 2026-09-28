<template>
  <div>
    <h3>审计与维护</h3>
    <el-tabs v-model="tab">
      <el-tab-pane label="审计日志" name="audit">
        <el-button size="small" @click="loadAudit" style="margin-bottom:10px">刷新</el-button>
        <el-table :data="logs" v-loading="loadingA" border>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="actorType" label="操作者" width="90" />
          <el-table-column prop="action" label="动作" width="160" />
          <el-table-column prop="targetType" label="对象" width="100" />
          <el-table-column prop="targetId" label="对象ID" width="90" />
          <el-table-column prop="result" label="结果" width="90" />
          <el-table-column prop="createdAt" label="时间" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="备份记录" name="backup">
        <el-button size="small" type="primary" @click="runBackup" :loading="running" style="margin-bottom:10px">触发备份</el-button>
        <el-button size="small" @click="loadBackups" style="margin-bottom:10px">刷新</el-button>
        <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px"
          title="恢复通过运维离线脚本执行（docs/operations/backup-restore.md），后台不提供一键生产恢复。" />
        <el-table :data="backups" v-loading="loadingB" border>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column prop="checksum" label="校验和" />
          <el-table-column prop="startedAt" label="开始" />
          <el-table-column prop="finishedAt" label="完成" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api'

const tab = ref('audit')
const logs = ref([]); const loadingA = ref(false)
const backups = ref([]); const loadingB = ref(false)
const running = ref(false)

async function loadAudit() {
  loadingA.value = true
  try { logs.value = (await adminApi.auditLogs({ page: 1, pageSize: 50 })).items || [] }
  catch (e) { ElMessage.error(e?.message || '加载失败') } finally { loadingA.value = false }
}
async function loadBackups() {
  loadingB.value = true
  try { backups.value = (await adminApi.backups({ page: 1, pageSize: 50 })).items || [] }
  catch (e) { ElMessage.error(e?.message || '加载失败') } finally { loadingB.value = false }
}
async function runBackup() {
  running.value = true
  try { await adminApi.runBackup(); ElMessage.success('备份已触发'); loadBackups() }
  catch (e) { ElMessage.error(e?.message || '备份失败') } finally { running.value = false }
}
onMounted(() => { loadAudit(); loadBackups() })
</script>
