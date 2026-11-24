<template>
  <div class="page-container">
    <div class="page-header">
      <h2>操作审计</h2>
      <p class="muted">按操作类型、资源类型与资源编号查询关键操作记录</p>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="action"
        placeholder="操作类型，如 FREEZE_CLUB"
        clearable
        style="width: 220px"
        @keyup.enter="search"
      />
      <el-select v-model="resourceType" placeholder="资源类型" clearable style="width: 180px">
        <el-option v-for="item in RESOURCE_TYPES" :key="item" :label="item" :value="item" />
      </el-select>
      <el-input
        v-model="resourceId"
        placeholder="资源编号"
        clearable
        style="width: 160px"
        @keyup.enter="search"
      />
      <el-button type="primary" :loading="loading" @click="search">查询</el-button>
    </div>

    <el-alert v-if="errorText" type="error" :title="errorText" show-icon :closable="false" class="mb-16">
      <el-button size="small" class="mt-8" @click="load">重新加载</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="records" border>
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="audit-detail">
            <div>
              <div class="text-strong">操作前</div>
              <pre>{{ pretty(row.beforeJson) }}</pre>
            </div>
            <div>
              <div class="text-strong">操作后</div>
              <pre>{{ pretty(row.afterJson) }}</pre>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作者" width="130">
        <template #default="{ row }">{{ row.operatorName || row.operatorId || '系统' }}</template>
      </el-table-column>
      <el-table-column prop="action" label="操作类型" min-width="170" />
      <el-table-column label="资源" min-width="160">
        <template #default="{ row }">{{ row.resourceType }} #{{ row.resourceId }}</template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ row.result || '—' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="traceId" label="traceId" min-width="200" show-overflow-tooltip />
      <template #empty>
        <el-empty v-if="!loading" description="暂无审计记录" />
      </template>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { listAuditLogs } from '../../api/audit'
import { extractPage, formatDateTime } from '../../utils/format'
import { notifyError } from '../../utils/feedback'

const RESOURCE_TYPES = ['CLUB', 'CLUB_APPLICATION', 'ACTIVITY', 'SIGNUP', 'SIGNIN', 'ARCHIVE', 'FILE']

const action = ref('')
const resourceType = ref('')
const resourceId = ref('')
const records = ref([])
const loading = ref(false)
const errorText = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

function pretty(value) {
  if (!value) return '—'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch (e) {
    return value
  }
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await listAuditLogs({
      action: action.value.trim(),
      resourceType: resourceType.value,
      resourceId: resourceId.value.trim(),
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    const page = extractPage(data)
    records.value = page.records
    total.value = page.total
  } catch (err) {
    records.value = []
    total.value = 0
    errorText.value = `${err.message || '加载审计日志失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
    notifyError(err)
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}

function onSizeChange() {
  pageNum.value = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.audit-detail {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  padding: 8px 24px;
}

.audit-detail pre {
  margin: 8px 0 0;
  padding: 12px;
  background: #f6f8fa;
  border-radius: 6px;
  max-height: 240px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
