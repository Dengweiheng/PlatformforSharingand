<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  auditResource,
  dashboardOverview,
  listPendingResources,
  listUserActions,
  listUsers,
  snapshotDashboard,
  updateUserStatus,
} from '@/api/adminApi'
import {
  listCommunityPosts,
  deleteCommunityPost,
  listPostComments,
  deleteCommunityComment,
} from '@/api/communityApi'
import { 
  User, 
  Files, 
  DataAnalysis, 
  Search, 
  Refresh, 
  Check, 
  Close, 
  Delete, 
  Timer,
  InfoFilled,
  Monitor,
  SwitchButton,
  ChatLineRound,
  Comment
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import { useRouter } from 'vue-router'

const router = useRouter()
const activeMenu = ref('users')
const drawerVisible = ref(false)

function logout() {
  localStorage.removeItem('user-center-current-user')
  router.push('/')
}

const userState = reactive({
  loading: false,
  status: '',
  keyword: '',
  users: [],
  selectedUserId: '',
  actionLoading: false,
  actions: [],
})

const auditState = reactive({
  loading: false,
  pendingResources: [],
  adminId: readUserCache()?.id || '',
  remarkMap: {},
})

const dashboardState = reactive({
  loading: false,
  snapshotLoading: false,
  stat: null,
  queryDate: '',
})

const communityState = reactive({
  loading: false,
  keyword: '',
  posts: [],
  selectedPostId: null,
  comments: [],
  commentLoading: false,
})

const commentDrawerVisible = ref(false)
const datePickerWrapperRef = ref(null)

function readUserCache() {
  const raw = localStorage.getItem('user-center-current-user')
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function normalizeError(error) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

async function loadUsers() {
  userState.loading = true
  try {
    const params = {
      status: userState.status,
      keyword: userState.keyword,
    }
    userState.users = await listUsers(params)
  } catch (error) {
    ElMessage.error(`用户列表加载失败：${normalizeError(error)}`)
  } finally {
    userState.loading = false
  }
}

async function handleToggleUserStatus(user) {
  const nextStatus = user.status === 1 ? 0 : 1
  try {
    await updateUserStatus(user.id, nextStatus)
    ElMessage.success(`用户 ${user.username} 状态已更新为 ${nextStatus === 1 ? '正常' : '封禁'}`)
    await loadUsers()
  } catch (error) {
    ElMessage.error(normalizeError(error))
  }
}

async function loadUserActions(userId) {
  userState.selectedUserId = userId
  userState.actionLoading = true
  drawerVisible.value = true
  try {
    userState.actions = await listUserActions(userId, 100)
  } catch (error) {
    ElMessage.error(`行为日志加载失败：${normalizeError(error)}`)
  } finally {
    userState.actionLoading = false
  }
}

async function loadPendingResources() {
  auditState.loading = true
  try {
    auditState.pendingResources = await listPendingResources()
  } catch (error) {
    ElMessage.error(`待审核资源加载失败：${normalizeError(error)}`)
  } finally {
    auditState.loading = false
  }
}

async function handleAudit(resourceId, result) {
  const adminId = Number(auditState.adminId)
  if (!Number.isInteger(adminId) || adminId <= 0) {
    ElMessage.warning('请先输入有效管理员ID')
    return
  }
  try {
    await auditResource(resourceId, adminId, result, auditState.remarkMap[resourceId] || '')
    ElMessage.success(`审核操作成功：${result}`)
    await loadPendingResources()
    await loadDashboard()
  } catch (error) {
    ElMessage.error(normalizeError(error))
  }
}

function getRawDateInputValue() {
  const inputEl = datePickerWrapperRef.value?.querySelector('input')
  return inputEl ? inputEl.value.trim() : ''
}

async function loadDashboard(date = '') {
  if (date) {
    const datePattern = /^\d{4}-\d{2}-\d{2}$/
    if (!datePattern.test(date)) {
      ElMessage.error('日期格式错误，请使用 yyyy-MM-dd')
      return
    }
  }

  dashboardState.loading = true
  try {
    dashboardState.stat = await dashboardOverview(date)
  } catch (error) {
    const msg = normalizeError(error)
    if (msg.includes('日期格式')) {
      ElMessage.error(msg)
    } else {
      ElMessage.error('统计数据获取失败，请稍后重试')
    }
  } finally {
    dashboardState.loading = false
  }
}

async function handleQuery() {
  const rawValue = getRawDateInputValue()
  if (rawValue) {
    const datePattern = /^\d{4}-\d{2}-\d{2}$/
    if (!datePattern.test(rawValue)) {
      ElMessage.error('日期格式错误，请使用 yyyy-MM-dd')
      return
    }
    await loadDashboard(rawValue)
  } else {
    await loadDashboard(dashboardState.queryDate || '')
  }
}

async function resetQuery() {
  dashboardState.queryDate = ''
  await loadDashboard('')
}

async function handleSaveSnapshot() {
  const rawValue = getRawDateInputValue()
  const date = rawValue || dashboardState.queryDate
  if (!date) {
    ElMessage.warning('请先输入或选择日期')
    return
  }
  const datePattern = /^\d{4}-\d{2}-\d{2}$/
  if (!datePattern.test(date)) {
    ElMessage.error('日期格式错误，请使用 yyyy-MM-dd')
    return
  }

  dashboardState.snapshotLoading = true
  try {
    dashboardState.stat = await snapshotDashboard(date)
    ElMessage.success('快照保存成功')
  } catch (error) {
    const msg = normalizeError(error)
    if (msg.includes('日期格式')) {
      ElMessage.error(msg)
    } else {
      ElMessage.error('快照保存失败，请稍后重试')
    }
  } finally {
    dashboardState.snapshotLoading = false
  }
}

async function loadCommunityPosts() {
  communityState.loading = true
  try {
    communityState.posts = await listCommunityPosts({
      keyword: communityState.keyword,
      limit: 100
    })
  } catch (error) {
    ElMessage.error(`讨论列表加载失败：${normalizeError(error)}`)
  } finally {
    communityState.loading = false
  }
}

async function handleDeletePost(postId) {
  try {
    await deleteCommunityPost(postId, readUserCache()?.id)
    ElMessage.success('讨论已删除')
    await loadCommunityPosts()
  } catch (error) {
    ElMessage.error(normalizeError(error))
  }
}

async function loadPostComments(postId) {
  communityState.selectedPostId = postId
  communityState.commentLoading = true
  commentDrawerVisible.value = true
  try {
    communityState.comments = await listPostComments(postId)
  } catch (error) {
    ElMessage.error(`评论加载失败：${normalizeError(error)}`)
  } finally {
    communityState.commentLoading = false
  }
}

async function handleDeleteComment(commentId) {
  try {
    await deleteCommunityComment(commentId, readUserCache()?.id)
    ElMessage.success('评论已删除')
    await loadPostComments(communityState.selectedPostId)
    await loadCommunityPosts()
  } catch (error) {
    ElMessage.error(normalizeError(error))
  }
}

onMounted(async () => {
  await Promise.all([loadUsers(), loadPendingResources(), loadDashboard(), loadCommunityPosts()])
})
</script>

<template>
  <div class="common-layout">
    <el-header class="admin-header">
      <div class="header-content">
        <div class="logo">
          <el-icon :size="20"><Monitor /></el-icon>
          <span>系统运营管理后台</span>
        </div>
        <div class="admin-info">
          <el-badge is-dot class="item">
             <el-avatar :size="32" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
          </el-badge>
          <span class="admin-name">{{ readUserCache()?.username || '管理员' }}</span>
          <el-divider direction="vertical" />
          <el-button type="info" link @click="logout" :icon="SwitchButton">退出登录</el-button>
        </div>
      </div>
    </el-header>

    <el-container class="admin-container">
      <el-aside width="220px">
        <el-menu
          :default-active="activeMenu"
          class="admin-menu"
          @select="(key) => activeMenu = key"
        >
          <el-menu-item index="users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="audit">
            <el-icon><Files /></el-icon>
            <span>资源审核</span>
            <el-badge 
              v-if="auditState.pendingResources.length" 
              :value="auditState.pendingResources.length" 
              class="badge-dot" 
              type="danger"
            />
          </el-menu-item>
          <el-menu-item index="dashboard">
            <el-icon><DataAnalysis /></el-icon>
            <span>数据看板</span>
          </el-menu-item>
          <el-menu-item index="community">
            <el-icon><ChatLineRound /></el-icon>
            <span>圈子管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main>
        <!-- Users Management -->
        <div v-if="activeMenu === 'users'">
          <el-card shadow="never" class="table-card">
            <template #header>
              <div class="card-header">
                <span>用户列表</span>
                <div class="header-ops">
                  <el-select v-model="userState.status" placeholder="用户状态" clearable style="width: 120px">
                    <el-option label="正常" value="1" />
                    <el-option label="封禁" value="0" />
                  </el-select>
                  <el-input
                    v-model="userState.keyword"
                    placeholder="关键词搜索"
                    style="width: 200px"
                    :prefix-icon="Search"
                    clearable
                  />
                  <el-button type="primary" :icon="Search" @click="loadUsers">查询</el-button>
                </div>
              </div>
            </template>

            <el-table v-loading="userState.loading" :data="userState.users" style="width: 100%">
              <el-table-column prop="username" label="用户名" />
              <el-table-column prop="email" label="邮箱" />
              <el-table-column prop="phone" label="手机号" />
              <el-table-column label="状态" width="100">
                <template #default="scope">
                  <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
                    {{ scope.row.status === 1 ? '正常' : '封禁' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="220" fixed="right">
                <template #default="scope">
                  <el-button 
                    size="small" 
                    :type="scope.row.status === 1 ? 'danger' : 'success'" 
                    @click="handleToggleUserStatus(scope.row)"
                  >
                    {{ scope.row.status === 1 ? '封禁' : '解封' }}
                  </el-button>
                  <el-button 
                    size="small" 
                    type="info" 
                    plain
                    @click="loadUserActions(scope.row.id)"
                  >
                    行为日志
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-drawer
            v-model="drawerVisible"
            title="用户行为日志"
            direction="rtl"
            size="45%"
          >
            <el-timeline v-loading="userState.actionLoading">
              <el-timeline-item
                v-for="action in userState.actions"
                :key="action.id"
                :timestamp="action.createdAt"
                placement="top"
              >
                <el-card shadow="hover">
                  <h4>{{ action.actionType }}</h4>
                  <p>资源 ID: {{ action.resourceId }} | 评分: {{ action.score || '无' }}</p>
                </el-card>
              </el-timeline-item>
              <el-empty v-if="!userState.actions.length" description="暂无记录" />
            </el-timeline>
          </el-drawer>
        </div>

        <!-- Resource Audit -->
        <div v-else-if="activeMenu === 'audit'">
          <el-card shadow="never" class="table-card">
            <template #header>
              <div class="card-header">
                <span>待审核资源</span>
                <div class="header-ops">
                   <el-input
                    v-model.number="auditState.adminId"
                    type="number"
                    placeholder="当前管理员ID"
                    style="width: 150px"
                  />
                  <el-button :icon="Refresh" @click="loadPendingResources">刷新</el-button>
                </div>
              </div>
            </template>

            <el-table v-loading="auditState.loading" :data="auditState.pendingResources" style="width: 100%">
              <el-table-column type="expand">
                <template #default="props">
                  <div style="padding: 20px">
                    <p><strong>摘要：</strong>{{ props.row.summary }}</p>
                    <p><strong>附件：</strong>{{ props.row.fileUrl }}</p>
                    <el-input
                      v-model="auditState.remarkMap[props.row.id]"
                      type="textarea"
                      placeholder="审核备注"
                      style="margin-top: 10px"
                    />
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="title" label="标题" min-width="150" />
              <el-table-column prop="subjectName" label="学科" width="120" />
              <el-table-column prop="examType" label="类型" width="100" />
              <el-table-column label="上传者" width="100">
                <template #default="scope">
                  ID: {{ scope.row.uploaderId }}
                </template>
              </el-table-column>
              <el-table-column label="审核操作" width="280" fixed="right">
                <template #default="scope">
                  <el-button-group>
                    <el-button type="success" size="small" :icon="Check" @click="handleAudit(scope.row.id, 'PASSED')">通过</el-button>
                    <el-button type="warning" size="small" :icon="Close" @click="handleAudit(scope.row.id, 'REJECTED')">驳回</el-button>
                    <el-button type="danger" size="small" :icon="Delete" @click="handleAudit(scope.row.id, 'DELETED')">删除</el-button>
                  </el-button-group>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>

        <!-- Dashboard -->
        <div v-else-if="activeMenu === 'dashboard'">
          <div class="dashboard-header">
            <h2>运营核心指标 <span v-if="dashboardState.stat?.statDate" style="font-size: 14px; color: #909399; font-weight: normal;">({{ dashboardState.stat.statDate }})</span></h2>
            <div ref="datePickerWrapperRef" class="snapshot-tool" style="display: flex; gap: 10px; align-items: center;">
              <el-date-picker
                v-model="dashboardState.queryDate"
                type="date"
                placeholder="选择日期"
                value-format="YYYY-MM-DD"
                format="YYYY-MM-DD"
                style="width: 180px"
                clearable
              />
              <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
              <el-button type="success" :loading="dashboardState.snapshotLoading" @click="handleSaveSnapshot">保存快照</el-button>
              <el-button :icon="Refresh" circle @click="resetQuery" />
            </div>
          </div>

          <el-row :gutter="20" v-if="dashboardState.stat">
            <el-col :span="6">
              <el-card shadow="hover" class="stat-card">
                <div class="stat-info">
                  <div class="label">注册用户数</div>
                  <div class="value">{{ dashboardState.stat.registerUserCount }}</div>
                </div>
                <el-icon class="stat-icon" color="#409EFF"><User /></el-icon>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="stat-card">
                <div class="stat-info">
                  <div class="label">资源总数</div>
                  <div class="value">{{ dashboardState.stat.totalResourceCount }}</div>
                </div>
                <el-icon class="stat-icon" color="#67C23A"><Files /></el-icon>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="stat-card">
                <div class="stat-info">
                  <div class="label">日活跃用户</div>
                  <div class="value">{{ dashboardState.stat.dailyActiveUserCount }}</div>
                </div>
                <el-icon class="stat-icon" color="#E6A23C"><Timer /></el-icon>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="stat-card">
                <div class="stat-info">
                  <div class="label">推荐点击率</div>
                  <div class="value">{{ dashboardState.stat.recClickRate }}%</div>
                </div>
                <el-icon class="stat-icon" color="#F56C6C"><DataAnalysis /></el-icon>
              </el-card>
            </el-col>
          </el-row>
          <el-empty v-else description="暂无统计数据" />
        </div>

        <!-- Community Management -->
        <div v-else-if="activeMenu === 'community'">
          <el-card shadow="never" class="table-card">
            <template #header>
              <div class="card-header">
                <span>讨论内容列表</span>
                <div class="header-ops">
                  <el-input
                    v-model="communityState.keyword"
                    placeholder="标题/内容搜索"
                    style="width: 250px"
                    :prefix-icon="Search"
                    clearable
                    @keyup.enter="loadCommunityPosts"
                  />
                  <el-button type="primary" :icon="Search" @click="loadCommunityPosts">查询</el-button>
                </div>
              </div>
            </template>

            <el-table v-loading="communityState.loading" :data="communityState.posts" style="width: 100%">
              <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
              <el-table-column prop="username" label="发布人" width="120" />
              <el-table-column prop="commentCount" label="评论数" width="100" />
              <el-table-column prop="createdAt" label="发布时间" width="180" />
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="scope">
                  <el-button size="small" type="primary" plain @click="loadPostComments(scope.row.id)">查看评论</el-button>
                  <el-popconfirm title="确定删除这篇讨论吗？" @confirm="handleDeletePost(scope.row.id)">
                    <template #reference>
                      <el-button size="small" type="danger">删除</el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-drawer
            v-model="commentDrawerVisible"
            title="帖子详情与评论"
            direction="rtl"
            size="45%"
          >
            <div v-loading="communityState.commentLoading">
              <div class="comment-item-admin" v-for="c in communityState.comments" :key="c.id">
                <div class="comment-info">
                  <p class="comment-content">{{ c.content }}</p>
                  <div class="comment-bottom">
                    <span>{{ c.username }} @ {{ c.createdAt }}</span>
                    <el-button type="danger" link @click="handleDeleteComment(c.id)">删除评论</el-button>
                  </div>
                </div>
              </div>
              <el-empty v-if="!communityState.comments.length" description="暂无评论" />
            </div>
          </el-drawer>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<style scoped>
.common-layout {
  min-height: 100vh;
  background-color: #f5f7fa;
}

.admin-header {
  background-color: #fff;
  border-bottom: 1px solid #dcdfe6;
  padding: 0 24px;
  height: 60px;
}

.header-content {
  height: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}

.admin-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-name {
  font-size: 14px;
  color: #606266;
}

.admin-container {
  height: calc(100vh - 60px);
}

.admin-menu {
  height: 100%;
  border-right: none;
}

.table-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-ops {
  display: flex;
  gap: 12px;
}

.badge-dot {
  margin-left: 8px;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.snapshot-tool {
  display: flex;
  gap: 12px;
  align-items: center;
}

.stat-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px;
}

.stat-info .label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-info .value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.stat-icon {
  font-size: 48px;
  opacity: 0.15;
}

:deep(.el-timeline) {
  padding-left: 10px;
}

.comment-item-admin {
  padding: 15px;
  border-bottom: 1px solid #ebeef5;
}

.comment-content {
  margin: 0 0 10px;
  font-size: 14px;
  color: #303133;
}

.comment-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #909399;
}
</style>
