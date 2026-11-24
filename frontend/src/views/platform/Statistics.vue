<template>
  <div class="page-container">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <p class="muted">{{ subtitle }}</p>
    </div>

    <div class="filter-bar">
      <el-date-picker
        v-model="range"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
      />
      <el-select
        v-if="platform"
        v-model="clubId"
        clearable
        placeholder="全部社团"
        class="club-select"
      >
        <el-option v-for="club in clubOptions" :key="club.id" :label="club.name" :value="club.id" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      <el-button :loading="exporting" @click="handleExport">导出明细</el-button>
    </div>

    <el-alert v-if="errorText" type="error" :title="errorText" show-icon :closable="false" class="mb-16">
      <el-button size="small" class="mt-8" @click="load">重新加载</el-button>
    </el-alert>

    <div v-if="overview" class="scope-line muted">
      <span>口径版本：{{ overview.ruleVersion || 'STAT-V1' }}</span>
      <span>统计范围：{{ scopeText }}</span>
      <span>生成时间：{{ formatDateTime(overview.generatedAt) }}</span>
    </div>

    <el-alert
      v-if="noteText"
      :title="noteText"
      type="info"
      show-icon
      :closable="false"
      class="mb-16"
    />

    <el-row :gutter="16" class="stat-cards" v-loading="loading">
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">活动数</div>
          <div class="stat-value">{{ metrics.activityCount }}</div>
          <div class="stat-hint">已发布 {{ metrics.publishedActivityCount }} 个</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">报名人次</div>
          <div class="stat-value">{{ metrics.signupCount }}</div>
          <div class="stat-hint">报名人数 {{ metrics.distinctStudentCount }} 人</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">通过人数</div>
          <div class="stat-value">{{ metrics.approvedCount }}</div>
          <div class="stat-hint">签到 {{ metrics.checkinCount }} 人（补签 {{ metrics.supplementCheckinCount }}）</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">签到率</div>
          <div class="stat-value">{{ rateText }}</div>
          <div class="stat-hint">{{ metrics.noBaseline ? '无基数' : '签到人数 / 通过人数' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="mt-16" v-loading="loading">
      <template #header>
        <div class="chart-header">
          <span class="text-strong">按日趋势</span>
          <span class="legend">
            <span class="legend-item"><i class="dot activity"></i>活动数</span>
            <span class="legend-item"><i class="dot signup"></i>报名人次</span>
            <span class="legend-item"><i class="dot approved"></i>通过人数</span>
          </span>
        </div>
      </template>
      <div v-if="hasTrendData" class="chart-wrap">
        <svg :viewBox="`0 0 ${chartWidth} ${chartHeight}`" class="trend-chart">
          <line
            v-for="line in gridLines"
            :key="`grid-${line.y}`"
            :x1="chartPadding.left"
            :x2="chartWidth - chartPadding.right"
            :y1="line.y"
            :y2="line.y"
            class="grid-line"
          />
          <text
            v-for="line in gridLines"
            :key="`label-${line.y}`"
            :x="chartPadding.left - 8"
            :y="line.y + 4"
            text-anchor="end"
            class="axis-text"
          >{{ line.label }}</text>
          <g v-for="series in seriesDefs" :key="series.key">
            <polyline :points="polyline(series.key)" :class="['series', series.cls]" />
            <circle
              v-for="(point, index) in trendPoints"
              :key="`${series.key}-${point.date}`"
              :cx="xAt(index)"
              :cy="yAt(point[series.key])"
              r="2.5"
              :class="['point', series.cls]"
            >
              <title>{{ point.date }} {{ series.label }}：{{ point[series.key] }}</title>
            </circle>
          </g>
        </svg>
        <div class="chart-axis">
          <span>{{ firstTrendDate }}</span>
          <span>{{ lastTrendDate }}</span>
        </div>
      </div>
      <el-empty v-else :image-size="80" description="时间范围内暂无趋势数据" />
    </el-card>

    <el-row :gutter="16" class="mt-16">
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="full-height">
          <template #header><span class="text-strong">活动状态分布</span></template>
          <div v-if="statusEntries.length" class="status-list">
            <div v-for="item in statusEntries" :key="item.status" class="status-item">
              <div class="status-head">
                <span>{{ item.label }}</span>
                <span>{{ item.count }}</span>
              </div>
              <div class="status-track">
                <div class="status-fill" :style="{ width: `${item.percent}%` }"></div>
              </div>
            </div>
          </div>
          <el-empty v-else :image-size="60" description="暂无活动数据" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="full-height">
          <template #header><span class="text-strong">签到构成</span></template>
          <div class="signin-breakdown">
            <div class="breakdown-item">
              <div class="stat-value">{{ metrics.checkinCount }}</div>
              <div class="muted">签到人数</div>
            </div>
            <div class="breakdown-item">
              <div class="stat-value">{{ metrics.normalCheckinCount }}</div>
              <div class="muted">正常签到</div>
            </div>
            <div class="breakdown-item">
              <div class="stat-value">{{ metrics.supplementCheckinCount }}</div>
              <div class="muted">补签</div>
            </div>
          </div>
          <p class="muted breakdown-note">签到人数 = 正常签到 + 补签；仅统计时间范围内通过报名的签到记录。</p>
        </el-card>
      </el-col>
    </el-row>

    <div class="toolbar mt-16">
      <span class="text-strong">社团活跃度排行</span>
      <span class="muted">活跃度 = 活动数 × 2 + 报名人次</span>
    </div>

    <el-table v-loading="loading" :data="clubRanking" border>
      <el-table-column label="排名" width="70">
        <template #default="{ $index }">{{ $index + 1 }}</template>
      </el-table-column>
      <el-table-column prop="clubName" label="社团名称" min-width="180" />
      <el-table-column prop="activityCount" label="活动数" width="90" />
      <el-table-column prop="signupCount" label="报名人次" width="100" />
      <el-table-column prop="approvedCount" label="通过人数" width="100" />
      <el-table-column prop="checkinCount" label="签到人数" width="100" />
      <el-table-column prop="checkinRate" label="签到率" width="100">
        <template #default="{ row }">{{ formatRate(row.checkinRate) }}</template>
      </el-table-column>
      <el-table-column prop="activityScore" label="活跃度" width="100" />
      <template #empty>
        <el-empty v-if="!loading" description="时间范围内暂无社团统计数据" />
      </template>
    </el-table>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getStatisticsOverview, getStatisticsTrend, exportStatistics } from '../../api/statistics'
import { listManagedClubs } from '../../api/club'
import { isPlatformAdmin } from '../../store/session'
import { ACTIVITY_STATUS, dateText, downloadBlob, formatDateTime } from '../../utils/format'
import { notifyError } from '../../utils/feedback'

const platform = isPlatformAdmin()
const pageTitle = platform ? '平台统计' : '社团统计'
const subtitle = platform
  ? '按时间范围与社团查看活动数、报名人次、签到率、趋势与活跃度排行'
  : '查看本人负责社团的活动数、报名人次、签到率、趋势与活跃度'

const now = new Date()
const range = ref([
  dateText(new Date(now.getFullYear(), now.getMonth(), 1)),
  dateText(now)
])
const clubId = ref(null)
const clubOptions = ref([])
const overview = ref(null)
const trend = ref(null)
const loading = ref(false)
const exporting = ref(false)
const errorText = ref('')

const metrics = computed(() => {
  const data = overview.value || {}
  return {
    activityCount: Number(data.activityCount || 0),
    publishedActivityCount: Number(data.publishedActivityCount || 0),
    signupCount: Number(data.signupCount || 0),
    distinctStudentCount: Number(data.distinctStudentCount || 0),
    approvedCount: Number(data.approvedCount || 0),
    checkinCount: Number(data.checkinCount || 0),
    normalCheckinCount: Number(data.normalCheckinCount || 0),
    supplementCheckinCount: Number(data.supplementCheckinCount || 0),
    checkinRate: Number(data.checkinRate || 0),
    noBaseline: Boolean(data.noBaseline)
  }
})

const rateText = computed(() => {
  if (metrics.value.noBaseline) return '0%'
  return `${metrics.value.checkinRate}%`
})

const formatRate = (value) => `${Number(value || 0)}%`

const clubRanking = computed(() => (overview.value && overview.value.clubRanking) || [])
const scopeText = computed(() => {
  if (!overview.value) return '—'
  const names = overview.value.clubNames || []
  if (overview.value.scopeType === 'ALL') return `全部社团（${names.length} 个）`
  return names.length ? names.join('、') : '无'
})
const noteText = computed(() => (overview.value && overview.value.note) || '')

const statusEntries = computed(() => {
  const counts = (overview.value && overview.value.activityStatusCounts) || {}
  const total = Number(overview.value && overview.value.activityCount) || 0
  return Object.keys(counts)
    .map((status) => {
      const count = Number(counts[status] || 0)
      return {
        status,
        label: ACTIVITY_STATUS[status] || status,
        count,
        percent: total > 0 ? Math.round((count / total) * 100) : 0
      }
    })
    .filter((item) => item.count > 0)
})

const chartWidth = 640
const chartHeight = 190
const chartPadding = { left: 40, right: 16, top: 16, bottom: 16 }

const trendPoints = computed(() => (trend.value && trend.value.points) || [])
const hasTrendData = computed(() =>
  trendPoints.value.some((point) =>
    Number(point.activityCount || 0) > 0 ||
    Number(point.signupCount || 0) > 0 ||
    Number(point.approvedCount || 0) > 0
  )
)
const trendMax = computed(() => {
  const values = []
  trendPoints.value.forEach((point) => {
    values.push(Number(point.activityCount || 0))
    values.push(Number(point.signupCount || 0))
    values.push(Number(point.approvedCount || 0))
  })
  const max = Math.max(...values, 0)
  return max <= 0 ? 1 : max
})
const gridLines = computed(() => {
  const top = chartPadding.top
  const height = chartHeight - chartPadding.top - chartPadding.bottom
  return [0, 0.5, 1].map((ratio) => ({
    y: top + height * ratio,
    label: Math.round(trendMax.value * (1 - ratio))
  }))
})
const firstTrendDate = computed(() => (trendPoints.value[0] && trendPoints.value[0].date) || '')
const lastTrendDate = computed(() => {
  const last = trendPoints.value[trendPoints.value.length - 1]
  return (last && last.date) || ''
})

const seriesDefs = [
  { key: 'activityCount', label: '活动数', cls: 'activity' },
  { key: 'signupCount', label: '报名人次', cls: 'signup' },
  { key: 'approvedCount', label: '通过人数', cls: 'approved' }
]

function xAt(index) {
  const count = trendPoints.value.length
  if (count <= 1) return chartPadding.left + (chartWidth - chartPadding.left - chartPadding.right) / 2
  return chartPadding.left +
    ((chartWidth - chartPadding.left - chartPadding.right) * index) / (count - 1)
}

function yAt(value) {
  const height = chartHeight - chartPadding.top - chartPadding.bottom
  return chartPadding.top + height - (Number(value || 0) / trendMax.value) * height
}

function polyline(key) {
  return trendPoints.value
    .map((point, index) => `${xAt(index).toFixed(1)},${yAt(point[key]).toFixed(1)}`)
    .join(' ')
}

function buildParams() {
  const dates = range.value || []
  if (dates.length !== 2) return null
  return {
    from: `${dates[0]}T00:00:00`,
    to: `${dates[1]}T23:59:59`,
    clubId: clubId.value || null
  }
}

async function loadClubOptions() {
  if (!platform) return
  try {
    clubOptions.value = (await listManagedClubs()) || []
  } catch (err) {
    clubOptions.value = []
  }
}

async function load() {
  const params = buildParams()
  if (!params) {
    ElMessage.warning('请选择统计时间范围')
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const [overviewData, trendData] = await Promise.all([
      getStatisticsOverview(params),
      getStatisticsTrend(params)
    ])
    overview.value = overviewData || {}
    trend.value = trendData || {}
  } catch (err) {
    overview.value = null
    trend.value = null
    errorText.value = `${err.message || '加载统计数据失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
    notifyError(err)
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  const params = buildParams()
  if (!params) {
    ElMessage.warning('请选择统计时间范围')
    return
  }
  exporting.value = true
  try {
    const response = await exportStatistics(params)
    const blob = response.data instanceof Blob
      ? response.data
      : new Blob([response.data], { type: 'text/csv;charset=utf-8' })
    downloadBlob(blob, `statistics-${params.from.slice(0, 10)}-${params.to.slice(0, 10)}.csv`)
    ElMessage.success('统计明细已导出')
  } catch (err) {
    notifyError(err)
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  await loadClubOptions()
  load()
})
</script>

<style scoped>
.club-select {
  width: 220px;
}

.scope-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin-bottom: 12px;
  font-size: 13px;
}

.chart-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.legend {
  display: inline-flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: #64748b;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot.activity,
.point.activity {
  background: #2563eb;
}

.dot.signup,
.point.signup {
  background: #0ea5e9;
}

.dot.approved,
.point.approved {
  background: #f59e0b;
}

.chart-wrap {
  width: 100%;
}

.trend-chart {
  display: block;
  width: 100%;
  height: 220px;
}

.grid-line {
  stroke: #e2e8f0;
  stroke-dasharray: 4 4;
  stroke-width: 1;
}

.axis-text {
  font-size: 11px;
  fill: #94a3b8;
}

.series {
  fill: none;
  stroke-width: 2;
}

.series.activity {
  stroke: #2563eb;
}

.series.signup {
  stroke: #0ea5e9;
}

.series.approved {
  stroke: #f59e0b;
}

.point {
  stroke: #ffffff;
  stroke-width: 1;
}

.chart-axis {
  display: flex;
  justify-content: space-between;
  padding: 0 8px;
  font-size: 12px;
  color: #94a3b8;
}

.status-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 13px;
  color: #475569;
}

.status-track {
  height: 8px;
  overflow: hidden;
  background: #eef2f7;
  border-radius: 999px;
}

.status-fill {
  height: 100%;
  background: linear-gradient(90deg, #2563eb, #38bdf8);
  border-radius: 999px;
}

.signin-breakdown {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 12px 0;
  text-align: center;
}

.breakdown-item .stat-value {
  font-size: 26px;
}

.breakdown-note {
  margin: 8px 0 0;
  font-size: 12px;
}

.full-height {
  height: 100%;
}
</style>
