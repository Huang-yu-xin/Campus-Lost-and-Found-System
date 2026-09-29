<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2>校园失物招领 · 管理后台</h2>
      <el-form @submit.prevent>
        <el-form-item label="账号">
          <el-input v-model="username" placeholder="admin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password @keyup.enter="login" />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="login" style="width:100%">登录</el-button>
      </el-form>
      <p class="hint">账号密码由 deploy/.env 的 ADMIN_BOOTSTRAP_* 在开发环境种子生成。</p>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'

const username = ref('admin')
const password = ref('')
const loading = ref(false)
const router = useRouter()

async function login() {
  loading.value = true
  try {
    const data = await authApi.login(username.value, password.value)
    localStorage.setItem('clf_admin_token', data.accessToken)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    ElMessage.error(e?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap { display: flex; justify-content: center; align-items: center; height: 100%; background: #f5f7fa; }
.login-card { width: 380px; }
.hint { color: #909399; font-size: 12px; margin-top: 12px; }
</style>
