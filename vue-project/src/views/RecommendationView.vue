<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  getRecommendations,
  recordDownloadAction,
  recordFavoriteAction,
  refreshRecommendations,
  resolveMediaUrl,
} from '@/api/recommendationApi'
import { 
  DataLine, 
  Refresh, 
  Download, 
  Star, 
  Switch, 
  InfoFilled,
  UserFilled,
  Memo
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const state = reactive({
  loading: false,
  actionLoading: false,
  userId: readUserCache()?.id || '',
  limit: 10,
  useForceRefresh: false,
  lastFetchMode: 'cache-first',
  lastFetchedAt: '',
})

const recommendList = ref([])

const viewList = computed(() => {
  return recommendList.value.map((item) => ({
    ...item,
    coverUrl: resolveMediaUrl(item.coverUrl),
  }))
})

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
  return error instanceof Error ? error.message : '请求失败，请稍后重试'
}

const coldStartMode = computed(() => {
  if (!recommendList.value.length) return false
  const text = recommendList.value.map((item) => String(item.recReason || '')).join('|').toLowerCase()
  return text.includes('cold') || text.includes('冷启动') || text.includes('热门')
})

async function fetchRecommendations({ force = false, manual = false } = {}) {
  const userId = Number(state.userId)
  if (!userId) {
    ElMessage.warning('请登录后再获取个性化推荐')
    return
  }

  state.loading = true
  try {
    let data = []
    if (force || state.useForceRefresh) {
      data = manual
        ? await refreshRecommendations(userId, { limit: state.limit })
        : await getRecommendations(userId, { limit: state.limit, refresh: true })
      state.lastFetchMode = 'force-refresh'
    } else {
      data = await getRecommendations(userId, { limit: state.limit, refresh: false })
      state.lastFetchMode = 'cache-first'
    }

    recommendList.value = data || []
    state.lastFetchedAt = new Date().toLocaleString()

    if (!recommendList.value.length) {
      ElMessage.info('当前暂无推荐结果，可先产生下载/收藏行为后再刷新。')
    } else {
      ElMessage.success(coldStartMode.value ? '已返回热门/兴趣冷启动推荐' : '已返回协同过滤推荐结果')
    }
  } catch (error) {
    ElMessage.error(normalizeError(error))
  } finally {
    state.loading = false
  }
}

async function handleManualRefresh() {
  await fetchRecommendations({ force: true, manual: true })
}

async function feedbackAction(resource, action) {
  const userId = Number(state.userId)
  const resourceId = typeof resource === 'object' ? resource.resourceId : resource
  if (!userId || !resourceId) return

  state.actionLoading = true
  try {
    if (action === 'download') {
      await recordDownloadAction(resourceId, userId)
      if (resource?.fileUrl) {
         const url = resolveMediaUrl(resource.fileUrl)
         const link = document.createElement('a')
         link.href = url
         link.setAttribute('download', resource.title || 'download')
         document.body.appendChild(link)
         link.click()
         document.body.removeChild(link)
         ElMessage.success('开始下载...')
      } else {
         ElMessage.success('下载记录已提交')
      }
    } else {
      await recordFavoriteAction(resourceId, userId)
      ElMessage.success('行为已记录')
    }

    await fetchRecommendations({ force: false })
  } catch (error) {
    ElMessage.error(normalizeError(error))
  } finally {
    state.actionLoading = false
  }
}

onMounted(async () => {
  if (state.userId) {
    await fetchRecommendations({ force: false })
  }
})
</script>

