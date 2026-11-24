<template>
  <div class="page-container">
    <div class="page-header">
      <h2>我的报名</h2>
      <p class="muted">查看本人报名记录、审核结果与审核原因；活动进行中可直接签到，活动结束后未签到可申请补签，经活动负责人审核后生效</p>
    </div>

    <el-alert v-if="errorText" type="error" :title="errorText" show-icon :closable="false" class="mb-16">
      <el-button size="small" class="mt-8" @click="load">重新加载</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="records" border>
      <el-table-column label="活动" min-width="220">
        <template #default="{ row }">
          <el-link type="primary" @click="goDetail(row.activityId)">
            {{ row.activityTitle || `活动 #${row.activityId}` }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="报名状态" width="110">
        <template #default="{ row }">
          <el-tag :type="signupTagType(row.status)" size="small">
            {{ labelOf(SIGNUP_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="签到状态" width="130">
        <template #default="{ row }">
          <el-tag v-if="row.signed" type="success" size="small">已签到</el-tag>
          <el-tag v-else-if="row.supplementStatus" :type="supplementTagType(row.supplementStatus)" size="small">
            {{ SUPPLEMENT_STATUS[row.supplementStatus] }}
          </el-tag>
          <el-tag v-else type="info" size="small">未签到</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="签到时间" width="160">
        <template #default="{ row }">{{ row.signed ? formatDateTime(row.signedAt) : '—' }}</template>
      </el-table-column>
      <el-table-column label="审核原因" min-width="180">
        <template #default="{ row }">{{ row.reviewReason || '—' }}</template>
      </el-table-column>
      <el-table-column label="报名备注" min-width="180">
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
      <el-table-column label="报名时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="canSignin(row)"
            type="success"
            link
            @click="signin(row)"
          >
            签到
          </el-button>
          <el-button
            v-if="canApply(row)"
            type="primary"
            link
            @click="openApply(row)"
          >
            {{ row.supplementStatus === 'REJECTED' ? '重新申请补签' : '申请补签' }}
          </el-button>
          <el-button
            v-if="canCancel(row)"
            type="danger"
            link
            @click="cancel(row)"
          >
            撤销报名
          </el-button>
          <span v-if="!canSignin(row) && !canApply(row) && !canCancel(row)" class="muted">—</span>
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
        @current-change="load"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="applyVisible" title="申请补签" width="500px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="活动">
          <span class="text-strong">{{ applyRow.activityTitle }}</span>
        </el-form-item>
        <el-form-item label="补签时间（必填）">
          <el-date-picker
            v-model="applyForm.signedAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            :disabled-date="disableFutureDate"
            placeholder="选择补签时间"
            style="width: 100%"
          />
          <div class="muted mt-8">需晚于活动开始时间、不晚于活动结束时间，且早于当前时间</div>
        </el-form-item>
        <el-form-item label="补签原因（必填）">
          <el-input
            v-model="applyForm.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请说明未能在活动期间签到的原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelSignup, listMySignups } from '../api/signup'
import { applySignin, selfSignin } from '../api/signin'
import { SIGNUP_STATUS, extractPage, formatDateTime, labelOf, signupTagType } from '../utils/format'
import { notifyError } from '../utils/feedback'

const SUPPLEMENT_STATUS = {
  PENDING: '补签待审核',
  APPROVED: '补签已通过',
  REJECTED: '补签已驳回'
}

function supplementTagType(status) {
  if (status === 'APPROVED') return 'success'
  if (status === 'PENDING') return 'warning'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}

const router = useRouter()
const records = ref([])
const loading = ref(false)
const errorText = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const applyVisible = ref(false)
const applyRow = ref({})
const applyForm = ref({ signedAt: '', reason: '' })
const applying = ref(false)
const signing = ref(false)

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await listMySignups({ pageNum: pageNum.value, pageSize: pageSize.value })
    const page = extractPage(data)
    records.value = page.records
    total.value = page.total
  } catch (err) {
    records.value = []
    total.value = 0
    errorText.value = `${err.message || '加载报名记录失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
    notifyError(err)
  } finally {
    loading.value = false
  }
}

function onSizeChange() {
  pageNum.value = 1
  load()
}

function goDetail(activityId) {
  router.push(`/activities/${activityId}`)
}

async function cancel(row) {
  try {
    await ElMessageBox.confirm(`确认撤销《${row.activityTitle || '该活动'}》的报名？`, '撤销报名', {
      type: 'warning',
      confirmButtonText: '确认撤销',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await cancelSignup(row.id)
    ElMessage.success('报名已撤销')
    await load()
  } catch (err) {
    notifyError(err, load)
  }
}

function canSignin(row) {
  if (row.status !== 'APPROVED' || row.signed) return false
  if (row.activityStatus !== 'PUBLISHED' && row.activityStatus !== 'FULL') return false
  if (!row.activityStartTime || !row.activityEndTime) return false
  const now = Date.now()
  return new Date(row.activityStartTime).getTime() <= now && now <= new Date(row.activityEndTime).getTime()
}

function canApply(row) {
  if (row.status !== 'APPROVED' || row.signed) return false
  if (row.supplementStatus === 'PENDING' || row.supplementStatus === 'APPROVED') return false
  if (!row.activityEndTime) return false
  return new Date(row.activityEndTime).getTime() < Date.now()
}

function canCancel(row) {
  if (row.status !== 'PENDING' && row.status !== 'APPROVED') return false
  if (row.activityStatus === 'DRAFT' || row.activityStatus === 'CLOSED' || row.activityStatus === 'ARCHIVED') return false
  if (!row.activityStartTime) return true
  return new Date(row.activityStartTime).getTime() > Date.now()
}

async function signin(row) {
  if (signing.value) return
  try {
    await ElMessageBox.confirm(`确认签到《${row.activityTitle || '该活动'}》？签到后不可撤销。`, '活动签到', {
      type: 'info',
      confirmButtonText: '确认签到',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  signing.value = true
  try {
    await selfSignin(row.id)
    ElMessage.success('签到成功')
    await load()
  } catch (err) {
    notifyError(err, load)
  } finally {
    signing.value = false
  }
}

function isoOf(ts) {
  const d = new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function nowIso(offsetMinutes = 0) {
  return isoOf(Date.now() + offsetMinutes * 60 * 1000)
}

function disableFutureDate(date) {
  return date.getTime() > Date.now()
}

function openApply(row) {
  applyRow.value = row
  const fallback = Date.now() - 60 * 1000
  const end = row.activityEndTime ? new Date(row.activityEndTime).getTime() : null
  applyForm.value = { signedAt: isoOf(end ? Math.min(fallback, end) : fallback), reason: '' }
  applyVisible.value = true
}

async function submitApply() {
  if (applying.value) return
  const reason = applyForm.value.reason.trim()
  if (!applyForm.value.signedAt) {
    ElMessage.warning('请选择补签时间')
    return
  }
  if (!reason) {
    ElMessage.warning('请填写补签原因')
    return
  }
  const start = new Date(applyRow.value.activityStartTime).getTime()
  const at = new Date(applyForm.value.signedAt).getTime()
  if (at <= start) {
    ElMessage.warning('补签时间必须晚于活动开始时间')
    return
  }
  const end = applyRow.value.activityEndTime ? new Date(applyRow.value.activityEndTime).getTime() : null
  if (end && at > end) {
    ElMessage.warning('补签时间不能晚于活动结束时间')
    return
  }
  if (at >= Date.now()) {
    ElMessage.warning('补签时间必须早于当前时间')
    return
  }
  applying.value = true
  try {
    await applySignin(applyRow.value.activityId, { signedAt: applyForm.value.signedAt, reason })
    ElMessage.success('补签申请已提交，等待活动负责人审核')
    applyVisible.value = false
    await load()
  } catch (err) {
    notifyError(err)
  } finally {
    applying.value = false
  }
}

onMounted(load)
</script>
