<template>
  <div class="page-container">
    <div class="page-header">
      <h2>社团活动管理</h2>
      <p class="muted">维护本社团活动：创建草稿、编辑信息、发布活动与关闭报名</p>
    </div>

    <div class="filter-bar">
      <el-select
        v-if="platformAdmin"
        v-model="clubId"
        placeholder="选择社团"
        style="width: 220px"
        @change="onClubChange"
      >
        <el-option v-for="club in clubs" :key="club.id" :label="`${club.name}（${labelOf(CLUB_STATUS, club.status)}）`" :value="club.id" />
      </el-select>
      <el-select v-model="statusFilter" placeholder="全部状态" clearable @change="search">
        <el-option
          v-for="(label, value) in ACTIVITY_STATUS"
          :key="value"
          :label="label"
          :value="value"
        />
      </el-select>
      <el-button type="primary" :disabled="!clubId" @click="openCreate">新建活动</el-button>
    </div>

    <el-alert v-if="!clubId && !loading" title="未找到可管理的社团，请先完成社团入驻或联系平台管理员" type="warning" show-icon :closable="false" class="mb-16" />
    <el-alert v-if="errorText" type="error" :title="errorText" show-icon :closable="false" class="mb-16">
      <el-button size="small" class="mt-8" @click="load">重新加载</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="records" border>
      <el-table-column prop="title" label="活动标题" min-width="180" />
      <el-table-column label="活动时间" min-width="200">
        <template #default="{ row }">
          {{ formatDateTime(row.startTime) }} 至 {{ formatDateTime(row.endTime) }}
        </template>
      </el-table-column>
      <el-table-column label="报名窗口" min-width="200">
        <template #default="{ row }">
          {{ formatDateTime(row.signupStart) }} 至 {{ formatDateTime(row.signupEnd) }}
        </template>
      </el-table-column>
      <el-table-column label="名额" width="110">
        <template #default="{ row }">{{ row.signedCount || 0 }} / {{ row.capacity }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tooltip v-if="row.takedownReason" :content="`下架原因：${row.takedownReason}`" placement="top">
            <el-tag type="danger" size="small">已下架</el-tag>
          </el-tooltip>
          <el-tag v-else :type="activityTagType(row.status)" size="small">
            {{ labelOf(ACTIVITY_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="150">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="340" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="['DRAFT', 'PUBLISHED', 'FULL'].includes(row.status)"
            type="primary"
            link
            @click="openEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="row.status === 'DRAFT' && !row.takedownReason"
            type="success"
            link
            @click="publishItem(row)"
          >
            发布
          </el-button>
          <el-button
            v-if="platformAdmin && row.status === 'DRAFT' && row.takedownReason"
            type="success"
            link
            @click="restoreItem(row)"
          >
            恢复上架
          </el-button>
          <el-button v-if="row.status === 'DRAFT'" type="danger" link @click="deleteItem(row)">
            删除
          </el-button>
          <el-button
            v-if="platformAdmin && ['PUBLISHED', 'FULL'].includes(row.status)"
            type="danger"
            link
            @click="takedownItem(row)"
          >
            下架
          </el-button>
          <el-button
            v-if="['PUBLISHED', 'FULL'].includes(row.status)"
            type="warning"
            link
            @click="openClose(row)"
          >
            关闭
          </el-button>
          <el-button type="info" link @click="goDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty v-if="!loading" description="暂无活动，点击「新建活动」开始创建" />
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

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑活动' : '新建活动'"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-alert v-if="dialogError" :title="dialogError" type="error" show-icon :closable="false" class="mb-16" />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="活动标题" prop="title">
          <el-input v-model="form.title" maxlength="150" show-word-limit placeholder="请输入活动标题" />
        </el-form-item>
        <el-form-item label="活动标签">
          <el-select
            v-model="form.tags"
            multiple
            filterable
            allow-create
            default-first-option
            :multiple-limit="5"
            placeholder="输入后回车创建标签，最多 5 个"
            style="width: 100%"
          >
            <el-option v-for="tag in tagPresets" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动简介" prop="summary">
          <el-input
            v-model="form.summary"
            type="textarea"
            :rows="3"
            maxlength="4000"
            show-word-limit
            placeholder="请输入活动简介"
          />
        </el-form-item>
        <el-form-item label="活动地点">
          <el-input v-model="form.location" maxlength="150" placeholder="请输入活动地点" />
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="form.contact" maxlength="100" show-word-limit placeholder="用于学生咨询，如手机号或邮箱" />
        </el-form-item>
        <el-form-item label="活动开始" prop="startTime">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="活动结束" prop="endTime">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="报名开始" prop="signupStart">
          <el-date-picker
            v-model="form.signupStart"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择报名开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="报名截止" prop="signupEnd">
          <el-date-picker
            v-model="form.signupEnd"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择报名截止时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="人数上限" prop="capacity">
          <el-input-number v-model="form.capacity" :min="1" :max="100000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="closeVisible" title="关闭活动" width="480px" :close-on-click-modal="false">
      <p class="muted mb-16">关闭后学生无法继续报名，关闭原因会记录在操作审计中。</p>
      <el-input
        v-model="closeReason"
        type="textarea"
        :rows="3"
        maxlength="255"
        show-word-limit
        placeholder="请填写关闭原因（必填）"
      />
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="warning" :loading="closing" @click="submitClose">确认关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  closeActivity,
  createActivity,
  deleteActivity,
  listActivities,
  publishActivity,
  restoreActivity,
  takedownActivity,
  updateActivity
} from '../../api/activity'
import { listManagedClubs } from '../../api/club'
import {
  ACTIVITY_STATUS,
  CLUB_STATUS,
  activityTagType,
  extractPage,
  formatDateTime,
  labelOf,
  toDateInput,
  toIso
} from '../../utils/format'
import { notifyError } from '../../utils/feedback'
import { isPlatformAdmin } from '../../store/session'

const router = useRouter()
const clubs = ref([])
const clubId = ref(null)
const records = ref([])
const loading = ref(false)
const errorText = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const statusFilter = ref('')

const dialogVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const dialogError = ref('')
const formRef = ref(null)

const closeVisible = ref(false)
const closeRow = ref(null)
const closeReason = ref('')
const closing = ref(false)

const platformAdmin = computed(() => isPlatformAdmin())

const tagPresets = ['讲座', '文体', '志愿', '竞赛', '社团活动']

const form = reactive({
  title: '',
  tags: [],
  summary: '',
  location: '',
  contact: '',
  startTime: '',
  endTime: '',
  signupStart: '',
  signupEnd: '',
  capacity: 50
})

const rules = {
  title: [{ required: true, message: '请输入活动标题', trigger: 'blur' }],
  summary: [{ required: true, message: '请输入活动简介', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择活动开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择活动结束时间', trigger: 'change' }],
  signupStart: [{ required: true, message: '请选择报名开始时间', trigger: 'change' }],
  signupEnd: [{ required: true, message: '请选择报名截止时间', trigger: 'change' }],
  capacity: [{ required: true, message: '请填写人数上限', trigger: 'change' }]
}

async function loadClubs() {
  try {
    const data = await listManagedClubs()
    clubs.value = Array.isArray(data) ? data : extractPage(data).records
  } catch (err) {
    clubs.value = []
    notifyError(err)
  }
  clubId.value = clubs.value.length ? clubs.value[0].id : null
}

async function load() {
  if (!clubId.value) {
    records.value = []
    total.value = 0
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const data = await listActivities({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      clubId: clubId.value,
      status: statusFilter.value
    })
    const page = extractPage(data)
    records.value = page.records
    total.value = page.total
  } catch (err) {
    records.value = []
    total.value = 0
    errorText.value = `${err.message || '加载活动失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
    notifyError(err)
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}

function onClubChange() {
  pageNum.value = 1
  load()
}

function onSizeChange() {
  pageNum.value = 1
  load()
}

function resetForm() {
  form.title = ''
  form.tags = []
  form.summary = ''
  form.location = ''
  form.contact = ''
  form.startTime = ''
  form.endTime = ''
  form.signupStart = ''
  form.signupEnd = ''
  form.capacity = 50
  dialogError.value = ''
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  dialogError.value = ''
  form.title = row.title || ''
  form.tags = Array.isArray(row.tags) ? [...row.tags] : []
  form.summary = row.summary || ''
  form.location = row.location || ''
  form.contact = row.contact || ''
  form.startTime = toDateInput(row.startTime)
  form.endTime = toDateInput(row.endTime)
  form.signupStart = toDateInput(row.signupStart)
  form.signupEnd = toDateInput(row.signupEnd)
  form.capacity = Number(row.capacity || 1)
  dialogVisible.value = true
}

function checkTimes() {
  if (form.signupStart && form.signupEnd && form.signupStart >= form.signupEnd) {
    return '报名开始时间必须早于报名截止时间'
  }
  if (form.signupEnd && form.startTime && form.signupEnd > form.startTime) {
    return '报名截止时间不能晚于活动开始时间'
  }
  if (form.startTime && form.endTime && form.startTime >= form.endTime) {
    return '活动开始时间必须早于活动结束时间'
  }
  return ''
}

async function submitForm() {
  if (saving.value) return
  dialogError.value = ''
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  const timeError = checkTimes()
  if (timeError) {
    dialogError.value = timeError
    return
  }
  saving.value = true
  const payload = {
    title: form.title.trim(),
    tags: form.tags.map((tag) => String(tag).trim()).filter((tag) => tag),
    summary: form.summary.trim(),
    location: form.location.trim(),
    contact: form.contact.trim(),
    startTime: toIso(form.startTime),
    endTime: toIso(form.endTime),
    signupStart: toIso(form.signupStart),
    signupEnd: toIso(form.signupEnd),
    capacity: Number(form.capacity)
  }
  try {
    if (editingId.value) {
      await updateActivity(editingId.value, payload)
      ElMessage.success('活动已更新')
    } else {
      await createActivity({ ...payload, clubId: clubId.value })
      ElMessage.success('活动草稿已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (err) {
    dialogError.value = `${err.message || '保存失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
    if (err.status === 409) await load()
  } finally {
    saving.value = false
  }
}

async function publishItem(row) {
  try {
    await ElMessageBox.confirm(`确认发布活动《${row.title}》？发布后学生即可在报名窗口内报名。`, '发布活动', {
      type: 'info',
      confirmButtonText: '确认发布',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await publishActivity(row.id)
    ElMessage.success('活动已发布')
    await load()
  } catch (err) {
    notifyError(err, load)
  }
}

function openClose(row) {
  closeRow.value = row
  closeReason.value = ''
  closeVisible.value = true
}

async function submitClose() {
  if (closing.value) return
  const reason = closeReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写关闭原因')
    return
  }
  closing.value = true
  try {
    await closeActivity(closeRow.value.id, reason)
    ElMessage.success('活动已关闭')
    closeVisible.value = false
    await load()
  } catch (err) {
    notifyError(err, load)
  } finally {
    closing.value = false
  }
}

async function deleteItem(row) {
  try {
    await ElMessageBox.confirm(`删除草稿《${row.title}》？删除后不可恢复。`, '删除草稿', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await deleteActivity(row.id)
    ElMessage.success('草稿已删除')
    await load()
  } catch (err) {
    notifyError(err, load)
  }
}

async function takedownItem(row) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(`下架违规活动《${row.title}》，下架后将回到草稿状态。`, '下架活动', {
      type: 'warning',
      confirmButtonText: '确认下架',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '请填写下架原因（必填）',
      inputValidator: (value) => (value && value.trim() ? true : '请填写下架原因')
    })
    reason = result.value.trim()
  } catch (e) {
    return
  }
  try {
    await takedownActivity(row.id, reason)
    ElMessage.success('活动已下架')
    await load()
  } catch (err) {
    notifyError(err, load)
  }
}

async function restoreItem(row) {
  try {
    await ElMessageBox.confirm(`恢复上架《${row.title}》？恢复后学生可在报名窗口内继续报名。`, '恢复上架', {
      type: 'info',
      confirmButtonText: '确认恢复',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await restoreActivity(row.id)
    ElMessage.success('活动已恢复上架')
    await load()
  } catch (err) {
    notifyError(err, load)
  }
}

function goDetail(id) {
  router.push(`/activities/${id}`)
}

onMounted(async () => {
  await loadClubs()
  await load()
})
</script>