<template>
  <div class="recommendation-container">
    <el-container>
      <!-- Sidebar Controls -->
      <el-aside width="320px">
        <el-card shadow="never" class="control-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><DataLine /></el-icon> 推荐引擎设置</span>
            </div>
          </template>
          
          <el-form label-position="top">
            <el-form-item label="当前用户ID">
              <el-input v-model.number="state.userId" :prefix-icon="UserFilled" disabled />
            </el-form-item>
            <el-form-item label="推荐资源条数">
              <el-slider v-model="state.limit" :min="5" :max="50" show-stops />
            </el-form-item>
            <el-form-item label="刷新模式">
               <el-switch
                v-model="state.useForceRefresh"
                active-text="强制重算 (跳过缓存)"
                inactive-text="缓存优先"
              />
            </el-form-item>
            
            <el-divider />
            
            <div class="action-buttons">
              <el-button type="primary" :icon="Refresh" :loading="state.loading" @click="fetchRecommendations({ force: false })" style="width: 100%">
                立即获取推荐
              </el-button>
              <el-button :icon="Switch" plain @click="handleManualRefresh" style="width: 100%; margin-top: 10px; margin-left: 0">
                重置推荐模型
              </el-button>
            </div>
          </el-form>

          <el-descriptions :column="1" border style="margin-top: 20px" size="small">
            <el-descriptions-item label="读取模式">
              <el-tag size="small">{{ state.lastFetchMode === 'force-refresh' ? '实时重算' : '快取命中' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="最近更新">{{ state.lastFetchedAt || '从未' }}</el-descriptions-item>
            <el-descriptions-item label="冷启动状态">
               <el-tag :type="coldStartMode ? 'warning' : 'success'" size="small">
                 {{ coldStartMode ? '命中兜底' : '协同过滤' }}
               </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-aside>

      <!-- Main Content List -->
      <el-main class="recommendation-main">
        <div class="list-header">
          <h2>为您精选的资源</h2>
          <el-alert
            v-if="coldStartMode"
            title="您目前产生的行为较少，系统为您推荐了热门资源及兴趣相关内容。"
            type="warning"
            show-icon
            :closable="false"
          />
        </div>

        <el-empty v-if="!viewList.length && !state.loading" description="暂无推荐，尝试去下载或收藏一些资料吧" />

        <div v-loading="state.loading" class="resource-grid">
           <el-row :gutter="20">
             <el-col :span="12" v-for="item in viewList" :key="item.resourceId" style="margin-bottom: 20px">
               <el-card shadow="hover" :body-style="{ padding: '0px' }" class="resource-card">
                 <div class="card-horizontal">
                    <div class="resource-cover-box">
                      <el-image :src="item.coverUrl" fit="cover" class="resource-cover">
                        <template #error>
                          <div class="image-slot">
                            <el-icon><Memo /></el-icon>
                          </div>
                        </template>
                      </el-image>
                    </div>
                    <div class="resource-detail">
                      <h3 class="resource-title">{{ item.title || `资源 #${item.resourceId}` }}</h3>
                      <p class="resource-reason">
                        <el-icon><InfoFilled /></el-icon> {{ item.recReason || '根据您的学习兴趣推荐' }}
                      </p>
                      <div class="resource-meta">
                        <el-tag size="small" type="info">评分: {{ item.recScore?.toFixed(2) || '0.00' }}</el-tag>
                        <el-tag size="small" type="success" style="margin-left: 5px">ID: {{ item.resourceId }}</el-tag>
                      </div>
                      <div class="resource-ops">
                        <el-button size="small" :icon="Download" @click="feedbackAction(item, 'download')" :loading="state.actionLoading">下载</el-button>
                        <el-button size="small" :icon="Star" type="warning" plain @click="feedbackAction(item, 'favorite')" :loading="state.actionLoading">收藏</el-button>
                      </div>
                    </div>
                 </div>
               </el-card>
             </el-col>
           </el-row>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<style scoped>
.recommendation-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 60px);
}

.control-card {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}

.recommendation-main {
  padding-top: 0;
}

.list-header {
  margin-bottom: 20px;
}

.list-header h2 {
  margin-top: 0;
  margin-bottom: 15px;
  color: #303133;
}

.resource-card {
  transition: transform 0.2s;
}

.resource-card:hover {
  transform: translateY(-4px);
}

.card-horizontal {
  display: flex;
  height: 160px;
}

.resource-cover-box {
  width: 140px;
  height: 100%;
}

.resource-cover {
  width: 100%;
  height: 100%;
}

.image-slot {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 100%;
  background: #f5f7fa;
  color: #909399;
  font-size: 30px;
}

.resource-detail {
  flex: 1;
  padding: 15px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.resource-title {
  margin: 0;
  font-size: 16px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-reason {
  font-size: 13px;
  color: #606266;
  margin: 8px 0;
  display: flex;
  align-items: center;
  gap: 4px;
}

.resource-ops {
  margin-top: 10px;
}
</style>
