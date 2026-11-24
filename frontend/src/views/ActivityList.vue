<template>
  <div class="page-container">
    <div class="page-header">
      <h2>活动中心</h2>
      <p class="muted">按关键词、社团、状态与时间范围检索校园活动，点击卡片查看详情并报名</p>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="filters.keyword"
        placeholder="搜索活动标题 / 简介"
        clearable
        @keyup.enter="search"
      />
      <el-select v-model="filters.clubId" placeholder="全部社团" clearable>
        <el-option v-for="club in clubs" :key="club.id" :label="club.name" :value="club.id" />
      </el-select>
      <el-input v-model="filters.tag" placeholder="标签，如 讲座" clearable @keyup.enter="search" />
      <el-select v-model="filters.status" placeholder="全部状态" clearable>
        <el-option
          v-for="[label, value] in statusOptions"
          :key="value"
          :label="label"
          :value="value"
        />
      </el-select>
      <el-date-picker
        v-model="filters.range"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-alert v-if="errorText" type="error" :title="errorText" show-icon :closable="false" class="mb-16">
      <el-button size="small" class="mt-8" @click="load">重新加载</el-button>
    </el-alert>

    <div v-loading="loading" class="card-grid">
      <el-row v-if="records.length" :gutter="16">
        <el-col v-for="item in records" :key="item.id" :xs="24" :sm="12" :lg="8" class="mb-16">
          <el-card shadow="hover" class="activity-card" @click="goDetail(item.id)">
            <div class="card-top">
              <h3 class="card-title">{{ item.title }}</h3>
              <el-tag :type="activityTagType(item.status)" size="small">
                {{ labelOf(ACTIVITY_STATUS, item.status) }}
              </el-tag>
            </div>
            <div class="club-name">{{ item.clubName || '未知社团' }}</div>
            <div v-if="item.tags && item.tags.length" style="display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 8px">
              <el-tag v-for="tag in item.tags" :key="tag" size="small" type="warning">{{ tag }}</el-tag>
            </div>
            <div class="meta-line">活动时间：{{ formatDateTime(item.startTime) }}</div>
            <div class="meta-line">活动地点：{{ item.location || '待定' }}</div>
            <div class="meta-line">
              剩余名额：
              <span :class="remainingSlots(item) === 0 ? 'text-danger' : 'text-success'">
                {{ remainingSlots(item) }}
              </span>
              / {{ item.capacity }}
            </div>
            <div class="meta-line">报名时间：{{ formatDateTime(item.signupStart) }} 至 {{ formatDateTime(item.signupEnd) }}</div>
            <div class="meta-line">
              报名状态：
              <span :class="item.signupOpen ? 'text-success' : 'text-warning'">
                {{ item.signupOpen ? '报名中' : '报名窗口未开放或已结束' }}
              </span>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-else-if="!loading && !errorText" description="暂无符合条件的活动" />
    </div>

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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listActivities } from '../api/activity'
import { listClubs } from '../api/club'
import {
  ACTIVITY_STATUS,
  activityTagType,
  extractPage,
  formatDateTime,
  labelOf,
  remainingSlots
} from '../utils/format'
import { notifyError } from '../utils/feedback'
import { isClubAdmin } from '../store/session'

const router = useRouter()
const route = useRoute()
const clubs = ref([])
const records = ref([])
const loading = ref(false)
const errorText = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  keyword: '',
  clubId: null,
  tag: '',
  status: '',
  range: null
})

const statusOptions = computed(() => {
  const entries = Object.entries(ACTIVITY_STATUS)
  return isClubAdmin() ? entries : entries.filter(([value]) => value !== 'DRAFT')
})

async function loadClubs() {
  try {
    const data = await listClubs({ pageNum: 1, pageSize: 100, status: 'ACTIVE' })
    clubs.value = extractPage(data).records
  } catch (err) {
    clubs.value = []
  }
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const range = filters.range || []
    const data = await listActivities({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: filters.keyword.trim(),
      clubId: filters.clubId,
      tag: filters.tag.trim(),
      status: filters.status,
      from: range.length ? `${range[0]}T00:00:00` : null,
      to: range.length ? `${range[1]}T23:59:59` : null
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

function reset() {
  filters.keyword = ''
  filters.clubId = null
  filters.tag = ''
  filters.status = ''
  filters.range = null
  pageNum.value = 1
  load()
}

function onSizeChange() {
  pageNum.value = 1
  load()
}

function goDetail(id) {
  router.push(`/activities/${id}`)
}

onMounted(async () => {
  const queryClubId = Number(route.query.clubId)
  if (queryClubId) {
    filters.clubId = queryClubId
  }
  await loadClubs()
  await load()
})
</script>
