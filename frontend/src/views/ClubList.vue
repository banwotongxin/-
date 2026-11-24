<template>
  <div class="page-container">
    <div class="page-header">
      <h2>社团信息</h2>
      <p class="muted">浏览已入驻社团，查看简介与负责人信息，并进入社团活动</p>
    </div>

    <div class="filter-bar">
      <el-input v-model="keyword" placeholder="搜索社团名称 / 简介" clearable @keyup.enter="search" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-alert v-if="errorText" type="error" :title="errorText" show-icon :closable="false" class="mb-16">
      <el-button size="small" class="mt-8" @click="load">重新加载</el-button>
    </el-alert>

    <div v-loading="loading" class="card-grid">
      <el-row v-if="records.length" :gutter="16">
        <el-col v-for="club in records" :key="club.id" :xs="24" :sm="12" :lg="8" class="mb-16">
          <el-card shadow="hover" class="activity-card" @click="openDetail(club)">
            <div class="card-top">
              <h3 class="card-title">{{ club.name }}</h3>
              <el-tag :type="clubTagType(club.status)" size="small">
                {{ labelOf(CLUB_STATUS, club.status) }}
              </el-tag>
            </div>
            <div class="meta-line">负责人：{{ club.ownerName || '—' }}</div>
            <div class="meta-line">联系方式：{{ club.contact || '—' }}</div>
            <div class="meta-line">更新时间：{{ formatDateTime(club.updatedAt) }}</div>
            <p class="club-intro">{{ club.intro || '暂无简介' }}</p>
            <el-button type="primary" link @click.stop="goActivities(club.id)">查看社团活动</el-button>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-else-if="!loading && !errorText" description="暂无社团" />
    </div>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[9, 18, 36]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="detailVisible" :title="detail.name || '社团详情'" width="560px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="社团状态">
          <el-tag :type="clubTagType(detail.status)" size="small">
            {{ labelOf(CLUB_STATUS, detail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="负责人">{{ detail.ownerName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系方式">{{ detail.contact || '—' }}</el-descriptions-item>
        <el-descriptions-item label="成立时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(detail.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="社团简介">{{ detail.intro || '暂无简介' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="goActivities(detail.id)">查看该社团活动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listClubs } from '../api/club'
import {
  CLUB_STATUS,
  clubTagType,
  extractPage,
  formatDateTime,
  labelOf
} from '../utils/format'
import { notifyError } from '../utils/feedback'

const router = useRouter()
const keyword = ref('')
const loading = ref(false)
const errorText = ref('')
const records = ref([])
const pageNum = ref(1)
const pageSize = ref(9)
const total = ref(0)
const detailVisible = ref(false)
const detail = ref({})

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await listClubs({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim(),
      status: 'ACTIVE'
    })
    const page = extractPage(data)
    records.value = page.records
    total.value = page.total
  } catch (err) {
    records.value = []
    total.value = 0
    errorText.value = `${err.message || '加载社团信息失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
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
  keyword.value = ''
  pageNum.value = 1
  load()
}

function onSizeChange() {
  pageNum.value = 1
  load()
}

function openDetail(club) {
  detail.value = club
  detailVisible.value = true
}

function goActivities(clubId) {
  if (!clubId) return
  detailVisible.value = false
  router.push({ path: '/activities', query: { clubId } })
}

onMounted(load)
</script>

<style scoped>
.club-intro {
  margin: 8px 0 4px;
  color: #606266;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
