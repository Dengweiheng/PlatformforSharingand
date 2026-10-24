<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import {
  createCommunityPost,
  createPostComment,
  listCommunityPosts,
  listPostComments,
  deleteCommunityPost,
  deleteCommunityComment,
  updateCommunityPost,
} from '@/api/communityApi'
import {
  recordDownload,
  listFolders,
  addResourceToFolder,
  createFolder
} from '@/api/interactionApi'
import { listPublicResources } from '@/api/resourceCenterApi'
import { 
  ChatLineRound, 
  Promotion, 
  Search, 
  Refresh, 
  Download, 
  Star,
  User,
  Memo,
  Plus,
  Delete,
  VideoPlay,
  Edit
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = reactive({
  posts: false,
  comments: false,
  interaction: false,
})

const state = reactive({
  userId: readUserCache()?.id || '',
  role: String(readUserCache()?.role || 'USER').toUpperCase(),
  postType: '',
  keyword: '',
  createForm: {
    postType: 'DISCUSSION',
    score: 0,
    content: '',
    resourceId: '',
  },
})

const posts = ref([])
const comments = ref([])
const folders = ref([])
const publicResources = ref([])
const activePostId = ref(null)
const commentDraft = ref('')

const validUserId = computed(() => Number(state.userId))

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

async function loadPosts() {
  loading.posts = true
  try {
    posts.value = await listCommunityPosts({
      postType: state.postType,
      keyword: state.keyword,
      limit: 50,
    })
  } finally {
    loading.posts = false
  }
}

async function loadFolders() {
  if (!validUserId.value) return
  try {
    folders.value = await listFolders(validUserId.value)
  } catch (error) {
    console.error(error)
  }
}

async function handleCreatePost() {
  if (!validUserId.value) {
    ElMessage.warning('请先登录')
    return
  }
  if (!state.createForm.resourceId) {
    ElMessage.warning('请选择关联资源')
    return
  }
  if (!state.createForm.score || state.createForm.score < 1 || state.createForm.score > 5) {
    ElMessage.warning('请进行评分（1-5星）')
    return
  }
  if (!state.createForm.content.trim()) {
    ElMessage.warning('请填写评价内容')
    return
  }

  try {
    await createCommunityPost({
      userId: validUserId.value,
      postType: state.createForm.postType,
      resourceId: state.createForm.resourceId,
      score: state.createForm.score,
      content: state.createForm.content,
    })
    ElMessage.success('评价发布成功')
    state.createForm.score = 0
    state.createForm.content = ''
    state.createForm.resourceId = ''
    await loadPosts()
  } catch (error) {
    ElMessage.error(normalizeError(error))
  }
}

async function openComments(postId) {
  activePostId.value = postId
  commentDraft.value = ''
  loading.comments = true
  try {
    comments.value = await listPostComments(postId)
  } finally {
    loading.comments = false
  }
}

async function submitComment() {
  if (!validUserId.value || !activePostId.value) {
    ElMessage.warning('请先登录并选择帖子')
    return
  }
  if (!commentDraft.value.trim()) {
    ElMessage.warning('评论内容不能为空')
    return
  }

  try {
    await createPostComment(activePostId.value, { 
      userId: validUserId.value, 
      content: commentDraft.value 
    })
    commentDraft.value = ''
    ElMessage.success('评论提交成功')
    await openComments(activePostId.value)
    await loadPosts()
  } catch (error) {
    ElMessage.error(normalizeError(error))
  }
}

async function handleDownload(resourceId) {
  if (!validUserId.value) {
    ElMessage.warning('请登录后再下载')
    return
  }
  try {
    await recordDownload(resourceId, validUserId.value)
    ElMessage.success('已记录下载行为')
  } catch (error) {
    ElMessage.error(normalizeError(error))
  }
}

async function handleFavorite(resourceId) {
  if (!validUserId.value) {
    ElMessage.warning('请登录后再收藏')
    return
  }
  try {
    await loadFolders()
    let folderId = folders.value[0]?.id
    if (!folderId) {
      const folder = await createFolder(validUserId.value, '我的收藏')
      folderId = folder.id
    }
    await addResourceToFolder(folderId, validUserId.value, resourceId)
    ElMessage.success('已加入收藏夹')
  } catch (error) {
    ElMessage.error(normalizeError(error))
  }
}

async function handlePostDelete(postId) {
  try {
    await ElMessageBox.confirm('确定要删除这条讨论吗？删除后相关的评论也会被清除。', '管理提示', { type: 'warning' })
    await deleteCommunityPost(postId, validUserId.value)
    ElMessage.success('讨论已删除')
    if (activePostId.value === postId) {
      activePostId.value = null
      comments.value = []
    }
    await loadPosts()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(normalizeError(error))
  }
}

async function handleCommentDelete(commentId) {
  try {
    await ElMessageBox.confirm('确定要删除这条评论吗？', '管理提示', { type: 'warning' })
    await deleteCommunityComment(commentId, validUserId.value)
    ElMessage.success('评论已删除')
    await openComments(activePostId.value)
    await loadPosts()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(normalizeError(error))
  }
}

async function fetchResources() {
  try {
    publicResources.value = await listPublicResources()
  } catch (error) {
    console.error('Failed to fetch resources:', error)
  }
}

function getRandomColor(seed) {
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#795548', '#673AB7']
  if (!seed) return colors[0]
  let hash = 0
  for (let i = 0; i < seed.length; i++) {
    hash = seed.charCodeAt(i) + ((hash << 5) - hash)
  }
  return colors[Math.abs(hash) % colors.length]
}

// Edit post (evaluation) logic
const editVisible = ref(false)
const editForm = reactive({
  id: null,
  resourceId: null,
  resourceTitle: '',
  score: 0,
  content: ''
})

function handleEditPost(post) {
  editForm.id = post.id
  editForm.resourceId = post.resourceId
  const res = publicResources.value.find(r => r.id === post.resourceId)
  editForm.resourceTitle = res ? res.title : ('资源ID: ' + post.resourceId)
  editForm.score = post.score || 0
  editForm.content = post.content || ''
  editVisible.value = true
}

async function submitEditPost() {
  if (!editForm.content.trim()) {
    ElMessage.warning('评价内容不能为空')
    return
  }
  if (!editForm.score || editForm.score < 1 || editForm.score > 5) {
    ElMessage.warning('请进行评分')
    return
  }
  try {
    await updateCommunityPost(editForm.id, {
      userId: validUserId.value,
      score: editForm.score,
      content: editForm.content
    })
    ElMessage.success('修改评价成功')
    editVisible.value = false
    await loadPosts()
  } catch (error) {
    ElMessage.error(normalizeError(error))
  }
}

onMounted(async () => {
  await loadPosts()
  await fetchResources()
})
</script>

<template>
  <div class="community-participation-container">
    <el-row :gutter="24">
      <!-- Left side: Discussion List -->
      <el-col :span="14">
        <el-card shadow="never" class="post-list-card">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <el-icon><ChatLineRound /></el-icon>
                <span>社区深度讨论区</span>
              </div>
              <div class="header-ops">
                <el-select v-model="state.postType" placeholder="全部类型" clearable @change="loadPosts" style="width: 120px">
                  <el-option label="普通讨论" value="DISCUSSION" />
                  <el-option label="资源动态" value="RESOURCE_UPDATE" />
                </el-select>
                <el-input 
                  v-model="state.keyword" 
                  placeholder="关键词..." 
                  :prefix-icon="Search" 
                  @keyup.enter="loadPosts"
                  style="width: 180px; margin-left: 10px"
                />
              </div>
            </div>
          </template>

          <div v-loading="loading.posts" class="post-scroll-area">
            <div v-for="post in posts" :key="post.id" class="post-item-modern" :class="{ active: activePostId === post.id }" @click="openComments(post.id)">
              <div class="user-avatar-side">
                <el-avatar :size="40" :style="{ backgroundColor: getRandomColor(post.username) }">
                  {{ post.username?.charAt(0).toUpperCase() }}
                </el-avatar>
              </div>
              
              <div class="post-main-content">
                <div class="post-header-modern">
                  <div class="header-top">
                    <span class="modern-tag" :class="post.postType.toLowerCase()">{{ post.postType === 'DISCUSSION' ? '话题交流' : '资源推荐' }}</span>
                    <span class="post-time-modern">{{ post.createdAt }}</span>
                  </div>
                  <div style="display: flex; align-items: center; gap: 12px; margin-top: 4px;">
                    <h3 class="modern-post-title" style="margin: 0;">{{ post.title }}</h3>
                    <el-rate v-if="post.score" v-model="post.score" disabled show-score score-template="" />
                  </div>
                </div>
                
                <p class="modern-post-body">{{ post.content }}</p>
                
                <div class="modern-post-footer">
                  <div class="footer-left">
                    <span class="user-name-modern">@{{ post.username }}</span>
                    <el-divider direction="vertical" />
                    <span class="stat-item"><el-icon><ChatLineRound /></el-icon> {{ post.commentCount }}</span>
                  </div>
                  
                  <div class="footer-actions" @click.stop v-if="post.resourceId || state.role === 'ADMIN' || post.userId == state.userId">
                    <el-button v-if="post.resourceId" size="small" type="warning" plain round :icon="Star" @click="handleFavorite(post.resourceId)">收藏资源</el-button>
                    <el-button v-if="post.userId == state.userId" size="small" type="primary" link :icon="Edit" @click="handleEditPost(post)">修改</el-button>
                    <el-button v-if="post.userId == state.userId || state.role === 'ADMIN'" size="small" type="danger" link :icon="Delete" @click="handlePostDelete(post.id)">删除</el-button>
                  </div>
                </div>
              </div>
            </div>
            <el-empty v-if="!posts.length && !loading.posts" description="暂无相关讨论，快来发表第一篇吧" />
          </div>
        </el-card>
      </el-col>

      <!-- Right side: Create post & Comments -->
      <el-col :span="10">
        <el-card shadow="never" class="create-card">
          <template #header>
            <div class="card-header"><el-icon><Promotion /></el-icon> 发起新讨论</div>
          </template>
          <el-form :model="state.createForm" label-position="top">
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="话题分类">
                  <el-select v-model="state.createForm.postType" style="width: 100%">
                    <el-option label="普通讨论" value="DISCUSSION" />
                    <el-option label="资源动态" value="RESOURCE_UPDATE" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="关联资源" required>
                  <el-select v-model="state.createForm.resourceId" placeholder="选择关联资源" filterable clearable style="width: 100%">
                    <el-option
                      v-for="res in publicResources"
                      :key="res.id"
                      :label="res.title"
                      :value="res.id"
                    >
                      <span style="float: left">{{ res.title }}</span>
                      <span style="float: right; color: #8492a6; font-size: 13px">ID: {{ res.id }}</span>
                    </el-option>
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="评分" required>
              <el-rate v-model="state.createForm.score" show-text :texts="['极差', '失望', '一般', '满意', '非常推荐']" />
            </el-form-item>
            <el-form-item label="详细内容" required>
              <el-input v-model="state.createForm.content" type="textarea" :rows="4" placeholder="分享你的感悟或对资源的见解..." />
            </el-form-item>
            <el-button type="primary" :icon="Plus" @click="handleCreatePost" style="width: 100%">确认发布</el-button>
          </el-form>
        </el-card>

        <el-card shadow="never" class="comment-card" style="margin-top: 20px">
          <template #header>
            <div class="card-header">
              <span><el-icon><ChatLineRound /></el-icon> 评论互动</span>
              <el-tag v-if="activePostId" size="small" type="info">帖子 #{{ activePostId }}</el-tag>
            </div>
          </template>
          
          <div v-if="activePostId" class="comment-section">
            <div v-loading="loading.comments" class="comment-list">
              <div v-for="c in comments" :key="c.id" class="comment-bubble-container" :class="{ 'my-comment': c.userId == state.userId }">
                <div class="bubble-avatar" v-if="c.userId != state.userId">
                  <el-avatar :size="28">{{ c.username?.charAt(0).toUpperCase() }}</el-avatar>
                </div>
                <div class="comment-bubble">
                  <div class="bubble-info" v-if="c.userId != state.userId">
                    <span class="bubble-user">{{ c.username }}</span>
                    <span class="bubble-time">{{ c.createdAt }}</span>
                  </div>
                  <div class="bubble-content">
                    {{ c.content }}
                    <el-button 
                      v-if="state.role === 'ADMIN'" 
                      class="bubble-delete"
                      type="danger" 
                      link 
                      :icon="Delete" 
                      size="small" 
                      @click="handleCommentDelete(c.id)"
                    />
                  </div>
                  <div class="bubble-time-right" v-if="c.userId == state.userId">{{ c.createdAt }}</div>
                </div>
              </div>
              <el-empty v-if="!comments.length && !loading.comments" description="暂无评论，虚位以待" />
            </div>
            
            <div class="comment-input-box">
              <el-input 
                v-model="commentDraft" 
                type="textarea" 
                :rows="2" 
                placeholder="参与讨论..." 
                style="margin-bottom: 10px"
              />
              <el-button type="primary" size="small" @click="submitComment">发表评论</el-button>
            </div>
          </div>
          <el-empty v-else description="点击左侧讨论查看详细评论与交流" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 修改评价 Dialog -->
    <el-dialog v-model="editVisible" title="修改我的评价" width="40%">
      <el-form :model="editForm" label-position="top">
        <el-form-item label="评价资源">
          <el-input :value="editForm.resourceTitle" disabled />
        </el-form-item>
        <el-form-item label="评分" required>
          <el-rate v-model="editForm.score" show-text :texts="['极差', '失望', '一般', '满意', '非常推荐']" />
        </el-form-item>
        <el-form-item label="详细内容" required>
          <el-input v-model="editForm.content" type="textarea" :rows="4" placeholder="分享你的感悟或对资源的见解..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEditPost">保存修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.community-participation-container {
  padding: 30px;
  background-color: #f0f2f5;
  min-height: calc(100vh - 60px);
}

.post-list-card, .create-card, .comment-card {
  border-radius: 16px;
  border: none;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05) !important;
}

