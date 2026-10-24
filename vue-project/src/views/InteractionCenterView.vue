<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  addResourceToFolder,
  batchRemoveFolderItems,
  createFolder,
  getInteractionRecords,
  listFolderItems,
  listFolders,
  listPublicResources,
  recordDownload,
  removeFolderItem,
  resolveMediaUrl,
} from '@/api/interactionApi'
import { 
  Download, 
  FolderOpened, 
  Plus, 
  Delete, 
  Refresh, 
  Files, 
  FolderAdd,
  CircleCheck,
  VideoPlay,
  Memo,
  View
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const state = reactive({
  userId: readUserCache()?.id || '',
  loading: false,
  folderLoading: false,
  interactionLoading: false,
  itemLoading: false,
  downloadBusyId: null,
  createFolderName: '',
  selectedFolderId: '',
  selectedResourceId: '',
  historyLimit: 20,
})

const resources = ref([])
const folders = ref([])
const folderItems = ref([])
const selectedItemIds = ref([])
const activeTab = ref('favorites')

const interaction = reactive({
  downloadHistory: [],
  favoriteFolders: [],
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
  return error instanceof Error ? error.message : '操作失败，请稍后再试'
}

const validUserId = computed(() => {
  const n = Number(state.userId)
  return Number.isInteger(n) && n > 0 ? n : null
})

const resourceOptions = computed(() => {
  return resources.value.map((item) => ({
    ...item,
    coverUrl: resolveMediaUrl(item.coverUrl),
  }))
})

async function loadResources() {
  state.loading = true
  try {
    resources.value = await listPublicResources()
  } catch (error) {
    ElMessage.error(`资源列表加载失败：${normalizeError(error)}`)
  } finally {
    state.loading = false
  }
}

async function loadFolders() {
  if (!validUserId.value) return
  state.folderLoading = true
  try {
    folders.value = await listFolders(validUserId.value)
    if (folders.value.length && !state.selectedFolderId) {
      state.selectedFolderId = folders.value[0].id
    }
  } catch (error) {
    ElMessage.error(`收藏夹加载失败：${normalizeError(error)}`)
  } finally {
    state.folderLoading = false
  }
}

async function loadFolderItems() {
  if (!validUserId.value || !state.selectedFolderId) {
    folderItems.value = []
    return
  }
  state.itemLoading = true
  try {
    folderItems.value = await listFolderItems(state.selectedFolderId, validUserId.value)
    selectedItemIds.value = []
  } catch (error) {
    ElMessage.error(`收藏条目加载失败：${normalizeError(error)}`)
  } finally {
    state.itemLoading = false
  }
}

async function loadInteraction() {
  if (!validUserId.value) return
  state.interactionLoading = true
  try {
    const data = await getInteractionRecords(validUserId.value, state.historyLimit)
    interaction.downloadHistory = data?.downloadHistory || []
    interaction.favoriteFolders = data?.favoriteFolders || []
  } catch (error) {
    ElMessage.error(`交互记录加载失败：${normalizeError(error)}`)
  } finally {
    state.interactionLoading = false
  }
}

async function handleDownload(resourceId) {
  if (!validUserId.value) {
    ElMessage.warning('请登录后再记录下载')
    return
  }
  state.downloadBusyId = resourceId
  try {
    const res = resources.value.find(r => r.id === resourceId)
    await recordDownload(resourceId, validUserId.value)
    if (res?.fileUrl) {
      const url = resolveMediaUrl(res.fileUrl)
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', res.title || 'download')
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      ElMessage.success('开始下载...')
    } else {
      ElMessage.success('下载记录已提交')
    }
    await loadInteraction()
  } catch (error) {
    ElMessage.error(normalizeError(error))
  } finally {
    state.downloadBusyId = null
  }
}

async function handleCreateFolder() {
  if (!validUserId.value || !state.createFolderName.trim()) {
    ElMessage.warning('请输入收藏夹名称')
    return
  }
  try {
    await createFolder(validUserId.value, state.createFolderName.trim())
    state.createFolderName = ''
    ElMessage.success('收藏夹创建成功')
    await loadFolders()
  } catch (error) {
    ElMessage.error(normalizeError(error))
  }
}

async function handleAddToFolder() {
  if (!validUserId.value || !state.selectedFolderId || !state.selectedResourceId) {
    ElMessage.warning('请完整选择收藏夹和资源')
    return
  }
  try {
    await addResourceToFolder(state.selectedFolderId, validUserId.value, state.selectedResourceId)
    ElMessage.success('已添加到收藏夹')
    await loadFolderItems()
  } catch (error) {
    ElMessage.error(normalizeError(error))
  }
}

async function handleBatchRemove() {
  if (!selectedItemIds.value.length) {
    ElMessage.warning('请选择要清理的条目')
    return
  }
  try {
    await ElMessageBox.confirm(`确定移除选中的 ${selectedItemIds.value.length} 个条目吗？`, '提示', { type: 'warning' })
    await batchRemoveFolderItems(state.selectedFolderId, validUserId.value, selectedItemIds.value)
    ElMessage.success('已批量移除')
    await loadFolderItems()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(normalizeError(error))
  }
}

// 在线预览功能
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
  await loadResources()
  if (validUserId.value) {
    await Promise.all([loadFolders(), loadInteraction()])
    await loadFolderItems()
  }
})
</script>

