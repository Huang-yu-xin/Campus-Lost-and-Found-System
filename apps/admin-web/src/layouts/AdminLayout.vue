<template>
  <el-container style="height:100vh">
    <el-header style="display:flex;align-items:center;justify-content:space-between;background:#001529;color:#fff">
      <span>校园失物招领 · 管理后台</span>
      <el-button size="small" @click="logout">退出</el-button>
    </el-header>
    <el-container>
      <el-aside width="200px">
        <el-menu :default-active="active" router>
          <el-menu-item index="/posts">信息治理</el-menu-item>
          <el-menu-item index="/users">用户治理</el-menu-item>
          <el-menu-item index="/disputes">争议处理</el-menu-item>
          <el-menu-item index="/audit">审计与维护</el-menu-item>
        </el-menu>
      </el-aside>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const active = computed(() => route.path)

function logout() {
  localStorage.removeItem('clf_admin_token')
  router.push('/login')
}
</script>
