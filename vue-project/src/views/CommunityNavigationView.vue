<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  getHotRank,
  getHotTags,
  getLatestRank,
  searchResources,
} from '@/api/navigationApi'
import {
  recordDownload,
  resolveMediaUrl,
  listFolders,
  addResourceToFolder,
  createFolder
} from '@/api/interactionApi'
import { 
  Search, 
  Refresh, 
  PriceTag, 
  TrendCharts, 
  List, 
  Download, 
  Star,
  FolderOpened,
  Loading,
  Memo,
  View
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const loading = reactive({
  category: false,
  tags: false,
  latest: false,
  hot: false,
  search: false,
  interaction: false,
})

const hotTags = ref([])
const latestRank = ref([])
const hotRank = ref([])
const searchResult = ref([])
const folders = ref([])

const searchForm = reactive({
  keyword: '',
  subjectName: '',
  examType: '',
  competitionType: '',
  tagName: '',
  sortBy: 'latest',
  limit: 20,
})

const currentUser = ref(readUserCache())
const validUserId = computed(() => currentUser.value?.id)

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


async function loadCategoryTree() {
  // 分类系统已移除
}

async function loadHotTags() {
  loading.tags = true
  try {
    hotTags.value = await getHotTags(15)
  } finally {
    loading.tags = false
  }
}

async function loadRanks() {
  loading.latest = true
  loading.hot = true
  try {
    const [latest, hot] = await Promise.all([getLatestRank(10), getHotRank(10)])
    latestRank.value = latest
    hotRank.value = hot
  } finally {
    loading.latest = false
    loading.hot = false
  }
}

async function loadFolders() {
  if (!validUserId.value) return
  try {
    folders.value = await listFolders(validUserId.value)
  } catch (error) {
    console.error('Failed to load folders', error)
  }
}

async function handleSearch() {
  loading.search = true
  try {
    searchResult.value = await searchResources(searchForm)
    if (searchResult.value.length) {
      ElMessage.success(`找到 ${searchResult.value.length} 条匹配资源`)
    }
  } catch (error) {
    ElMessage.error(`检索失败：${normalizeError(error)}`)
  } finally {
    loading.search = false
  }
}

async function handleDownload(resource) {
  if (!validUserId.value) {
    ElMessage.warning('请登录后再下载资源')
    return
  }
  try {
    const resourceId = typeof resource === 'object' ? resource.id : resource
    const fileUrl = typeof resource === 'object' ? resource.fileUrl : null
    
    await recordDownload(resourceId, validUserId.value)
    
    if (fileUrl) {
      const url = resolveMediaUrl(fileUrl)
      // 使用隐藏 a 标签触发强制下载，避免浏览器直接打开文件
      const link = document.createElement('a')
      link.href = url
      // 提取原始文件名（可选）或使用资源标题
      const originalFileName = typeof resource === 'object' ? resource.title : 'download'
      link.setAttribute('download', originalFileName)
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      ElMessage.success('开始下载...')
    } else {
      ElMessage.success('下载记录已提交')
    }
  } catch (error) {
    ElMessage.error(normalizeError(error))
  }
}

async function handleFavorite(resourceId) {
  if (!validUserId.value) {
    ElMessage.warning('请登录后再收藏资源')
    return
  }
  
  loading.interaction = true
  try {
    await loadFolders()
    let folderId = folders.value[0]?.id
    
    // Auto create default folder if none exists
    if (!folderId) {
       const newFolder = await createFolder(validUserId.value, '我的收藏')
       folderId = newFolder.id
    }
    
    await addResourceToFolder(folderId, validUserId.value, resourceId)
    ElMessage.success('已添加到收藏夹')
  } catch (error) {
    ElMessage.error(`收藏失败：${normalizeError(error)}`)
  } finally {
    loading.interaction = false
  }
}

function resetSearch() {
  searchForm.keyword = ''
  searchForm.categoryLv1Id = ''
  searchForm.categoryLv2Id = ''
  searchForm.subjectName = ''
  searchForm.examType = ''
  searchForm.competitionType = ''
  searchForm.tagName = ''
  searchForm.sortBy = 'latest'
  searchForm.limit = 20
  searchResult.value = []
}

// Preview functionality
const previewVisible = ref(false)
const previewType = ref('') // 'pdf' or 'txt'
const previewUrl = ref('')
const previewText = ref('')
const previewTitle = ref('')
const previewLoading = ref(false)

function isSupportedPreview(resource) {
  if (!resource || !resource.fileUrl) return false
  const url = resource.fileUrl.toLowerCase()
  return url.endsWith('.pdf') || url.endsWith('.txt')
}

async function handlePreview(resource) {
  if (!resource || !resource.fileUrl) return
  previewTitle.value = resource.title || '文件预览'
  const resolvedUrl = resolveMediaUrl(resource.fileUrl)
  previewUrl.value = resolvedUrl
  
  const lowerUrl = resource.fileUrl.toLowerCase()
  if (lowerUrl.endsWith('.pdf')) {
    previewType.value = 'pdf'
    previewVisible.value = true
  } else if (lowerUrl.endsWith('.txt')) {
    previewType.value = 'txt'
    previewVisible.value = true
    previewLoading.value = true
    previewText.value = ''
    try {
      const response = await fetch(resolvedUrl)
      if (!response.ok) {
        throw new Error('获取文本文件内容失败')
      }
      const buffer = await response.arrayBuffer()
      let text = new TextDecoder('utf-8').decode(buffer)
      if (text.includes('\uFFFD')) {
        text = new TextDecoder('gbk').decode(buffer)
      }
      previewText.value = text
    } catch (error) {
      console.error(error)
      ElMessage.error('无法读取文本内容: ' + error.message)
      previewVisible.value = false
    } finally {
      previewLoading.value = false
    }
  }
}

onMounted(async () => {
  await Promise.all([loadCategoryTree(), loadHotTags(), loadRanks()])
  await handleSearch()
})
</script>

<template>
  <div class="community-navigation-container">
    <el-row :gutter="24">
      <!-- Left Sidebar: Categories & Tags -->
      <el-col :span="6">

        <el-card shadow="never" class="sidebar-card" style="margin-top: 20px">
          <template #header>
            <div class="card-header"><el-icon><PriceTag /></el-icon> 热门标签</div>
          </template>
          <div v-loading="loading.tags" class="hot-tags-box">
             <el-tag 
               v-for="tag in hotTags" 
               :key="tag.tagId"
               :type="tag.useCount > 5 ? 'danger' : 'info'"
               class="hot-tag"
               @click="searchForm.tagName = tag.tagName; handleSearch()"
             >
               {{ tag.tagName }}
             </el-tag>
          </div>
        </el-card>

        <el-card shadow="never" style="margin-top: 20px">
          <template #header>
            <div class="card-header"><el-icon><TrendCharts /></el-icon> 活跃榜单</div>
          </template>
          <el-tabs stretch size="small">
            <el-tab-pane label="最热资源">
              <ul class="rank-list">
                <li v-for="(item, index) in hotRank" :key="item.id">
                  <span class="rank-num" :class="{ 'top-3': index < 3 }">{{ index + 1 }}</span>
                  <span class="rank-title" @click="isSupportedPreview(item) ? handlePreview(item) : handleDownload(item)">
                    {{ item.title }}
                    <span v-if="isSupportedPreview(item)" class="rank-preview-tag">预览</span>
                  </span>
                </li>
              </ul>
            </el-tab-pane>
            <el-tab-pane label="最新发布">
              <ul class="rank-list">
                <li v-for="(item, index) in latestRank" :key="item.id">
                  <span class="rank-num">{{ index + 1 }}</span>
                  <span class="rank-title" @click="isSupportedPreview(item) ? handlePreview(item) : handleDownload(item)">
                    {{ item.title }}
                    <span v-if="isSupportedPreview(item)" class="rank-preview-tag">预览</span>
                  </span>
                </li>
              </ul>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>

      <!-- Main Content: Search & Results -->
      <el-col :span="18">
        <el-card shadow="never" class="search-panel">
          <template #header>
            <div class="card-header">
              <span><el-icon><Search /></el-icon> 联合检索中心</span>
            </div>
          </template>
          
          <el-form :model="searchForm" label-position="top">
             <el-row :gutter="20">
               <el-col :span="8">
                 <el-form-item label="关键词">
                   <el-input v-model="searchForm.keyword" placeholder="搜索标题/摘要" clearable />
                 </el-form-item>
               </el-col>
               <el-col :span="8">
                 <el-form-item label="所属学科">
                   <el-input v-model="searchForm.subjectName" placeholder="如：高等数学" clearable />
                 </el-form-item>
               </el-col>
               <el-col :span="8">
                 <el-form-item label="考试/竞赛">
                   <el-input v-model="searchForm.examType" placeholder="如：考研、挑战杯" clearable />
                 </el-form-item>
               </el-col>
             </el-row>
             
             <el-row :gutter="20">
               <el-col :span="6">
                 <el-form-item label="排序方式">
                   <el-select v-model="searchForm.sortBy" style="width: 100%">
                     <el-option label="最新发布" value="latest" />
                     <el-option label="最热点击" value="hot" />
                     <el-option label="下载最多" value="download" />
                     <el-option label="收藏最多" value="favorite" />
                   </el-select>
                 </el-form-item>
               </el-col>
               <el-col :span="18" class="search-btns">
                 <el-button type="primary" :icon="Search" :loading="loading.search" @click="handleSearch">搜索资源</el-button>
                 <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
               </el-col>
             </el-row>
          </el-form>
        </el-card>

        <div class="result-area" v-loading="loading.search">
          <div v-if="searchResult.length">
             <el-card 
               v-for="item in searchResult" 
               :key="item.id" 
               shadow="hover" 
               class="result-item-card"
             >
                 <div class="result-inner">
                   <div class="res-cover">
                     <el-image :src="resolveMediaUrl(item.coverUrl)" fit="cover" class="cover-img">
                       <template #error>
                         <div class="image-slot"><el-icon><Memo /></el-icon></div>
                       </template>
                     </el-image>
                   </div>
                   <div class="result-info">
                     <h3 
                        class="res-title" 
                        :class="{ 'clickable-title': isSupportedPreview(item) }"
                        @click="isSupportedPreview(item) ? handlePreview(item) : null"
                      >
                        {{ item.title }}
                        <el-tag v-if="isSupportedPreview(item)" size="small" type="success" style="margin-left: 8px; cursor: pointer;">
                          可在线预览
                        </el-tag>
                      </h3>
                     <div class="res-meta">
                       <el-tag size="small" v-if="item.subjectName">{{ item.subjectName }}</el-tag>
                       <span class="res-date">{{ item.createdAt }}</span>
                       <span class="res-stats">
                         <el-icon><Download /></el-icon> {{ item.downloadCount || 0 }}
                         <el-icon style="margin-left: 10px"><Star /></el-icon> {{ item.favoriteCount || 0 }}
                       </span>
                     </div>
                     <p class="res-summary">{{ item.summary || '暂无详细描述...' }}</p>
                   </div>
                   <div class="result-actions">
                     <el-button v-if="isSupportedPreview(item)" type="success" plain :icon="View" @click="handlePreview(item)">在线预览</el-button>
                     <el-button type="primary" plain :icon="Download" @click="handleDownload(item)">直接下载</el-button>
                     <el-button type="warning" plain :icon="Star" @click="handleFavorite(item.id)">加入收藏</el-button>
                   </div>
                 </div>
             </el-card>
          </div>
          <el-empty v-else description="输入关键词或选择分类开始探索吧" />
        </div>
      </el-col>
    </el-row>

    <!-- 文件预览 Dialog -->
    <el-dialog 
      v-model="previewVisible" 
      :title="previewTitle" 
      width="75%" 
      destroy-on-close
      class="preview-dialog"
    >
      <div v-loading="previewLoading" class="preview-body">
        <template v-if="previewType === 'pdf'">
          <iframe 
            :src="previewUrl" 
            width="100%" 
            height="650px" 
            style="border: none;"
          ></iframe>
        </template>
        <template v-else-if="previewType === 'txt'">
          <div class="txt-preview-wrapper">
            <pre class="txt-preview-content">{{ previewText }}</pre>
          </div>
        </template>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="previewVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.community-navigation-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 60px);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: #303133;
}

