<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { Reading, ArrowDown } from '@element-plus/icons-vue'

const route = useRoute()
const userJson = ref(localStorage.getItem('user-center-current-user'))

// Sync userJson whenever localStorage might change (optional for simple apps, but good for robust reactivity)
watch(() => route.path, () => {
  userJson.value = localStorage.getItem('user-center-current-user')
})

const currentUser = computed(() => {
  try {
    return userJson.value ? JSON.parse(userJson.value) : null
  } catch {
    return null
  }
})

const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')

const router = useRouter()
function handleLogout() {
  localStorage.removeItem('user-center-current-user')
  userJson.value = null
  router.push('/')
}
</script>

<template>
  <div class="app-shell">
    <el-header v-if="route.path !== '/' && !isAdmin" class="main-header">
      <div class="header-inner">
        <div class="brand">
          <el-icon :size="20"><Reading /></el-icon>
          <span>个性化学习资源平台</span>
        </div>
        
        <el-menu
          :default-active="route.path"
          mode="horizontal"
          router
          class="nav-menu"
        >
          <el-menu-item index="/user-center">用户中心</el-menu-item>
          <el-menu-item index="/resource-center">资源管理</el-menu-item>
          <el-menu-item index="/community-navigation">社区分类</el-menu-item>
          <el-menu-item index="/community-participation">社区参与</el-menu-item>
          <el-menu-item index="/recommendation">为您推荐</el-menu-item>
          <el-menu-item index="/interaction-center">下载收藏</el-menu-item>
        </el-menu>

        <div class="user-ops">
          <el-dropdown>
            <span class="el-dropdown-link">
              {{ currentUser?.username || '用户' }}
              <el-icon class="el-icon--right"><arrow-down /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/user-center')">个人资料</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </el-header>

    <div class="app-body">
      <RouterView />
    </div>
  </div>
</template>

<style>
/* Global resets for Element Plus layout */
body {
  margin: 0;
  font-family: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif;
}
</style>

<style scoped>
.main-header {
  background-color: #fff;
  border-bottom: 1px solid #dcdfe6;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 1000;
  height: 60px;
}

.header-inner {
  max-width: 1400px;
  margin: 0 auto;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 700;
  font-size: 16px;
  white-space: nowrap;
}

.nav-menu {
  flex: 1;
  border-bottom: none;
  margin: 0 40px;
  height: 60px;
}

.user-ops {
  cursor: pointer;
}

.el-dropdown-link {
  display: flex;
  align-items: center;
  color: #409EFF;
  font-weight: 600;
}

.app-body {
  background-color: #f5f7fa;
  min-height: calc(100vh - 60px);
}
</style>
