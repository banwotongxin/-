<template>
  <div class="page-container">
    <div class="page-header">
      <h2>资料归档</h2>
      <p class="muted">选择已结束活动，上传活动资料与总结并设置可见范围</p>
    </div>

    <div class="filter-bar">
      <el-select
        v-model="activityId"
        placeholder="选择已结束活动"
        filterable
        style="width: 320px"
        @change="handleActivityChange"
      >
        <el-option
          v-for="item in activities"
          :key="item.id"
          :label="`${item.title}（${labelOf(ACTIVITY_STATUS, item.status)}）`"
          :value="item.id"
        />
      </el-select>
      <el-button :disabled="!activityId" @click="loadArchives">刷新归档列表</el-button>
      <span v-if="activityId" class="muted">共 {{ total }} 条归档</span>
    </div>

    <el-alert v-if="!activityId && !loading" title="暂无可归档活动，活动关闭后才能上传归档资料" type="info" show-icon :closable="false" class="mb-16" />

    <el-card v-if="selectedActivity" shadow="never" class="mb-16">
      <el-descriptions :column="4" border>
        <el-descriptions-item label="活动">{{ selectedActivity.title }}</el-descriptions-item>
        <el-descriptions-item label="社团">{{ selectedActivity.clubName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ labelOf(ACTIVITY_STATUS, selectedActivity.status) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ formatDateTime(selectedActivity.endTime) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="mb-16">
      <template #header>
        <span class="text-strong">上传归档资料</span>
      </template>
      <el-form label-width="100px" :disabled="!activityId">
        <el-form-item label="活动资料" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".jpg,.jpeg,.png,.pdf,.zip,.docx"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :on-exceed="handleExceed"
          >
            <el-button :disabled="!activityId">选择文件</el-button>
            <template #tip>
              <div class="muted">支持 jpg/jpeg/png/pdf/zip/docx，单文件不超过 20 MB，单活动最多 100 个归档</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="活动总结" required>
          <el-input
            v-model="summary"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="请输入活动总结（不超过 1000 字）"
          />
        </el-form-item>
        <el-form-item label="可见范围">
          <el-select v-model="visibility" style="width: 220px">
            <el-option label="公开" value="PUBLIC" />
            <el-option label="仅社团成员" value="CLUB_ONLY" />
            <el-option label="仅管理员" value="PRIVATE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="uploading" :disabled="!activityId" @click="submitUpload">
            上传归档
          </el-button>
          <el-progress
            v-if="uploading"
            :percentage="uploadPercent"
            :stroke-width="8"
            style="width: 220px; margin-left: 16px"
          />
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert v-if="errorText" type="error" :title="errorText" show-icon :closable="false" class="mb-16">
      <el-button size="small" class="mt-8" @click="loadArchives">重新加载</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="archives" border>
      <el-table-column label="版本" width="80">
        <template #default="{ row }">v{{ row.version || 1 }}</template>
      </el-table-column>
      <el-table-column label="文件名" min-width="180">
        <template #default="{ row }">{{ row.originalName || '归档文件' }}</template>
      </el-table-column>
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
      </el-table-column>
      <el-table-column label="活动总结" min-width="220">
        <template #default="{ row }">{{ row.summary || '—' }}</template>
      </el-table-column>
      <el-table-column label="可见范围" width="120">
        <template #default="{ row }">{{ labelOf(VISIBILITY, row.visibility) }}</template>
      </el-table-column>
      <el-table-column label="上传人" width="120">
        <template #default="{ row }">{{ row.uploaderName || '—' }}</template>
      </el-table-column>
      <el-table-column label="上传时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-if="isImage(row)" type="primary" link @click="openPreview(row)">预览</el-button>
          <el-button type="primary" link :loading="downloadingId === row.id" @click="download(row)">
            下载
          </el-button>
          <el-button type="danger" link @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty v-if="!loading" description="暂无归档资料" />
      </template>
    </el-table>

    <el-pagination
      v-if="total > 0"
      class="mt-16"
      background
      layout="total, sizes, prev, pager, next"
      :total="total"
      :current-page="pageNum"
      :page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />

    <el-dialog v-model="previewVisible" :title="previewName" width="720px" @closed="closePreview">
      <img v-if="previewUrl" :src="previewUrl" alt="归档图片预览" style="max-width: 100%" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listActivities } from '../../api/activity'
import { listManagedClubs } from '../../api/club'
import { deleteArchive, downloadFile, listArchives, uploadArchive } from '../../api/archive'
import {
  ACTIVITY_STATUS,
  VISIBILITY,
  downloadBlob,
  extractPage,
  formatDateTime,
  formatSize,
  labelOf
} from '../../utils/format'
import { notifyError } from '../../utils/feedback'
import { isPlatformAdmin } from '../../store/session'

const activities = ref([])
const activityId = ref(null)
const archives = ref([])
const loading = ref(false)
const errorText = ref('')
const uploading = ref(false)
const uploadPercent = ref(0)
const downloadingId = ref(null)
const summary = ref('')
const visibility = ref('PUBLIC')
const selectedFile = ref(null)
const uploadRef = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const previewVisible = ref(false)
const previewUrl = ref('')
const previewName = ref('')

const selectedActivity = computed(() => activities.value.find(
  (item) => String(item.id) === String(activityId.value)
) || null)

async function loadActivities() {
  try {
    let clubIds = []
    if (!isPlatformAdmin()) {
      const managed = await listManagedClubs()
      const clubs = Array.isArray(managed) ? managed : extractPage(managed).records
      clubIds = clubs.map((club) => club.id)
      if (!clubIds.length) {
        activities.value = []
        activityId.value = null
        return
      }
    }
    const targets = clubIds.length ? clubIds : [null]
    const tasks = []
    targets.forEach((clubId) => {
      ['CLOSED', 'ARCHIVED'].forEach((status) => {
        tasks.push(listActivities({ pageNum: 1, pageSize: 100, clubId, status }))
      })
    })
    const results = await Promise.all(tasks)
    const merged = []
    const seen = {}
    results.forEach((data) => {
      extractPage(data).records.forEach((item) => {
        if (!seen[item.id]) {
          seen[item.id] = true
          merged.push(item)
        }
      })
    })
    activities.value = merged
    const currentExists = merged.some((item) => String(item.id) === String(activityId.value))
    if (!currentExists) {
      activityId.value = merged.length ? merged[0].id : null
      pageNum.value = 1
    }
  } catch (err) {
    activities.value = []
    notifyError(err)
  }
}

async function loadArchives() {
  if (!activityId.value) {
    archives.value = []
    total.value = 0
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const data = await listArchives(activityId.value, { pageNum: pageNum.value, pageSize: pageSize.value })
    const result = extractPage(data)
    archives.value = result.records
    total.value = result.total
  } catch (err) {
    archives.value = []
    total.value = 0
    errorText.value = `${err.message || '加载归档列表失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
    notifyError(err)
  } finally {
    loading.value = false
  }
}

function handleActivityChange() {
  pageNum.value = 1
  loadArchives()
}

function handlePageChange(value) {
  pageNum.value = value
  loadArchives()
}

function handleSizeChange(value) {
  pageSize.value = value
  pageNum.value = 1
  loadArchives()
}

function handleFileChange(file) {
  if (file.raw && file.raw.size > 20 * 1024 * 1024) {
    ElMessage.warning('文件大小不能超过 20 MB')
    uploadRef.value.clearFiles()
    selectedFile.value = null
    return
  }
  selectedFile.value = file.raw
}

function handleFileRemove() {
  selectedFile.value = null
}

function handleExceed() {
  ElMessage.warning('单次只能上传一个文件，请先移除已选文件')
}

async function submitUpload() {
  if (uploading.value) return
  if (!selectedFile.value) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  if (!summary.value.trim()) {
    ElMessage.warning('请填写活动总结')
    return
  }
  uploading.value = true
  uploadPercent.value = 0
  const formData = new FormData()
  formData.append('file', selectedFile.value)
  formData.append('summary', summary.value.trim())
  formData.append('visibility', visibility.value)
  try {
    await uploadArchive(activityId.value, formData, (event) => {
      if (event.total) {
        uploadPercent.value = Math.round((event.loaded * 100) / event.total)
      }
    })
    ElMessage.success('归档上传成功')
    summary.value = ''
    selectedFile.value = null
    if (uploadRef.value) uploadRef.value.clearFiles()
    pageNum.value = 1
    await loadActivities()
    await loadArchives()
  } catch (err) {
    notifyError(err, loadArchives)
  } finally {
    uploading.value = false
    uploadPercent.value = 0
  }
}

function isImage(row) {
  return row.mimeType && String(row.mimeType).indexOf('image/') === 0
}

async function download(row) {
  downloadingId.value = row.id
  try {
    const response = await downloadFile(row.fileId)
    downloadBlob(response.data, row.originalName || `archive-${row.id}`)
  } catch (err) {
    notifyError(err)
  } finally {
    downloadingId.value = null
  }
}

async function openPreview(row) {
  try {
    const response = await downloadFile(row.fileId)
    closePreview()
    previewUrl.value = URL.createObjectURL(response.data)
    previewName.value = row.originalName || '图片预览'
    previewVisible.value = true
  } catch (err) {
    notifyError(err)
  }
}

function closePreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = ''
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.originalName || '归档文件'}」？删除后不可恢复。`,
      '删除归档',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteArchive(activityId.value, row.id)
    ElMessage.success('已删除归档资料')
    if (archives.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1
    }
    await Promise.all([loadActivities(), loadArchives()])
  } catch (err) {
    notifyError(err)
  }
}

onMounted(async () => {
  await loadActivities()
  await loadArchives()
})

onBeforeUnmount(closePreview)
</script>