.category-group {
  margin-bottom: 20px;
}

.lv1-title {
  font-size: 14px;
  font-weight: 700;
  color: #606266;
  margin-bottom: 10px;
  padding-left: 4px;
  border-left: 3px solid #409eff;
}

.lv2-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.lv2-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.lv2-tag:hover {
  transform: scale(1.05);
  background-color: #409eff;
  color: #fff;
}

.hot-tags-box {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hot-tag {
  cursor: pointer;
}

.rank-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.rank-list li {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f2f5;
  font-size: 13px;
}

.rank-num {
  width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  background: #f0f2f5;
  color: #909399;
  border-radius: 4px;
  font-weight: 700;
  margin-right: 12px;
}

.rank-num.top-3 {
  background: #fdf6ec;
  color: #e6a23c;
}

.rank-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  color: #606266;
}

.rank-title:hover {
  color: #409eff;
}

.search-panel {
  margin-bottom: 24px;
}

.search-btns {
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  padding-bottom: 18px;
}

.result-area {
  min-height: 400px;
}

.result-item-card {
  margin-bottom: 16px;
  border: 1px solid #ebeef5;
}

.result-inner {
  display: flex;
  align-items: flex-start;
  gap: 20px;
}

.res-cover {
  width: 140px;
  height: 100px;
  flex-shrink: 0;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.cover-img {
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
  font-size: 24px;
}

.result-info {
  flex: 1;
  min-width: 0;
}

.res-title {
  margin: 0 0 8px;
  color: #1a1a1a;
  font-size: 18px;
}

.res-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #909399;
  font-size: 13px;
  margin-bottom: 10px;
}

.res-stats {
  display: flex;
  align-items: center;
}

.res-summary {
  margin: 0;
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}

.result-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 140px;
}

.result-actions .el-button {
  margin-left: 0;
}

.preview-dialog :deep(.el-dialog__body) {
  padding: 10px 20px 20px;
}

.preview-body {
  min-height: 200px;
  background-color: #fafafa;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  overflow: hidden;
}

.txt-preview-wrapper {
  max-height: 650px;
  overflow-y: auto;
  padding: 20px;
  background-color: #fcfcfc;
}

.txt-preview-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: Menlo, Monaco, Consolas, "Courier New", monospace, "Microsoft YaHei";
  font-size: 14px;
  line-height: 1.6;
  color: #303133;
}

.clickable-title {
  cursor: pointer;
  transition: color 0.2s;
}

.clickable-title:hover {
  color: #67c23a;
}

.rank-preview-tag {
  margin-left: 6px;
  font-size: 10px;
  background-color: #f0f9eb;
  color: #67c23a;
  border: 1px solid #e1f3d8;
  padding: 1px 4px;
  border-radius: 3px;
  font-weight: normal;
}
</style>
