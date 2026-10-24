<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  getInteractionRecords,
  getUserMessages,
  getUserProfile,
  markMessageRead,
  updateUserProfile,
} from '@/api/userCenterApi'
import { 
  User, 
  Setting, 
  Message, 
  Collection, 
  Download, 
  Edit,
  UserFilled,
  MessageBox,
  Plus
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const activeTab = ref('profile')

const profileForm = reactive({
  userId: null,
  nickname: '',
  avatarUrl: '',
  majorName: '',
  interestTags: '',
})

const interaction = reactive({
  downloadHistory: [],
  favoriteFolders: [],
})

const messages = ref([])
const loading = reactive({
  profile: false,
  actions: false,
  messages: false,
})

const currentUser = ref(readUserCache())
const isLoggedIn = computed(() => Boolean(currentUser.value?.id))
const unreadCount = computed(() => messages.value.filter((item) => item.isRead === 0).length)

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

async function handleSaveProfile() {
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录')
    return
  }

  loading.profile = true
  try {
    await updateUserProfile(profileForm)
    ElMessage.success('个人资料已保存')
    await loadProfile()
  } catch (error) {
    ElMessage.error(normalizeError(error))
  } finally {
    loading.profile = false
  }
}

async function loadProfile() {
  if (!isLoggedIn.value) return
  loading.profile = true
  try {
    const profile = await getUserProfile(currentUser.value.id)
    profileForm.userId = currentUser.value.id
    profileForm.nickname = profile?.nickname || currentUser.value.username || ''
    profileForm.avatarUrl = profile?.avatarUrl || ''
    profileForm.majorName = profile?.majorName || ''
    profileForm.interestTags = profile?.interestTags || ''
  } finally {
    loading.profile = false
  }
}

async function loadInteraction() {
  if (!isLoggedIn.value) return
  loading.actions = true
  try {
    const data = await getInteractionRecords(currentUser.value.id, 30)
    interaction.downloadHistory = data?.downloadHistory || []
    interaction.favoriteFolders = data?.favoriteFolders || []
  } finally {
    loading.actions = false
  }
}

async function loadMessages() {
  if (!isLoggedIn.value) return
  loading.messages = true
  try {
    messages.value = await getUserMessages(currentUser.value.id)
  } finally {
    loading.messages = false
  }
}

async function handleMarkRead(messageId) {
  try {
    await markMessageRead(messageId)
    messages.value = messages.value.map((item) => {
      if (item.id === messageId) {
        return { ...item, isRead: 1 }
      }
      return item
    })
    ElMessage.success('消息已标记为已读')
  } catch (error) {
    ElMessage.error(normalizeError(error))
  }
}

