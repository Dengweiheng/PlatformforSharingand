<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  buildPreviewUrl,
  deleteResource,
  listResourceVersions,
  listUploaderResources,
  updateResource,
  updateResourceVisibility,
  uploadResource,
  uploadResourceVersion,
} from '@/api/resourceCenterApi'
import { resolveMediaUrl } from '@/api/interactionApi'
import { 
  Plus,  Edit,
  Upload, 
  Management, 
  View, 
  Delete, 
  Refresh, 
  Position, 
  Clock,
  CircleCheck,
  Warning,
  Lock,
  UploadFilled
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const userCache = readUserCache()

const uploadForm = reactive({
  uploaderId: userCache?.id || null,
  title: '',
  summary: '',
  subjectName: '',
  examType: '',
  competitionType: '',
  tags: '',
  visibility: 'PUBLIC',
  resourceFile: null,
  coverFile: null,
})

const versionForm = reactive({
  resourceId: '',
  changeLog: '',
  resourceFile: null,
})

const loading = reactive({
  upload: false,
  resources: false,
  versions: false,
  edit: false,
})

const editForm = reactive({
  id: null,
  title: '',
  summary: '',
  subjectName: '',
  examType: '',
  competitionType: '',
  tags: '',
  resourceFile: null,
  coverFile: null,
})

const editVisible = ref(false)
const editCoverPreview = ref('')

const resources = ref([])
const versions = ref([])
const selectedResourceId = ref(null)
const coverPreview = ref('')
const activeTab = ref('manage')
const drawerVisible = ref(false)
const versionUploadRef = ref(null)


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

function onResourceFileChange(file) {
  uploadForm.resourceFile = file.raw
}

function onCoverFileChange(file) {
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
  uploadForm.coverFile = file.raw
  coverPreview.value = URL.createObjectURL(file.raw)
}

function onVersionFileChange(file) {
  versionForm.resourceFile = file.raw
}

function onEditResourceFileChange(file) {
  editForm.resourceFile = file.raw
}

function onEditCoverFileChange(file) {
  if (editCoverPreview.value) URL.revokeObjectURL(editCoverPreview.value)
  editForm.coverFile = file.raw
  editCoverPreview.value = URL.createObjectURL(file.raw)
}

async function loadResources() {
  if (!uploadForm.uploaderId) {
    resources.value = []
    return
  }
  loading.resources = true
  try {
    resources.value = await listUploaderResources(uploadForm.uploaderId)
  } catch (error) {
    ElMessage.error(`资源列表加载失败：${normalizeError(error)}`)
  } finally {
    loading.resources = false
  }
}

async function handleUploadResource() {
  if (!uploadForm.uploaderId) {
    ElMessage.warning('请先登录及核对上传者ID')
    return
  }
  if (!uploadForm.title || !uploadForm.summary || !uploadForm.subjectName || !uploadForm.examType || !uploadForm.competitionType || !uploadForm.tags || !uploadForm.resourceFile) {
    console.error('上传校验失败，缺失字段详情：', {
      title: !!uploadForm.title,
      summary: !!uploadForm.summary,
      subjectName: !!uploadForm.subjectName,
      examType: !!uploadForm.examType,
      competitionType: !!uploadForm.competitionType,
      tags: !!uploadForm.tags,
      resourceFile: !!uploadForm.resourceFile
    })
    ElMessage.warning('请检查所有带有星号的必填项是否已填写')
    return
  }

  const formData = new FormData()
  formData.append('uploaderId', String(uploadForm.uploaderId))
  formData.append('title', uploadForm.title)
  formData.append('summary', uploadForm.summary)
  formData.append('subjectName', uploadForm.subjectName)
  formData.append('examType', uploadForm.examType)
  formData.append('competitionType', uploadForm.competitionType)
  formData.append('visibility', uploadForm.visibility)
  formData.append('tags', uploadForm.tags)
  formData.append('resourceFile', uploadForm.resourceFile)
  if (uploadForm.coverFile) formData.append('coverFile', uploadForm.coverFile)

  loading.upload = true
  try {
    await uploadResource(formData)
    ElMessage.success('资源上传成功（待审核）')
    activeTab.value = 'manage'
    await loadResources()
  } catch (error) {
    ElMessage.error(normalizeError(error))
  } finally {
    loading.upload = false
  }
}

async function handleToggleVisibility(resource) {
  const nextVisibility = resource.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC'
  try {
    await updateResourceVisibility(resource.id, Number(uploadForm.uploaderId), nextVisibility)
    ElMessage.success(`状态已切换为 ${nextVisibility}`)
    await loadResources()
  } catch (error) {
    ElMessage.error(normalizeError(error))
  }
}

async function handleDeleteResource(resource) {
  try {
    await ElMessageBox.confirm(`确认删除资源：${resource.title}？`, '提示', { type: 'warning' })
    await deleteResource(resource.id, Number(uploadForm.uploaderId))
    ElMessage.success('资源删除成功')
    await loadResources()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(normalizeError(error))
  }
}

async function handleLoadVersions(resourceId) {
  selectedResourceId.value = resourceId
  versionForm.resourceId = resourceId
  loading.versions = true
  drawerVisible.value = true
  try {
    versions.value = await listResourceVersions(resourceId)
  } catch (error) {
    ElMessage.error(`版本加载失败：${normalizeError(error)}`)
  } finally {
    loading.versions = false
  }
}

async function handleUploadVersion() {
  if (!versionForm.resourceId) {
    ElMessage.warning('资源ID缺失')
    return
  }
  if (!versionForm.resourceFile) {
    ElMessage.warning('请选择新版本文件')
    return
  }

  console.log('正在发布新版本...', {
    resourceId: versionForm.resourceId,
    uploaderId: uploadForm.uploaderId,
    fileName: versionForm.resourceFile?.name,
    fileSize: versionForm.resourceFile?.size
  })
  
  const formData = new FormData()
  formData.append('uploaderId', String(uploadForm.uploaderId))
  formData.append('changeLog', versionForm.changeLog)
  formData.append('resourceFile', versionForm.resourceFile)

  loading.versions = true
  try {
    await uploadResourceVersion(versionForm.resourceId, formData)
    ElMessage.success('新版本发布成功')
    versionForm.changeLog = ''
    versionForm.resourceFile = null
    if (versionUploadRef.value) versionUploadRef.value.clearFiles()
    // 重新加载列表以显示最新版本号
    await loadResources()
    // 刷新当前版本记录
    await handleLoadVersions(versionForm.resourceId)
  } catch (error) {
    ElMessage.error(normalizeError(error))
  } finally {
    loading.versions = false
  }
}

async function handleEditResource(resource) {
  editForm.id = resource.id
  editForm.title = resource.title
  editForm.summary = resource.summary
  editForm.subjectName = resource.subjectName
  editForm.examType = resource.examType
  editForm.competitionType = resource.competitionType
  editForm.tags = resource.tags || ''
  editForm.resourceFile = null
  editForm.coverFile = null
  editCoverPreview.value = resource.coverUrl ? resolveMediaUrl(resource.coverUrl) : ''
  editVisible.value = true
}

async function submitEdit() {
  if (!editForm.id) return
  loading.edit = true
  
  const formData = new FormData()
  formData.append('uploaderId', String(uploadForm.uploaderId))
  formData.append('title', editForm.title)
  formData.append('summary', editForm.summary)
  formData.append('subjectName', editForm.subjectName)
  formData.append('examType', editForm.examType)
  formData.append('competitionType', editForm.competitionType)
  formData.append('tags', editForm.tags)
  if (editForm.resourceFile) formData.append('resourceFile', editForm.resourceFile)
  if (editForm.coverFile) formData.append('coverFile', editForm.coverFile)

  try {
    await updateResource(editForm.id, formData)
    ElMessage.success('资源信息更新成功')
    editVisible.value = false
    await loadResources()
  } catch (error) {
    ElMessage.error(normalizeError(error))
  } finally {
    loading.edit = false
  }
}

function openPreview(resource) {
  const url = buildPreviewUrl(resource)
  if (!url) {
    ElMessage.error('无法生成预览地址')
    return
  }
  const joiner = url.includes('?') ? '&' : '?'
  const withViewer = uploadForm.uploaderId ? `${url}${joiner}viewerId=${uploadForm.uploaderId}` : url
  window.open(withViewer, '_blank')
}

onMounted(async () => {
  await loadResources()
})


onBeforeUnmount(() => {
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
})
</script>

<template>
  <div class="resource-center-container">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- Management Tab -->
      <el-tab-pane name="manage">
        <template #label>
          <span class="tab-label"><el-icon><Management /></el-icon> 资源管理</span>
        </template>

        <div class="manage-header">
          <div class="search-bar">
            <el-button type="primary" :icon="Plus" @click="activeTab = 'upload'">发布新资源</el-button>
            <el-button :icon="Refresh" @click="loadResources">刷新列表</el-button>
          </div>
          <el-alert
            title="提示：点击“版本”按钮可查看历史迭代并上传新版本。"
            type="info"
            show-icon
            style="margin-bottom: 20px"
          />
        </div>

        <el-table :data="resources" v-loading="loading.resources" style="width: 100%">
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column label="版本" width="80">
            <template #default="scope">
              <el-tag size="small">v{{ scope.row.latestVersionNo || 1 }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审核状态" width="100">
            <template #default="scope">
              <el-tag 
                :type="scope.row.auditStatus === 'PASSED' ? 'success' : (scope.row.auditStatus === 'PENDING' ? 'warning' : 'danger')"
              >
                {{ scope.row.auditStatus }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="可见性" width="100">
             <template #default="scope">
                <el-icon v-if="scope.row.visibility === 'PUBLIC'" color="#67C23A"><View /></el-icon>
                <el-icon v-else color="#909399"><Lock /></el-icon>
                <span style="margin-left: 4px">{{ scope.row.visibility }}</span>
             </template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="scope">
              <el-button-group>
                <el-button size="small" type="primary" link @click="openPreview(scope.row)">预览</el-button>
                <el-button size="small" type="primary" link @click="handleEditResource(scope.row)">编辑</el-button>
                <el-button size="small" type="info" link @click="handleLoadVersions(scope.row.id)">版本</el-button>
                <el-button size="small" type="warning" link @click="handleToggleVisibility(scope.row)">权限</el-button>
                <el-button size="small" type="danger" link @click="handleDeleteResource(scope.row)">删除</el-button>
              </el-button-group>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- Upload Tab -->
      <el-tab-pane name="upload">
        <template #label>
          <span class="tab-label"><el-icon><Upload /></el-icon> 发布资料</span>
        </template>
        
        <el-form :model="uploadForm" label-width="120px" class="upload-form">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="资源标题" required>
                <el-input v-model="uploadForm.title" placeholder="输入鲜明的资源标题" />
              </el-form-item>
              <el-form-item label="资源简介" required>
                <el-input v-model="uploadForm.summary" type="textarea" placeholder="简介将展示给其他学习者" />
              </el-form-item>
              <el-form-item label="所属学科" required>
                 <el-input v-model="uploadForm.subjectName" placeholder="例如：计算机科学、数学" />
              </el-form-item>
              <el-form-item label="考试类型" required>
                <el-input v-model="uploadForm.examType" placeholder="例如：考研、期末考试、等级考试" />
              </el-form-item>
              <el-form-item label="竞赛/证书" required>
                <el-input v-model="uploadForm.competitionType" placeholder="例如：软考 / 大学生竞赛" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="搜索标签" required>
                <el-input v-model="uploadForm.tags" placeholder="逗号分隔，如：真题,笔记,总结" />
              </el-form-item>
              <el-form-item label="可见范围">
                <el-radio-group v-model="uploadForm.visibility">
                  <el-radio value="PUBLIC">所有人可见</el-radio>
                  <el-radio value="PRIVATE">仅自己可见</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="资源文件" required>
                <el-upload
                  class="upload-demo"
                  drag
                  action="#"
                  :auto-upload="false"
                  :on-change="onResourceFileChange"
                  :limit="1"
                >
                  <el-icon class="el-icon--upload"><upload-filled /></el-icon>
                  <div class="el-upload__text">
                    将文件拖到此处，或 <em>点击上传</em>
                  </div>
                </el-upload>
              </el-form-item>
               <el-form-item label="封面图">
                <el-upload
                  class="cover-uploader"
                  action="#"
                  :show-file-list="false"
                  :auto-upload="false"
                  :on-change="onCoverFileChange"
                >
                  <img v-if="coverPreview" :src="coverPreview" class="cover-image" />
                  <el-icon v-else class="cover-uploader-icon"><Plus /></el-icon>
                </el-upload>
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-divider />
          <div style="text-align: center">
            <el-button type="primary" size="large" :loading="loading.upload" @click="handleUploadResource" style="width: 200px">
               确认发布
            </el-button>
          </div>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <!-- Version Drawer -->
    <el-drawer
      v-model="drawerVisible"
      title="版本更新与迭代记录"
      size="40%"
    >
      <div v-loading="loading.versions">
        <el-card shadow="never" class="version-upload-card">
          <template #header>
            <div class="card-header"><span>上传新版本</span></div>
          </template>
          <el-form :model="versionForm" label-position="top">
            <el-form-item label="迭代说明">
              <el-input v-model="versionForm.changeLog" type="textarea" placeholder="描述此版本的更新内容" />
            </el-form-item>
            <el-form-item label="最新版本文件">
               <el-upload
                  ref="versionUploadRef"
                  action="#"
                  :auto-upload="false"
                  :on-change="onVersionFileChange"
                  :limit="1"
                >
                  <el-button type="primary" :icon="Upload">选择新文件</el-button>
                </el-upload>
            </el-form-item>
            <el-button type="primary" style="width: 100%" @click="handleUploadVersion">发布新版本</el-button>
          </el-form>
        </el-card>

        <el-timeline style="margin-top: 24px">
          <el-timeline-item
            v-for="v in versions"
            :key="v.id"
            :timestamp="v.createdAt"
            placement="top"
            type="primary"
          >
            <el-card>
              <h4>版本 v{{ v.versionNo }}</h4>
              <p>{{ v.fileName }}</p>
              <el-tag v-if="v.changeLog" size="small" type="info">{{ v.changeLog }}</el-tag>
            </el-card>
          </el-timeline-item>
          <el-empty v-if="!versions.length" description="暂无历史版本" />
        </el-timeline>
      </div>
    </el-drawer>

    <!-- Edit Dialog -->
    <el-dialog v-model="editVisible" title="修改资源基本信息" width="50%">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="资源标题">
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-form-item label="内容简介">
          <el-input v-model="editForm.summary" type="textarea" rows="3" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所属学科">
              <el-input v-model="editForm.subjectName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标签">
              <el-input v-model="editForm.tags" placeholder="逗号分隔" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="考试类型">
              <el-input v-model="editForm.examType" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="竞赛类别">
              <el-input v-model="editForm.competitionType" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="更新资源文件">
              <el-upload
                action="#"
                :auto-upload="false"
                :on-change="onEditResourceFileChange"
                :limit="1"
              >
                <el-button type="info" plain size="small">选择新文件 (可选)</el-button>
              </el-upload>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="修改封面图">
              <el-upload
                class="cover-uploader"
                action="#"
                :show-file-list="false"
                :auto-upload="false"
                :on-change="onEditCoverFileChange"
              >
                <img v-if="editCoverPreview" :src="editCoverPreview" class="cover-image" />
                <el-icon v-else class="cover-uploader-icon"><Plus /></el-icon>
              </el-upload>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading.edit" @click="submitEdit">保存修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.resource-center-container {
  padding: 24px;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.manage-header {
  margin-bottom: 10px;
}

.search-bar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.upload-form {
  padding: 20px 0;
}

.cover-uploader {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  width: 120px;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-uploader:hover {
  border-color: #409eff;
}

.cover-uploader-icon {
  font-size: 28px;
  color: #8c939d;
}

.cover-image {
  width: 120px;
  height: 120px;
  display: block;
  object-fit: cover;
}

.version-upload-card {
  background-color: #fafafa;
}
</style>