.card-header {
  font-weight: 700;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.post-scroll-area {
  height: calc(100vh - 200px);
  overflow-y: auto;
  padding: 10px;
}

.post-item-modern {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
  display: flex;
  gap: 16px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid transparent;
}

.post-item-modern:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.08);
  border-color: #409EFF33;
}

.post-item-modern.active {
  background: #fdfdfd;
  border-color: #409EFF;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.15);
}

.user-avatar-side {
  flex-shrink: 0;
}

.post-main-content {
  flex: 1;
  min-width: 0;
}

.post-header-modern {
  margin-bottom: 12px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.modern-tag {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 20px;
  font-weight: 600;
}

.modern-tag.discussion { background: #e3f2fd; color: #1976d2; }
.modern-tag.resource_update { background: #fff3e0; color: #f57c00; }

.modern-post-title {
  margin: 0;
  font-size: 18px;
  color: #2c3e50;
  line-height: 1.4;
}

.modern-post-body {
  font-size: 15px;
  color: #5d6d7e;
  line-height: 1.6;
  margin: 0 0 16px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.modern-post-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.user-name-modern {
  font-weight: 600;
  color: #34495e;
  font-size: 13px;
}

.stat-item {
  color: #95a5a6;
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.footer-actions {
  display: flex;
  gap: 10px;
}

/* Comments Modern UI */
.comment-list {
  height: 400px;
  overflow-y: auto;
  padding: 15px;
  background: #f8f9fa;
  border-radius: 12px;
  margin-bottom: 15px;
}

.comment-bubble-container {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  align-items: flex-end;
}

.comment-bubble-container.my-comment {
  flex-direction: row-reverse;
}

.comment-bubble {
  max-width: 80%;
  position: relative;
}

.bubble-content {
  background: #fff;
  padding: 10px 14px;
  border-radius: 14px 14px 14px 2px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
  font-size: 14px;
  color: #34495e;
  line-height: 1.5;
  position: relative;
}

.my-comment .bubble-content {
  background: #409EFF;
  color: #fff;
  border-radius: 14px 14px 2px 14px;
}

.bubble-info {
  margin-bottom: 4px;
  display: flex;
  gap: 8px;
  font-size: 11px;
}

.bubble-user {
  font-weight: 700;
  color: #7f8c8d;
}

.bubble-time {
  color: #bdc3c7;
}

.bubble-time-right {
  text-align: right;
  font-size: 10px;
  color: #bdc3c7;
  margin-top: 4px;
}

.bubble-delete {
  position: absolute;
  top: -8px;
  right: -8px;
  background: #fff;
  padding: 2px;
  border-radius: 50%;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  display: none;
}

.comment-bubble-container:hover .bubble-delete {
  display: block;
}

.comment-input-box {
  background: #fff;
  padding: 12px;
  border-radius: 12px;
  box-shadow: 0 -2px 10px rgba(0,0,0,0.02);
}

:deep(.el-textarea__inner) {
  border-radius: 8px;
  background-color: #fdfdfd;
}
</style>
