<template>
  <div class="page-container" v-loading="loading">
    <div class="toolbar">
      <el-button @click="router.back()">返回</el-button>
    </div>

    <el-alert v-if="errorText" type="error" :title="errorText" show-icon :closable="false" class="mb-16">
      <el-button size="small" class="mt-8" @click="loadAll">重新加载</el-button>
    </el-alert>

    <template v-else-if="activity">
      <div class="toolbar">
        <h2 class="card-title">{{ activity.title }}</h2>
        <el-tag :type="activityTagType(activity.status)">{{ labelOf(ACTIVITY_STATUS, activity.status) }}</el-tag>
      </div>

      <div v-if="activity.tags && activity.tags.length" style="display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px">
        <el-tag v-for="tag in activity.tags" :key="tag" size="small" type="warning">{{ tag }}</el-tag>
      </div>

      <el-alert
        v-if="activity.takedownReason"
        type="error"
        show-icon
        :closable="false"
        class="mb-16"
        :title="`该活动已被平台下架：${activity.takedownReason}`"
      />

      <el-descriptions :column="2" border>
        <el-descriptions-item label="所属社团">{{ activity.clubName || '未知社团' }}</el-descriptions-item>
        <el-descriptions-item label="活动地点">{{ activity.location || '待定' }}</el-descriptions-item>
        <el-descriptions-item label="联系方式">{{ activity.contact || '—' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(activity.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="活动时间">
          {{ formatDateTime(activity.startTime) }} 至 {{ formatDateTime(activity.endTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="报名时间">
          {{ formatDateTime(activity.signupStart) }} 至 {{ formatDateTime(activity.signupEnd) }}
        </el-descriptions-item>
        <el-descriptions-item label="人数上限">{{ activity.capacity }} 人</el-descriptions-item>
        <el-descriptions-item label="已通过人数">{{ activity.signedCount || 0 }} 人</el-descriptions-item>
        <el-descriptions-item label="剩余名额">
          <span :class="remaining === 0 ? 'text-danger' : 'text-success'">{{ remaining }} 人</span>
        </el-descriptions-item>
        <el-descriptions-item label="报名窗口">
          <el-tag :type="activity.signupOpen ? 'success' : 'info'" size="small">
            {{ activity.signupOpen ? '开放中' : '未开放 / 已结束' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="活动简介" :span="2">
          <div style="white-space: pre-wrap; line-height: 1.8">{{ activity.summary || '暂无简介' }}</div>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">我的报名</el-divider>

      <div v-if="isStudent">
        <el-alert
          v-if="activeSignup"
          :title="`当前报名状态：${labelOf(SIGNUP_STATUS, activeSignup.status)}`"
          :type="activeSignup.status === 'APPROVED' ? 'success' : 'warning'"
          show-icon
          :closable="false"
          class="mb-16"
        >
          <div v-if="activeSignup.signed">签到状态：已签到（{{ formatDateTime(activeSignup.signedAt) }}）</div>
          <div v-if="activeSignup.reviewReason">审核原因：{{ activeSignup.reviewReason }}</div>
          <div v-else-if="activeSignup.remark">报名备注：{{ activeSignup.remark }}</div>
        </el-alert>

        <el-alert
          v-else-if="blockReason"
          :title="blockReason"
          type="info"
          show-icon
          :closable="false"
          class="mb-16"
        />

        <div class="detail-actions">
          <el-button
            v-if="!activeSignup"
            type="primary"
            :disabled="!!blockReason"
            @click="openSignupDialog"
          >
            提交报名
          </el-button>
          <el-button
            v-if="canSignin"
            type="success"
            :loading="signing"
            @click="signinNow"
          >
            活动签到
          </el-button>
          <el-button
            v-if="activeSignup && canCancelSignup"
            type="danger"
            plain
            :loading="cancelling"
            @click="cancelSignupItem"
          >
            撤销报名
          </el-button>
        </div>
        <p v-if="!activeSignup && blockReason" class="muted mt-8">报名条件未满足：{{ blockReason }}</p>
        <p v-if="canReapply" class="muted mt-8">上次报名已结束，如仍符合条件可重新提交报名。</p>
      </div>
      <el-alert
        v-else
        title="当前账号无学生报名权限，可前往社团管理或平台管理完成审核、签到与统计工作"
        type="info"
        show-icon
        :closable="false"
      />

      <template v-if="archives.length">
        <el-divider content-position="left">活动归档资料</el-divider>
        <el-table :data="archives" border>
          <el-table-column label="版本" width="80">
            <template #default="{ row }">v{{ row.version || 1 }}</template>
          </el-table-column>
          <el-table-column label="文件名" min-width="200">
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
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button v-if="isImage(row)" type="primary" link @click="openPreview(row)">预览</el-button>
              <el-button type="primary" link :loading="downloadingId === row.id" @click="downloadArchive(row)">
                下载
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </template>

    <el-empty v-else-if="!loading && !errorText" description="活动不存在或已被删除" />

    <el-dialog v-model="dialogVisible" title="提交报名" width="480px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="报名备注（选填）">
          <el-input
            v-model="remark"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="可填写参与意向、联系方式等信息"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitSignupForm">确认报名</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" :title="previewName" width="720px" @closed="closePreview">
      <img v-if="previewUrl" :src="previewUrl" alt="归档图片预览" style="max-width: 100%" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getActivity } from '../api/activity'
import { cancelSignup, getMySignup, submitSignup } from '../api/signup'
import { selfSignin } from '../api/signin'
import { downloadFile, listArchives } from '../api/archive'
import {
  ACTIVITY_STATUS,
  SIGNUP_STATUS,
  VISIBILITY,
  activityTagType,
  downloadBlob,
  extractPage,
  formatDateTime,
  formatSize,
  labelOf,
  remainingSlots
} from '../utils/format'
import { notifyError } from '../utils/feedback'
import { isStudent } from '../store/session'

const route = useRoute()
const router = useRouter()

const activity = ref(null)
const mySignup = ref(null)
const loading = ref(false)
const errorText = ref('')
const remark = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const cancelling = ref(false)
const signing = ref(false)
const archives = ref([])
const downloadingId = ref(null)
const previewVisible = ref(false)
const previewUrl = ref('')
const previewName = ref('')

const remaining = computed(() => remainingSlots(activity.value))
const activeSignup = computed(() => {
  const status = mySignup.value && mySignup.value.status
  return status === 'PENDING' || status === 'APPROVED' ? mySignup.value : null
})
const canReapply = computed(() => {
  const status = mySignup.value && mySignup.value.status
  return status === 'REJECTED' || status === 'CANCELLED'
})
const canSignin = computed(() => {
  const signup = activeSignup.value
  const item = activity.value
  if (!signup || signup.status !== 'APPROVED' || signup.signed) return false
  if (!item || (item.status !== 'PUBLISHED' && item.status !== 'FULL')) return false
  if (!item.startTime || !item.endTime) return false
  const now = Date.now()
  return new Date(item.startTime).getTime() <= now && now <= new Date(item.endTime).getTime()
})
const canCancelSignup = computed(() => {
  const signup = activeSignup.value
  const item = activity.value
  if (!signup) return false
  if (!item) return true
  if (item.status === 'DRAFT' || item.status === 'CLOSED' || item.status === 'ARCHIVED') return false
  if (!item.startTime) return true
  return new Date(item.startTime).getTime() > Date.now()
})
const blockReason = computed(() => {
  const item = activity.value
  if (!item) return ''
  if (item.status === 'DRAFT') return '活动尚未发布'
  if (item.status === 'CLOSED' || item.status === 'ARCHIVED') return '活动已关闭，报名已结束'
  if (item.status === 'FULL' || remaining.value === 0) return '名额已满，暂无法报名'
  if (!item.signupOpen) return '当前不在报名时间窗口内'
  return ''
})

async function loadActivity() {
  const data = await getActivity(route.params.id)
  activity.value = data
}

async function loadMySignup() {
  if (!isStudent()) {
    mySignup.value = null
    return
  }
  try {
    const data = await getMySignup(route.params.id)
    mySignup.value = data || null
  } catch (err) {
    mySignup.value = null
  }
}

async function loadArchives() {
  try {
    const data = await listArchives(route.params.id, { pageNum: 1, pageSize: 100 })
    archives.value = extractPage(data).records
  } catch (err) {
    archives.value = []
  }
}

function isImage(row) {
  return row.mimeType && String(row.mimeType).indexOf('image/') === 0
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

async function downloadArchive(row) {
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

async function loadAll() {
  loading.value = true
  errorText.value = ''
  try {
    await loadActivity()
    await loadMySignup()
    await loadArchives()
  } catch (err) {
    activity.value = null
    errorText.value = `${err.message || '加载活动详情失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
    notifyError(err)
  } finally {
    loading.value = false
  }
}

function openSignupDialog() {
  remark.value = ''
  dialogVisible.value = true
}

async function submitSignupForm() {
  if (submitting.value) return
  submitting.value = true
  try {
    await submitSignup(route.params.id, remark.value.trim())
    ElMessage.success('报名已提交，等待社团管理员审核')
    dialogVisible.value = false
    await loadAll()
  } catch (err) {
    dialogVisible.value = false
    notifyError(err, loadAll)
  } finally {
    submitting.value = false
  }
}

async function signinNow() {
  const signup = activeSignup.value
  if (!signup || signing.value) return
  try {
    await ElMessageBox.confirm(`确认签到《${activity.value.title}》？签到后不可撤销。`, '活动签到', {
      type: 'info',
      confirmButtonText: '确认签到',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  signing.value = true
  try {
    await selfSignin(signup.id)
    ElMessage.success('签到成功')
    await loadAll()
  } catch (err) {
    notifyError(err, loadAll)
  } finally {
    signing.value = false
  }
}

async function cancelSignupItem() {
  try {
    await ElMessageBox.confirm('撤销后如仍需参加需重新报名，确认撤销？', '撤销报名', {
      type: 'warning',
      confirmButtonText: '确认撤销',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  cancelling.value = true
  try {
    await cancelSignup(mySignup.value.id)
    ElMessage.success('报名已撤销')
    await loadAll()
  } catch (err) {
    notifyError(err, loadAll)
  } finally {
    cancelling.value = false
  }
}

onMounted(loadAll)
onBeforeUnmount(closePreview)
watch(() => route.params.id, (value) => {
  if (value) loadAll()
})
</script>
