<template>
  <div class="page-container">
    <div class="page-header">
      <h2>消息中心</h2>
      <p class="muted">报名审核结果、活动变更等通知在此查看</p>
    </div>

    <div class="toolbar">
      <div class="filter-bar" style="margin-bottom: 0">
        <el-select v-model="isRead" placeholder="全部消息" clearable>
          <el-option label="未读" :value="false" />
          <el-option label="已读" :value="true" />
        </el-select>
        <el-select v-model="type" placeholder="全部类型" clearable>
          <el-option v-for="(label, value) in MESSAGE_TYPE" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button :disabled="unread === 0" @click="markAll">全部已读</el-button>
      </div>
      <el-tag type="danger" effect="plain">未读 {{ unread }} 条</el-tag>
    </div>

    <el-alert v-if="errorText" type="error" :title="errorText" show-icon :closable="false" class="mb-16">
      <el-button size="small" class="mt-8" @click="load">重新加载</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="records" border>
      <el-table-column label="标题" min-width="200">
        <template #default="{ row }">
          <el-link type="primary" @click="openMessage(row)">{{ row.title }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="120">
        <template #default="{ row }">{{ labelOf(MESSAGE_TYPE, row.type) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.isRead ? 'info' : 'danger'" size="small">
            {{ row.isRead ? '已读' : '未读' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button v-if="!row.isRead" type="primary" link @click="markRead(row)">标记已读</el-button>
          <el-button type="primary" link @click="openMessage(row)">查看</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty v-if="!loading" description="暂无消息" />
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

    <el-dialog v-model="dialogVisible" :title="current.title || '消息详情'" width="520px">
      <div class="muted mb-16">
        {{ labelOf(MESSAGE_TYPE, current.type) }} · {{ formatDateTime(current.createdAt) }}
        <el-tag :type="current.isRead ? 'info' : 'danger'" size="small" style="margin-left: 8px">
          {{ current.isRead ? '已读' : '未读' }}
        </el-tag>
      </div>
      <div style="white-space: pre-wrap; line-height: 1.8">{{ current.content || '暂无内容' }}</div>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button v-if="!current.isRead" type="primary" @click="markRead(current, true)">
          标记已读
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchUnreadCount, listMessages, markAllMessagesRead, markMessageRead } from '../api/message'
import { extractPage, formatDateTime, labelOf, MESSAGE_TYPE } from '../utils/format'
import { notifyError } from '../utils/feedback'

const records = ref([])
const loading = ref(false)
const errorText = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const isRead = ref(null)
const type = ref(null)
const unread = ref(0)
const dialogVisible = ref(false)
const current = ref({})

async function loadUnread() {
  try {
    const data = await fetchUnreadCount()
    unread.value = Number((data && data.unreadCount) || 0)
  } catch (err) {
    unread.value = 0
  }
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await listMessages({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      isRead: isRead.value,
      type: type.value
    })
    const page = extractPage(data)
    records.value = page.records
    total.value = page.total
    await loadUnread()
  } catch (err) {
    records.value = []
    total.value = 0
    errorText.value = `${err.message || '加载消息失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
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

function openMessage(row) {
  current.value = { ...row }
  dialogVisible.value = true
  if (!row.isRead) {
    markRead(row)
  }
}

async function markRead(row, closeAfter) {
  try {
    await markMessageRead(row.id)
    row.isRead = true
    if (current.value && current.value.id === row.id) current.value.isRead = true
    ElMessage.success('已标记为已读')
    await loadUnread()
    if (closeAfter) dialogVisible.value = false
  } catch (err) {
    notifyError(err)
  }
}

async function markAll() {
  try {
    await markAllMessagesRead()
    ElMessage.success('已将全部消息标记为已读')
    load()
  } catch (err) {
    notifyError(err)
  }
}

onMounted(load)
</script>
