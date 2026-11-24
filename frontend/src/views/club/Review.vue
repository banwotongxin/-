<template>
  <div class="page-container">
    <div class="page-header">
      <h2>报名审核</h2>
      <p class="muted">按活动查看报名名单，支持姓名/学号检索、汇总统计与审核留痕（驳回原因必填）</p>
    </div>

    <div class="filter-bar">
      <el-select
        v-model="clubId"
        placeholder="选择社团"
        filterable
        :clearable="isPlatformAdmin()"
        style="width: 220px"
        @change="onClubChange"
      >
        <el-option v-for="club in clubs" :key="club.id" :label="club.name" :value="club.id" />
      </el-select>
      <el-select
        v-model="activityId"
        placeholder="选择活动"
        filterable
        remote
        :remote-method="searchActivities"
        :loading="activityLoading"
        :disabled="!isPlatformAdmin() && !clubId"
        style="width: 300px"
        @change="onActivityChange"
      >
        <el-option
          v-for="item in activities"
          :key="item.id"
          :label="`${item.title}（${labelOf(ACTIVITY_STATUS, item.status)}）`"
          :value="item.id"
        />
      </el-select>
      <el-input
        v-model="keyword"
        placeholder="姓名/学号"
        clearable
        style="width: 180px"
        @keyup.enter="search"
      />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 150px" @change="search">
        <el-option
          v-for="(label, value) in SIGNUP_STATUS"
          :key="value"
          :label="label"
          :value="value"
        />
      </el-select>
      <el-button type="primary" :disabled="!activityId" @click="search">查询</el-button>
    </div>

    <el-alert
      v-if="!isPlatformAdmin() && !clubs.length && !loading"
      title="暂无可管理社团，无法审核报名"
      type="info"
      show-icon
      :closable="false"
      class="mb-16"
    />
    <el-alert v-else-if="!activityId && !loading" title="请选择需要审核报名的活动" type="info" show-icon :closable="false" class="mb-16" />
    <el-alert v-if="errorText" type="error" :title="errorText" show-icon :closable="false" class="mb-16">
      <el-button size="small" class="mt-8" @click="loadSignups">重新加载</el-button>
    </el-alert>

    <el-card v-if="activityInfo" shadow="never" class="mb-16">
      <div class="toolbar" style="margin-bottom: 8px">
        <div class="text-strong">{{ activityInfo.title }}</div>
        <div>
          <el-tag v-if="summary && !summary.signupOpen" type="info" size="small" style="margin-right: 8px">报名未开放</el-tag>
          <el-tag v-if="isFull" type="warning" size="small">名额已满</el-tag>
        </div>
      </div>
      <el-descriptions v-if="summary" :column="6" size="small" border class="mb-16">
        <el-descriptions-item label="待审核">{{ summary.pendingCount }}</el-descriptions-item>
        <el-descriptions-item label="已通过">{{ summary.approvedCount }}</el-descriptions-item>
        <el-descriptions-item label="已驳回">{{ summary.rejectedCount }}</el-descriptions-item>
        <el-descriptions-item label="已撤销">{{ summary.cancelledCount }}</el-descriptions-item>
        <el-descriptions-item label="剩余名额">{{ summary.remainingCapacity }}</el-descriptions-item>
        <el-descriptions-item label="报名窗口">
          {{ formatDateTime(summary.signupStart) }} ~ {{ formatDateTime(summary.signupEnd) }}
        </el-descriptions-item>
      </el-descriptions>
      <div class="muted" style="margin-bottom: 6px">
        已通过 {{ summary ? summary.approvedCount : (activityInfo.signedCount || 0) }} / 容量 {{ summary ? summary.capacity : activityInfo.capacity }} 人
      </div>
      <el-progress
        :percentage="approvedPercent"
        :status="approvedPercent >= 100 ? 'warning' : 'success'"
      />
    </el-card>

    <el-table v-loading="loading" :data="records" border>
      <el-table-column label="姓名" width="110">
        <template #default="{ row }">{{ row.studentName || '—' }}</template>
      </el-table-column>
      <el-table-column label="学号" width="130">
        <template #default="{ row }">{{ row.studentNo || '—' }}</template>
      </el-table-column>
      <el-table-column label="报名备注" min-width="150">
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
      <el-table-column label="报名时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="signupTagType(row.status)" size="small">
            {{ labelOf(SIGNUP_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审核时间" width="160">
        <template #default="{ row }">{{ row.reviewedAt ? formatDateTime(row.reviewedAt) : '—' }}</template>
      </el-table-column>
      <el-table-column label="审核人" width="110">
        <template #default="{ row }">{{ row.reviewerName || '—' }}</template>
      </el-table-column>
      <el-table-column label="审核原因" min-width="150">
        <template #default="{ row }">{{ row.reviewReason || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button type="success" link :disabled="isFull" @click="approve(row)">通过</el-button>
            <el-button type="danger" link @click="openReject(row)">驳回</el-button>
          </template>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty v-if="!loading" description="暂无报名记录" />
      </template>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadSignups"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="rejectVisible" title="驳回报名" width="480px" :close-on-click-modal="false">
      <p class="muted mb-16">驳回原因将展示给学生，请填写明确说明。</p>
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        placeholder="请填写驳回原因（必填）"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="reviewing" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getActivity, listActivities } from '../../api/activity'
import { listManagedClubs } from '../../api/club'
import { getActivitySignupSummary, listActivitySignups, reviewSignup } from '../../api/signup'
import {
  ACTIVITY_STATUS,
  SIGNUP_STATUS,
  extractPage,
  formatDateTime,
  labelOf,
  signupTagType
} from '../../utils/format'
import { notifyError } from '../../utils/feedback'
import { isPlatformAdmin } from '../../store/session'

const clubs = ref([])
const clubId = ref(null)
const activities = ref([])
const activityLoading = ref(false)
const activityId = ref(null)
const activityInfo = ref(null)
const summary = ref(null)
const records = ref([])
const loading = ref(false)
const errorText = ref('')
const keyword = ref('')
const statusFilter = ref('PENDING')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const rejectVisible = ref(false)
const rejectRow = ref(null)
const rejectReason = ref('')
const reviewing = ref(false)

let activityRequestId = 0

const isFull = computed(() => Boolean(summary.value && summary.value.remainingCapacity <= 0))

const approvedPercent = computed(() => {
  const data = summary.value
  const capacity = data ? data.capacity : (activityInfo.value ? activityInfo.value.capacity : 0)
  const approved = data ? data.approvedCount : (activityInfo.value ? activityInfo.value.signedCount : 0)
  if (!capacity) return 0
  return Math.min(Math.round((Number(approved || 0) / Number(capacity)) * 100), 100)
})

async function loadClubs() {
  try {
    const data = await listManagedClubs()
    clubs.value = Array.isArray(data) ? data : extractPage(data).records
    if (!isPlatformAdmin() && clubs.value.length) {
      clubId.value = clubs.value[0].id
    }
  } catch (err) {
    clubs.value = []
    notifyError(err)
  }
}

async function searchActivities(keywordText = '') {
  if (!isPlatformAdmin() && !clubId.value) {
    activities.value = []
    return
  }
  const requestId = ++activityRequestId
  activityLoading.value = true
  try {
    const data = await listActivities({
      pageNum: 1,
      pageSize: 50,
      clubId: clubId.value,
      keyword: keywordText
    })
    if (requestId !== activityRequestId) return
    activities.value = extractPage(data).records
  } catch (err) {
    if (requestId === activityRequestId) {
      activities.value = []
      notifyError(err)
    }
  } finally {
    if (requestId === activityRequestId) {
      activityLoading.value = false
    }
  }
}

async function loadSignups() {
  if (!activityId.value) {
    records.value = []
    total.value = 0
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const data = await listActivitySignups(activityId.value, {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: statusFilter.value,
      keyword: keyword.value.trim()
    })
    const page = extractPage(data)
    records.value = page.records
    total.value = page.total
  } catch (err) {
    records.value = []
    total.value = 0
    errorText.value = `${err.message || '加载报名名单失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
    notifyError(err)
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  if (!activityId.value) {
    summary.value = null
    return
  }
  try {
    summary.value = await getActivitySignupSummary(activityId.value)
  } catch (err) {
    summary.value = null
  }
}

async function refreshActivityInfo() {
  if (!activityId.value) return
  try {
    activityInfo.value = await getActivity(activityId.value)
  } catch (err) {
    notifyError(err)
  }
}

async function reloadAll() {
  await Promise.all([refreshActivityInfo(), loadSummary(), loadSignups()])
}

async function onClubChange() {
  activityId.value = null
  activityInfo.value = null
  summary.value = null
  records.value = []
  total.value = 0
  pageNum.value = 1
  await searchActivities('')
  if (activities.value.length) {
    activityId.value = activities.value[0].id
    activityInfo.value = activities.value[0]
    await Promise.all([loadSignups(), loadSummary()])
  }
}

async function onActivityChange() {
  pageNum.value = 1
  activityInfo.value = activities.value.find((item) => item.id === activityId.value) || null
  await Promise.all([loadSignups(), loadSummary()])
}

function search() {
  if (!activityId.value) {
    ElMessage.warning('请先选择活动')
    return
  }
  pageNum.value = 1
  loadSignups()
}

function onSizeChange() {
  pageNum.value = 1
  loadSignups()
}

async function approve(row) {
  if (isFull.value) {
    ElMessage.warning('名额已满，无法再通过新的报名')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认通过 ${row.studentName || '该学生'} 的报名？通过后将占用活动名额。`,
      '通过报名',
      { type: 'success', confirmButtonText: '确认通过', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await reviewSignup(row.id, { decision: 'APPROVED' })
    ElMessage.success('报名已通过')
    await reloadAll()
  } catch (err) {
    notifyError(err, reloadAll)
  }
}

function openReject(row) {
  rejectRow.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

async function submitReject() {
  if (reviewing.value) return
  const reason = rejectReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  reviewing.value = true
  try {
    await reviewSignup(rejectRow.value.id, { decision: 'REJECTED', reason })
    ElMessage.success('报名已驳回')
    rejectVisible.value = false
    await reloadAll()
  } catch (err) {
    notifyError(err, reloadAll)
  } finally {
    reviewing.value = false
  }
}

onMounted(async () => {
  await loadClubs()
  if (!isPlatformAdmin() && !clubs.value.length) {
    return
  }
  await searchActivities('')
  if (!activityId.value && activities.value.length) {
    activityId.value = activities.value[0].id
  }
  if (activityId.value) {
    activityInfo.value = activities.value.find((item) => item.id === activityId.value) || null
    await Promise.all([loadSignups(), loadSummary()])
  }
})
</script>
