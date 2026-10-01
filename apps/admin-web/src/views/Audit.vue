<template>
  <div>
    <h3>审计与维护</h3>
    <el-tabs v-model="tab">
      <el-tab-pane label="审计日志" name="audit">
        <el-form :inline="true">
          <el-form-item label="动作">
            <el-select v-model="action" clearable placeholder="全部" style="width:200px" @change="reloadAudit">
              <el-option v-for="a in actions" :key="a" :label="a" :value="a" />
            </el-select>
          </el-form-item>
          <el-form-item label="对象">
            <el-select v-model="targetType" clearable placeholder="全部" style="width:150px" @change="reloadAudit">
              <el-option v-for="t in targets" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-button @click="loadAudit">查询</el-button>
        </el-form>
        <el-table :data="logs" v-loading="loadingA" border>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="actorType" label="操作者" width="90" />
          <el-table-column prop="action" label="动作" width="170" />
          <el-table-column prop="targetType" label="对象" width="100" />
          <el-table-column prop="targetId" label="对象ID" width="90" />
          <el-table-column prop="result" label="结果" width="90" />
          <el-table-column prop="createdAt" label="时间" />
        </el-table>
        <el-pagination style="margin-top:10px" layout="prev, pager, next, total" :total="totalA"
          :page-size="pageSize" :current-page="pageA" @current-change="onPageA" />
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
const actions = ['CLAIM_REVIEW', 'DISPUTE_RAISE', 'DISPUTE_ASSIGN', 'DISPUTE_RESOLVE', 'POST_REMOVE', 'POST_RESTORE', 'USER_RESTRICT', 'USER_UNRESTRICT', 'BACKUP_RUN', 'LOST_RESOLVED', 'HANDOVER_CANCELLED', 'LOST_MARK_FOUND', 'LEAD_REVIEW']
const targets = ['POST', 'USER', 'CLAIM', 'DISPUTE', 'BACKUP', 'LEAD']
const action = ref('')
const targetType = ref('')
const logs = ref([]); const loadingA = ref(false); const totalA = ref(0); const pageA = ref(1); const pageSize = 20
const backups = ref([]); const loadingB = ref(false)
const running = ref(false)

async function loadAudit() {
  loadingA.value = true
  try {
    const d = await adminApi.auditLogs({ action: action.value, targetType: targetType.value, page: pageA.value, pageSize })
    logs.value = d.items || []; totalA.value = d.total || 0
  } catch (e) { ElMessage.error(e?.message || '加载失败') } finally { loadingA.value = false }
}
function onPageA(p) { pageA.value = p; loadAudit() }
// E1：切换筛选时重置页码再查询
function reloadAudit() { pageA.value = 1; loadAudit() }
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