function handleAvatarChange(uploadFile) {
  const file = uploadFile.raw
  if (!file) return
  
  if (!['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)) {
    ElMessage.error('头像只能是图片格式!')
    return false
  }
  if (file.size / 1024 / 1024 > 2) {
    ElMessage.error('头像大小不能超过 2MB!')
    return false
  }

  const reader = new FileReader()
  reader.onload = (e) => {
    profileForm.avatarUrl = e.target.result
  }
  reader.readAsDataURL(file)
}

onMounted(async () => {
  if (isLoggedIn.value) {
    profileForm.userId = currentUser.value.id
    await Promise.all([loadProfile(), loadInteraction(), loadMessages()])
  }
})
</script>

<template>
  <div class="user-center-container">
    <el-row :gutter="20">
      <!-- Left Profile Sidebar -->
      <el-col :span="6">
        <el-card shadow="hover" class="profile-side-card">
          <div class="user-info-brief">
            <el-avatar :size="80" :src="profileForm.avatarUrl || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
            <h2>{{ profileForm.nickname || currentUser?.username || '未登录' }}</h2>
            <el-tag v-if="profileForm.majorName" size="small" effect="plain">{{ profileForm.majorName }}</el-tag>
            <p class="user-id">ID: {{ currentUser?.id || '-' }}</p>
          </div>
          
          <el-divider />
          
          <el-menu
            :default-active="activeTab"
            class="side-menu"
            @select="(key) => activeTab = key"
          >
            <el-menu-item index="profile">
              <el-icon><User /></el-icon>
              <span>个人资料</span>
            </el-menu-item>
            <el-menu-item index="actions">
              <el-icon><Download /></el-icon>
              <span>行为记录</span>
            </el-menu-item>
            <el-menu-item index="messages">
              <el-icon><Message /></el-icon>
              <span>消息通知</span>
              <el-badge v-if="unreadCount > 0" :value="unreadCount" class="msg-badge" type="danger" />
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>

      <!-- Right Content Area -->
      <el-col :span="18">
        <div v-if="activeTab === 'profile'">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <span><el-icon><Edit /></el-icon> 编辑个人资料</span>
              </div>
            </template>
            <el-form :model="profileForm" label-width="100px" v-loading="loading.profile">
              <el-form-item label="个人头像">
                <el-upload
                  class="avatar-uploader"
                  action="#"
                  :show-file-list="false"
                  :auto-upload="false"
                  :on-change="handleAvatarChange"
                >
                  <img v-if="profileForm.avatarUrl" :src="profileForm.avatarUrl" class="avatar" />
                  <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
                </el-upload>
                <div class="upload-tip">点击预览图可重新上传本地图片</div>
              </el-form-item>
              <el-form-item label="昵称">
                <el-input v-model="profileForm.nickname" placeholder="请输入昵称" />
              </el-form-item>
              <el-form-item label="专业领域">
                <el-input v-model="profileForm.majorName" placeholder="例如：计算机科学与技术" />
              </el-form-item>
              <el-form-item label="兴趣标签">
                <el-input v-model="profileForm.interestTags" placeholder="例如：考研, 算法, 英语 (逗号分隔)" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSaveProfile" :loading="loading.profile">保存修改</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </div>

        <div v-else-if="activeTab === 'actions'">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-card shadow="never" class="action-card">
                <template #header>
                  <div class="card-header">
                    <span><el-icon><Download /></el-icon> 下载历史</span>
                  </div>
                </template>
                <el-table :data="interaction.downloadHistory" v-loading="loading.actions" style="width: 100%" height="400">
                  <el-table-column prop="resourceId" label="资源 ID" width="80" />
                  <el-table-column prop="actionType" label="类型" />
                  <el-table-column prop="createdAt" label="时间" min-width="120" />
                </el-table>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="never" class="action-card">
                <template #header>
                  <div class="card-header">
                    <span><el-icon><Collection /></el-icon> 收藏夹</span>
                  </div>
                </template>
                <el-table :data="interaction.favoriteFolders" v-loading="loading.actions" style="width: 100%" height="400">
                  <el-table-column prop="folder.folderName" label="文件夹名称" />
                  <el-table-column label="资源数" width="80">
                    <template #default="scope">
                      {{ scope.row.resources.length }}
                    </template>
                  </el-table-column>
                </el-table>
              </el-card>
            </el-col>
          </el-row>
        </div>

        <div v-else-if="activeTab === 'messages'">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <span><el-icon><MessageBox /></el-icon> 消息中心</span>
              </div>
            </template>
            <el-table :data="messages" v-loading="loading.messages" style="width: 100%">
              <el-table-column label="状态" width="80">
                <template #default="scope">
                   <el-badge is-dot v-if="scope.row.isRead === 0" />
                   <span v-else style="color: #909399">已读</span>
                </template>
              </el-table-column>
              <el-table-column prop="title" label="标题" width="180" />
              <el-table-column prop="content" label="内容" min-width="250" />
              <el-table-column prop="createdAt" label="时间" width="160" />
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="scope">
                  <el-button 
                    v-if="scope.row.isRead === 0" 
                    type="primary" 
                    link 
                    size="small" 
                    @click="handleMarkRead(scope.row.id)"
                  >
                    设为已读
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.user-center-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 100px);
}

.profile-side-card {
  text-align: center;
  padding: 20px 0;
}

.user-info-brief h2 {
  margin: 15px 0 8px;
  color: #303133;
}

.user-id {
  font-size: 13px;
  color: #909399;
  margin-top: 10px;
}

.side-menu {
  border-right: none;
}

.msg-badge {
  margin-left: 10px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}

.action-card {
  height: 100%;
}

.avatar-uploader {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  width: 100px;
  height: 100px;
  transition: border-color 0.3s;
}

.avatar-uploader:hover {
  border-color: #409eff;
}

.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 100px;
  height: 100px;
  text-align: center;
  line-height: 100px;
}

.avatar {
  width: 100px;
  height: 100px;
  display: block;
  object-fit: cover;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
</style>
