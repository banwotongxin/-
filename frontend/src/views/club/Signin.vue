<template>
  <div class="page-container">
    <div class="page-header">
      <h2>签到管理</h2>
      <p class="muted">加载已通过报名名单登记现场签到；学生补签申请需在此审核，通过后自动生成补签记录</p>
    </div>

    <div class="filter-bar">
      <el-select v-model="activityId" placeholder="选择活动" filterable style="width: 300px" @change="onActivityChange">
        <el-option
          v-for="item in activities"
          :key="item.id"
          :label="`${item.title}（${labelOf(ACTIVITY_STATUS, item.status)}）`"
          :value="item.id"
        />
      </el-select>
      <el-button type="primary" :disabled="!activityId" :loading="exporting" @click="exportCsv">
        导出签到名单
      </el-button>
    </div>

    <el-alert v-if="!activityId && !loading" title="请选择需要登记签到的活动" type="info" show-icon :closable="false" class="mb-16" />
    <el-alert v-if="errorText" type="error" :title="errorText" show-icon :closable="false" class="mb-16">
      <el-button size="small" class="mt-8" @click="loadRoster">重新加载</el-button>
    </el-alert>
    <el-alert
      v-if="activityId && summaryReady && !summary.windowOpen"
      title="当前不在签到窗口内：仅已发布/已满员的活动在进行中可登记签到，活动结束后可通过补签申请处理"
      type="warning"
      show-icon
      :closable="false"
      class="mb-16"
    />

    <el-row v-if="activityId" :gutter="16" class="mb-16">
      <el-col :xs="24" :sm="6">
        <el-card shadow="never"><div class="stat-label">已通过报名</div><div class="stat-value">{{ summary.approvedCount }}</div></el-card>
      </el-col>
      <el-col :xs="24" :sm="6">
        <el-card shadow="never"><div class="stat-label">已签到</div><div class="stat-value">{{ summary.signedCount }}</div></el-card>
      </el-col>
      <el-col :xs="24" :sm="6">
        <el-card shadow="never"><div class="stat-label">签到率</div><div class="stat-value">{{ summary.signinRate }}%</div></el-card>
      </el-col>
      <el-col :xs="24" :sm="6">
        <el-card shadow="never"><div class="stat-label">待审补签</div><div class="stat-value">{{ summary.pendingApplicationCount }}</div></el-card>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="roster" border>
      <el-table-column label="学号" width="130">
        <template #default="{ row }">{{ row.studentNo || '—' }}</template>
      </el-table-column>
      <el-table-column label="姓名" width="110">
        <template #default="{ row }">{{ row.studentName || '—' }}</template>
      </el-table-column>
      <el-table-column label="报名状态" width="100">
        <template #default="{ row }">
          <el-tag :type="signupTagType(row.signupStatus)" size="small">
            {{ labelOf(SIGNUP_STATUS, row.signupStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="签到状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.signed ? 'success' : 'info'" size="small">
            {{ row.signed ? '已签到' : '未签到' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="签到方式" width="100">
        <template #default="{ row }">{{ row.signed ? labelOf(SIGNIN_SOURCE, row.source) : '—' }}</template>
      </el-table-column>
      <el-table-column label="补签申请" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.supplementStatus" :type="applicationTagType(row.supplementStatus)" size="small">
            {{ APPLICATION_STATUS[row.supplementStatus] || row.supplementStatus }}
          </el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="签到时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.signedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-if="!row.signed" type="primary" link :disabled="!summary.windowOpen" @click="openSign(row)">登记签到</el-button>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty v-if="!loading" description="该活动暂无已通过报名" />
      </template>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="rosterPageNum"
        v-model:page-size="rosterPageSize"
        :total="rosterTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadRoster"
        @size-change="onRosterSizeChange"
      />
    </div>

    <el-card v-if="activityId" shadow="never" class="mt-16">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span class="text-strong">补签申请</span>
          <el-select v-model="applicationStatus" style="width: 140px" @change="onApplicationStatusChange">
            <el-option label="待审核" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="全部" value="" />
          </el-select>
        </div>
      </template>
      <el-table v-loading="appLoading" :data="applications" border>
        <el-table-column label="学号" width="130">
          <template #default="{ row }">{{ row.studentNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="姓名" width="110">
          <template #default="{ row }">{{ row.studentName || '—' }}</template>
        </el-table-column>
        <el-table-column label="补签时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.signedAt) }}</template>
        </el-table-column>
        <el-table-column label="补签原因" min-width="180">
          <template #default="{ row }">{{ row.reason || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="applicationTagType(row.status)" size="small">
              {{ APPLICATION_STATUS[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核原因" min-width="160">
          <template #default="{ row }">{{ row.reviewReason || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button type="primary" link @click="approveApplication(row)">通过</el-button>
              <el-button type="danger" link @click="rejectApplication(row)">驳回</el-button>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="!appLoading" description="暂无补签申请" />
        </template>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="appPageNum"
          v-model:page-size="appPageSize"
          :total="appTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadApplications"
          @size-change="onAppSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="登记签到" width="480px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="签到对象">
          <span class="text-strong">{{ currentRow.studentName }}（{{ currentRow.studentNo }}）</span>
        </el-form-item>
        <el-form-item label="备注（选填）">
          <el-input
            v-model="signRemark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="可填写备注信息"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="signing" @click="submitSign">确认签到</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listActivities } from '../../api/activity'
import { listClubs } from '../../api/club'
import {
  exportSignins,
  getSigninSummary,
  listSigninApplications,
  listSignins,
  registerSignin,
  reviewSigninApplication
} from '../../api/signin'
import {
  ACTIVITY_STATUS,
  SIGNIN_SOURCE,
  SIGNUP_STATUS,
  downloadBlob,
  extractPage,
  formatDateTime,
  labelOf,
  signupTagType
} from '../../utils/format'
import { notifyError } from '../../utils/feedback'
import { session, isPlatformAdmin } from '../../store/session'

const activities = ref([])
const activityId = ref(null)
const roster = ref([])
const loading = ref(false)
const errorText = ref('')
const exporting = ref(false)
const summary = ref({ approvedCount: 0, signedCount: 0, pendingApplicationCount: 0, signinRate: 0, windowOpen: false })
const summaryReady = ref(false)
const rosterPageNum = ref(1)
const rosterPageSize = ref(10)
const rosterTotal = ref(0)

const dialogVisible = ref(false)
const currentRow = ref({})
const signRemark = ref('')
const signing = ref(false)

const applications = ref([])
const applicationStatus = ref('PENDING')
const appLoading = ref(false)
const appPageNum = ref(1)
const appPageSize = ref(10)
const appTotal = ref(0)

const APPLICATION_STATUS = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回'
}

function applicationTagType(status) {
  if (status === 'APPROVED') return 'success'
  if (status === 'PENDING') return 'warning'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}

async function loadSummary() {
  if (!activityId.value) {
    summary.value = { approvedCount: 0, signedCount: 0, pendingApplicationCount: 0, signinRate: 0, windowOpen: false }
    summaryReady.value = false
    return
  }
  try {
    summary.value = await getSigninSummary(activityId.value)
    summaryReady.value = true
  } catch (err) {
    notifyError(err)
  }
}

async function loadActivities() {
  try {
    let clubId = null
    if (!isPlatformAdmin()) {
      const data = await listClubs({ pageNum: 1, pageSize: 100 })
      const clubs = extractPage(data).records
      const owned = clubs.find((club) => String(club.ownerUserId) === String(session.user && session.user.id))
      clubId = owned ? owned.id : (clubs.length ? clubs[0].id : null)
    }
    const list = await listActivities({ pageNum: 1, pageSize: 100, clubId })
    activities.value = extractPage(list).records
    if (!activityId.value && activities.value.length) {
      activityId.value = activities.value[0].id
    }
  } catch (err) {
    activities.value = []
    notifyError(err)
  }
}

async function loadRoster() {
  if (!activityId.value) {
    roster.value = []
    rosterTotal.value = 0
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const data = await listSignins(activityId.value, {
      pageNum: rosterPageNum.value,
      pageSize: rosterPageSize.value
    })
    const page = extractPage(data)
    roster.value = page.records
    rosterTotal.value = page.total
  } catch (err) {
    roster.value = []
    rosterTotal.value = 0
    errorText.value = `${err.message || '加载签到名单失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
    notifyError(err)
  } finally {
    loading.value = false
  }
}

function onRosterSizeChange() {
  rosterPageNum.value = 1
  loadRoster()
}

function onActivityChange() {
  rosterPageNum.value = 1
  appPageNum.value = 1
  loadRoster()
  loadApplications()
  loadSummary()
}

async function loadApplications() {
  if (!activityId.value) {
    applications.value = []
    appTotal.value = 0
    return
  }
  appLoading.value = true
  try {
    const data = await listSigninApplications(activityId.value, {
      pageNum: appPageNum.value,
      pageSize: appPageSize.value,
      status: applicationStatus.value
    })
    const page = extractPage(data)
    applications.value = page.records
    appTotal.value = page.total
  } catch (err) {
    applications.value = []
    appTotal.value = 0
    notifyError(err)
  } finally {
    appLoading.value = false
  }
}

function onAppSizeChange() {
  appPageNum.value = 1
  loadApplications()
}

function onApplicationStatusChange() {
  appPageNum.value = 1
  loadApplications()
}

async function approveApplication(row) {
  try {
    await ElMessageBox.confirm(
      `确认通过 ${row.studentName} 的补签申请？通过后将按申请时间生成签到记录。`,
      '审核补签',
      { type: 'warning', confirmButtonText: '通过', cancelButtonText: '取消' }
    )
    await reviewSigninApplication(row.id, { decision: 'APPROVED' })
    ElMessage.success('补签申请已通过')
    await Promise.all([loadApplications(), loadRoster(), loadSummary()])
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') notifyError(err, loadApplications)
  }
}

async function rejectApplication(row) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请填写驳回原因', '驳回补签申请', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '驳回原因（必填）',
      inputValidator: (value) => (value && value.trim() ? true : '请填写驳回原因')
    })
    reason = result.value.trim()
  } catch (err) {
    return
  }
  try {
    await reviewSigninApplication(row.id, { decision: 'REJECTED', reason })
    ElMessage.success('补签申请已驳回')
    await Promise.all([loadApplications(), loadSummary()])
  } catch (err) {
    notifyError(err, loadApplications)
  }
}

function openSign(row) {
  currentRow.value = row
  signRemark.value = ''
  dialogVisible.value = true
}

async function submitSign() {
  if (signing.value) return
  const remark = signRemark.value.trim()
  signing.value = true
  const payload = { signupId: currentRow.value.signupId }
  if (remark) payload.remark = remark
  try {
    await registerSignin(activityId.value, payload)
    ElMessage.success('签到已登记')
    dialogVisible.value = false
    await Promise.all([loadRoster(), loadSummary()])
  } catch (err) {
    notifyError(err, loadRoster)
  } finally {
    signing.value = false
  }
}

async function exportCsv() {
  if (!activityId.value || exporting.value) return
  exporting.value = true
  try {
    const response = await exportSignins(activityId.value)
    const disposition = response.headers['content-disposition'] || ''
    let filename = `signins-${activityId.value}.csv`
    const match = disposition.match(/filename="?([^";]+)"?/)
    if (match && match[1]) filename = match[1]
    downloadBlob(response.data, filename)
    ElMessage.success('签到名单已导出')
  } catch (err) {
    notifyError(err)
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  await loadActivities()
  await Promise.all([loadRoster(), loadApplications(), loadSummary()])
})
</script>