<template>
  <div class="interaction-center-container">
    <el-row :gutter="20">
      <!-- Side Tools -->
      <el-col :span="7">
        <el-card shadow="never" class="tool-card">
          <template #header>
            <div class="card-header"><el-icon><FolderAdd /></el-icon> 收藏管理控制台</div>
          </template>

          <el-form label-position="top">
            <el-form-item label="新建收藏空间">
              <el-input v-model="state.createFolderName" placeholder="例如：学习计划、论文资料">
                <template #append>
                  <el-button :icon="Plus" @click="handleCreateFolder" />
                </template>
              </el-input>
            </el-form-item>

            <el-divider />

            <el-form-item label="收藏资源到空间">
              <el-select v-model="state.selectedFolderId" placeholder="选择目标收藏夹" style="width: 100%; margin-bottom: 10px" @change="loadFolderItems">
                <el-option v-for="f in folders" :key="f.id" :label="f.folderName" :value="f.id" />
              </el-select>
              <el-select v-model="state.selectedResourceId" placeholder="选择要收藏的资源" filterable style="width: 100%; margin-bottom: 10px">
                <el-option v-for="r in resources" :key="r.id" :label="r.title" :value="r.id" />
              </el-select>
              <el-button type="primary" :icon="Plus" style="width: 100%" @click="handleAddToFolder">添加到文件夹</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" style="margin-top: 20px">
          <template #header>
            <div class="card-header"><el-icon><Download /></el-icon> 快速记录下载</div>
          </template>
          <div class="quick-download-list">
             <div v-for="r in resources.slice(0, 5)" :key="r.id" class="mini-res-item">
                <span>{{ r.title }}</span>
                <el-button size="small" circle :icon="Download" :loading="state.downloadBusyId === r.id" @click="handleDownload(r.id)" />
             </div>
          </div>
        </el-card>
      </el-col>

      <!-- Main Interaction Content -->
      <el-col :span="17">
        <el-tabs v-model="activeTab" type="border-card">
          <el-tab-pane name="favorites">
            <template #label>
              <span class="tab-label"><el-icon><FolderOpened /></el-icon> 我的收藏夹详情</span>
            </template>
            
            <div class="folder-content-header" v-if="state.selectedFolderId">
              <div class="folder-title">
                <el-icon><Files /></el-icon>
                <span>{{ folders.find(f => f.id === state.selectedFolderId)?.folderName || '未命名' }}</span>
              </div>
              <el-button type="danger" plain :icon="Delete" :disabled="!selectedItemIds.length" @click="handleBatchRemove">
                批量清理 ({{ selectedItemIds.length }})
              </el-button>
            </div>

            <el-table 
              v-loading="state.itemLoading" 
              :data="folderItems" 
              style="width: 100%; margin-top: 15px"
              @selection-change="(selection) => selectedItemIds = selection.map(i => i.itemId)"
            >
              <el-table-column type="selection" width="55" />
              <el-table-column prop="title" label="资源内容" min-width="200">
                 <template #default="scope">
                    <div style="display: flex; align-items: center; gap: 10px">
                      <el-image :src="resolveMediaUrl(scope.row.coverUrl)" style="width: 40px; height: 30px; border-radius: 4px; flex-shrink: 0" fit="cover">
                         <template #error><div style="background:#f5f7fa;width:100%;height:100%;display:flex;justify-content:center;align-items:center"><el-icon><Memo /></el-icon></div></template>
                      </el-image>
                      <span 
                        :style="isSupportedPreview(scope.row) ? 'cursor: pointer; font-weight: 500;' : ''" 
                        @click="isSupportedPreview(scope.row) ? handlePreview(scope.row) : null"
                      >
                        {{ scope.row.title || `资源 #${scope.row.resourceId}` }}
                      </span>
                      <el-tag 
                        v-if="isSupportedPreview(scope.row)" 
                        size="small" 
                        type="success" 
                        style="cursor: pointer;"
                        @click="handlePreview(scope.row)"
                      >
                        预览
                      </el-tag>
                    </div>
                 </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="收藏时间" width="160" />
              <el-table-column label="操作" width="200" fixed="right">
                 <template #default="scope">
                    <el-button-group>
                      <el-button 
                        v-if="isSupportedPreview(scope.row)" 
                        type="success" 
                        size="small" 
                        plain 
                        :icon="View" 
                        @click="handlePreview(scope.row)"
                      >
                        在线预览
                      </el-button>
                      <el-button 
                        type="primary" 
                        size="small" 
                        plain 
                        :icon="Download" 
                        @click="handleDownload(scope.row.resourceId)"
                      >
                        直接下载
                      </el-button>
                    </el-button-group>
                 </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!folderItems.length && !state.itemLoading" description="当前收藏夹空空如也" />
          </el-tab-pane>

          <el-tab-pane name="history">
            <template #label>
              <span class="tab-label"><el-icon><Refresh /></el-icon> 最近交互流水</span>
            </template>
            
            <div class="history-grid">
               <el-card shadow="never" class="history-box">
                 <template #header><div>下载流水</div></template>
                 <el-table :data="interaction.downloadHistory" size="small" stripe height="400">
                   <el-table-column prop="resourceId" label="资源ID" width="70" />
                   <el-table-column prop="createdAt" label="发生时间" />
                 </el-table>
               </el-card>
               
               <el-card shadow="never" class="history-box">
                 <template #header><div>空间概览</div></template>
                 <el-table :data="interaction.favoriteFolders" size="small" stripe height="400">
                    <el-table-column prop="folder.folderName" label="收藏夹名" />
                    <el-table-column label="项数" width="60">
                      <template #default="scope">{{ scope.row.resources.length }}</template>
                    </el-table-column>
                 </el-table>
               </el-card>
            </div>
          </el-tab-pane>
        </el-tabs>
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
.interaction-center-container {
  padding: 24px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.quick-download-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mini-res-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #606266;
}

.mini-res-item span {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 10px;
}

.folder-content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #fafafa;
  padding: 10px 15px;
  border-radius: 4px;
}

.folder-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}

.history-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.history-box {
  border-radius: 8px;
}

.txt-preview-wrapper {
  max-height: 500px;
  overflow-y: auto;
  padding: 15px;
  background-color: #fafafa;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
}

.txt-preview-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: Consolas, Monaco, monospace;
  font-size: 14px;
  line-height: 1.6;
  color: #333;
}
</style>
