<template>
  <div class="page-container">
    <div class="page-header">
      <h2>社团入驻申请</h2>
      <p class="muted">提交社团名称与简介，平台管理员审核通过后自动创建社团并授予社团管理员权限</p>
    </div>

    <el-alert
      v-if="!student"
      title="仅学生账号可提交社团入驻申请"
      type="info"
      show-icon
      :closable="false"
      class="mb-16"
    />

    <el-card v-else shadow="never" class="mb-16">
      <template #header>
        <span class="text-strong">提交申请</span>
      </template>
      <el-alert v-if="formError" :title="formError" type="error" show-icon :closable="false" class="mb-16" />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="社团名称" prop="name">
          <el-input v-model="form.name" maxlength="100" show-word-limit placeholder="请输入社团名称" />
        </el-form-item>
        <el-form-item label="社团简介" prop="intro">
          <el-input
            v-model="form.intro"
            type="textarea"
            :rows="5"
            maxlength="4000"
            show-word-limit
            placeholder="请介绍社团定位、常规活动与人员情况"
          />
        </el-form-item>
        <el-form-item label="联系方式" prop="contact">
          <el-input v-model="form.contact" maxlength="100" show-word-limit placeholder="邮箱或联系电话，仅平台管理员可见" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">提交入驻申请</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="toolbar mt-16">
      <span class="text-strong">我的申请记录</span>
      <el-button link type="primary" :loading="loading" @click="loadMine">刷新</el-button>
    </div>

    <el-alert v-if="errorText" type="error" :title="errorText" show-icon :closable="false" class="mb-16">
      <el-button size="small" class="mt-8" @click="loadMine">重新加载</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="records" border>
      <el-table-column prop="clubName" label="社团名称" min-width="160" />
      <el-table-column prop="content" label="申请说明" min-width="260" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="signupTagType(row.status)" size="small">
            {{ labelOf(CLUB_APPLICATION_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审核原因" min-width="200">
        <template #default="{ row }">{{ row.reviewReason || '—' }}</template>
      </el-table-column>
      <el-table-column label="申请时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <template #empty>
        <el-empty v-if="!loading" description="暂无申请记录" />
      </template>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadMine"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { applyClub, listMyClubApplications } from '../../api/club'
import {
  CLUB_APPLICATION_STATUS,
  extractPage,
  formatDateTime,
  labelOf,
  signupTagType
} from '../../utils/format'
import { notifyError } from '../../utils/feedback'
import { isStudent } from '../../store/session'

const student = computed(() => isStudent())
const formRef = ref(null)
const submitting = ref(false)
const formError = ref('')
const form = reactive({
  name: '',
  intro: '',
  contact: ''
})
const rules = {
  name: [{ required: true, message: '请输入社团名称', trigger: 'blur' }],
  intro: [{ required: true, message: '请输入社团简介', trigger: 'blur' }]
}

const records = ref([])
const loading = ref(false)
const errorText = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function loadMine() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await listMyClubApplications({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    const page = extractPage(data)
    records.value = page.records
    total.value = page.total
  } catch (err) {
    records.value = []
    total.value = 0
    errorText.value = `${err.message || '加载申请记录失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
    notifyError(err)
  } finally {
    loading.value = false
  }
}

function onSizeChange() {
  pageNum.value = 1
  loadMine()
}

async function submit() {
  if (submitting.value) return
  formError.value = ''
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    await applyClub({
      name: form.name.trim(),
      intro: form.intro.trim(),
      contact: form.contact.trim()
    })
    ElMessage.success('入驻申请已提交，请等待平台管理员审核')
    form.name = ''
    form.intro = ''
    form.contact = ''
    pageNum.value = 1
    await loadMine()
  } catch (err) {
    formError.value = `${err.message || '提交失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
  } finally {
    submitting.value = false
  }
}

onMounted(loadMine)
</script>
