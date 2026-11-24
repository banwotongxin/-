<template>
  <div class="page-container">
    <div class="page-header">
      <h2>入驻审核与社团管理</h2>
      <p class="muted">审核社团入驻申请，查看全平台社团并对违规社团执行冻结</p>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="入驻申请" name="applications">
        <div class="filter-bar">
          <el-select
            v-model="appStatus"
            placeholder="全部状态"
            clearable
            style="width: 180px"
            @change="searchApplications"
          >
            <el-option
              v-for="(label, value) in CLUB_APPLICATION_STATUS"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
          <el-button type="primary" @click="searchApplications">查询</el-button>
        </div>

        <el-alert v-if="appError" type="error" :title="appError" show-icon :closable="false" class="mb-16">
          <el-button size="small" class="mt-8" @click="loadApplications">重新加载</el-button>
        </el-alert>

        <el-table v-loading="appLoading" :data="applications" border>
          <el-table-column prop="clubName" label="社团名称" min-width="150" />
          <el-table-column label="申请人" width="120">
            <template #default="{ row }">{{ row.applicantName || '—' }}</template>
          </el-table-column>
          <el-table-column label="联系方式" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.contact || '—' }}</template>
          </el-table-column>
          <el-table-column prop="content" label="申请说明" min-width="240" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="clubTagType(row.status)" size="small">
                {{ labelOf(CLUB_APPLICATION_STATUS, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="审核说明" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.status === 'PENDING' ? '—' : (row.reviewReason || '—') }}</template>
          </el-table-column>
          <el-table-column label="审核时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.reviewedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'PENDING'">
                <el-button type="success" link @click="approveApplication(row)">通过</el-button>
                <el-button type="danger" link @click="openReject(row)">驳回</el-button>
              </template>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty v-if="!appLoading" description="暂无入驻申请" />
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
      </el-tab-pane>

      <el-tab-pane label="社团列表" name="clubs">
        <div class="filter-bar">
          <el-input v-model="clubKeyword" placeholder="搜索社团名称 / 简介" clearable @keyup.enter="searchClubs" />
          <el-select v-model="clubStatus" placeholder="全部状态" clearable>
            <el-option
              v-for="(label, value) in CLUB_STATUS"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
          <el-button type="primary" @click="searchClubs">查询</el-button>
        </div>

        <el-alert v-if="clubError" type="error" :title="clubError" show-icon :closable="false" class="mb-16">
          <el-button size="small" class="mt-8" @click="loadClubs">重新加载</el-button>
        </el-alert>

        <el-table v-loading="clubLoading" :data="clubs" border>
          <el-table-column prop="name" label="社团名称" min-width="160" />
          <el-table-column prop="intro" label="简介" min-width="240" show-overflow-tooltip />
          <el-table-column label="负责人" width="120">
            <template #default="{ row }">{{ row.ownerName || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="clubTagType(row.status)" size="small">
                {{ labelOf(CLUB_STATUS, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'ACTIVE'" type="danger" link @click="openFreeze(row)">
                冻结
              </el-button>
              <el-button v-else-if="row.status === 'FROZEN'" type="success" link @click="unfreezeItem(row)">
                解冻
              </el-button>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty v-if="!clubLoading" description="暂无社团数据" />
          </template>
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="clubPageNum"
            v-model:page-size="clubPageSize"
            :total="clubTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadClubs"
            @size-change="onClubSizeChange"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="rejectVisible" title="驳回入驻申请" width="480px" :close-on-click-modal="false">
      <p class="muted mb-16">驳回原因将反馈给申请人，请填写明确说明。</p>
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="3"
        maxlength="255"
        show-word-limit
        placeholder="请填写驳回原因（必填）"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="reviewing" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="freezeVisible" title="冻结社团" width="480px" :close-on-click-modal="false">
      <p class="muted mb-16">
        冻结后社团无法发布新活动，确认冻结「{{ freezeRow.name }}」？
      </p>
      <el-input
        v-model="freezeReason"
        type="textarea"
        :rows="3"
        maxlength="255"
        show-word-limit
        placeholder="请填写冻结原因（必填）"
      />
      <template #footer>
        <el-button @click="freezeVisible = false">取消</el-button>
        <el-button type="danger" :loading="freezing" @click="submitFreeze">确认冻结</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { freezeClub, listClubApplications, listClubs, reviewClubApplication, unfreezeClub } from '../../api/club'
import {
  CLUB_APPLICATION_STATUS,
  CLUB_STATUS,
  clubTagType,
  extractPage,
  formatDateTime,
  labelOf
} from '../../utils/format'
import { notifyError } from '../../utils/feedback'

const activeTab = ref('applications')

const applications = ref([])
const appLoading = ref(false)
const appError = ref('')
const appPageNum = ref(1)
const appPageSize = ref(10)
const appTotal = ref(0)
const appStatus = ref('')

const clubs = ref([])
const clubLoading = ref(false)
const clubError = ref('')
const clubPageNum = ref(1)
const clubPageSize = ref(10)
const clubTotal = ref(0)
const clubKeyword = ref('')
const clubStatus = ref('')

const rejectVisible = ref(false)
const rejectRow = ref({})
const rejectReason = ref('')
const reviewing = ref(false)

const freezeVisible = ref(false)
const freezeRow = ref({})
const freezeReason = ref('')
const freezing = ref(false)

async function loadApplications() {
  appLoading.value = true
  appError.value = ''
  try {
    const data = await listClubApplications({
      pageNum: appPageNum.value,
      pageSize: appPageSize.value,
      status: appStatus.value || undefined
    })
    const page = extractPage(data)
    applications.value = page.records
    appTotal.value = page.total
  } catch (err) {
    applications.value = []
    appTotal.value = 0
    appError.value = `${err.message || '加载入驻申请失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
    notifyError(err)
  } finally {
    appLoading.value = false
  }
}

function searchApplications() {
  appPageNum.value = 1
  loadApplications()
}

function onAppSizeChange() {
  appPageNum.value = 1
  loadApplications()
}

async function loadClubs() {
  clubLoading.value = true
  clubError.value = ''
  try {
    const data = await listClubs({
      pageNum: clubPageNum.value,
      pageSize: clubPageSize.value,
      keyword: clubKeyword.value.trim(),
      status: clubStatus.value
    })
    const page = extractPage(data)
    clubs.value = page.records
    clubTotal.value = page.total
  } catch (err) {
    clubs.value = []
    clubTotal.value = 0
    clubError.value = `${err.message || '加载社团列表失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
    notifyError(err)
  } finally {
    clubLoading.value = false
  }
}

function searchClubs() {
  clubPageNum.value = 1
  loadClubs()
}

function onClubSizeChange() {
  clubPageNum.value = 1
  loadClubs()
}

async function approveApplication(row) {
  try {
    await ElMessageBox.confirm(`确认通过「${row.clubName}」的入驻申请？通过后将创建社团。`, '通过入驻申请', {
      type: 'success',
      confirmButtonText: '确认通过',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await reviewClubApplication(row.id, { decision: 'APPROVED' })
    ElMessage.success('入驻申请已通过')
    await loadApplications()
    await loadClubs()
  } catch (err) {
    notifyError(err, loadApplications)
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
    await reviewClubApplication(rejectRow.value.id, { decision: 'REJECTED', reason })
    ElMessage.success('入驻申请已驳回')
    rejectVisible.value = false
    await loadApplications()
  } catch (err) {
    notifyError(err, loadApplications)
  } finally {
    reviewing.value = false
  }
}

function openFreeze(row) {
  freezeRow.value = row
  freezeReason.value = ''
  freezeVisible.value = true
}

async function submitFreeze() {
  if (freezing.value) return
  const reason = freezeReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写冻结原因')
    return
  }
  freezing.value = true
  try {
    await freezeClub(freezeRow.value.id, reason)
    ElMessage.success('社团已冻结')
    freezeVisible.value = false
    await loadClubs()
  } catch (err) {
    notifyError(err, loadClubs)
  } finally {
    freezing.value = false
  }
}

async function unfreezeItem(row) {
  try {
    await ElMessageBox.confirm(`确认解冻社团「${row.name}」？解冻后可重新发布活动。`, '解冻社团', {
      type: 'warning',
      confirmButtonText: '确认解冻',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await unfreezeClub(row.id)
    ElMessage.success('社团已解冻')
    await loadClubs()
  } catch (err) {
    notifyError(err, loadClubs)
  }
}

onMounted(async () => {
  await loadApplications()
  await loadClubs()
})
</script>
