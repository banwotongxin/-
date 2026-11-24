<template>
  <div class="page-container">
    <div class="page-header">
      <h2>社团资料维护</h2>
      <p class="muted">维护社团名称、简介与联系方式，修改后将在活动中心与平台展示</p>
    </div>

    <div class="filter-bar">
      <el-select
        v-if="platformAdmin || clubs.length > 1"
        v-model="clubId"
        placeholder="选择社团"
        filterable
        style="width: 260px"
        @change="loadDetail"
      >
        <el-option v-for="club in clubs" :key="club.id" :label="`${club.name}（${labelOf(CLUB_STATUS, club.status)}）`" :value="club.id" />
      </el-select>
      <el-button :disabled="!clubId" @click="loadDetail">刷新</el-button>
    </div>

    <el-alert
      v-if="!clubId && !loading"
      title="未找到可维护的社团，请先完成社团入驻或联系平台管理员"
      type="warning"
      show-icon
      :closable="false"
      class="mb-16"
    />
    <el-alert v-if="errorText" type="error" :title="errorText" show-icon :closable="false" class="mb-16">
      <el-button size="small" class="mt-8" @click="loadClubs">重新加载</el-button>
    </el-alert>

    <el-card v-if="clubId" v-loading="loading" shadow="never" style="max-width: 720px">
      <el-alert
        v-if="!editable"
        title="社团已冻结，请先由平台管理员解冻后再维护资料"
        type="warning"
        show-icon
        :closable="false"
        class="mb-16"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="社团状态">
          <el-tag :type="clubTagType(detail.status)" size="small">
            {{ labelOf(CLUB_STATUS, detail.status) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="负责人">
          <span>{{ detail.ownerName || '—' }}</span>
        </el-form-item>
        <el-form-item label="更新时间">
          <span>{{ formatDateTime(detail.updatedAt) }}</span>
        </el-form-item>
        <el-form-item label="社团名称" prop="name">
          <el-input v-model="form.name" :disabled="!editable" maxlength="100" show-word-limit placeholder="请输入社团名称" />
        </el-form-item>
        <el-form-item label="社团简介" prop="intro">
          <el-input
            v-model="form.intro"
            type="textarea"
            :rows="5"
            :disabled="!editable"
            maxlength="4000"
            show-word-limit
            placeholder="请输入社团简介"
          />
        </el-form-item>
        <el-form-item label="联系方式" prop="contact">
          <el-input v-model="form.contact" :disabled="!editable" maxlength="100" show-word-limit placeholder="邮箱或联系电话" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" :disabled="!editable" @click="submit">保存修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getClub, listManagedClubs, updateClub } from '../../api/club'
import {
  CLUB_STATUS,
  clubTagType,
  formatDateTime,
  labelOf
} from '../../utils/format'
import { notifyError } from '../../utils/feedback'
import { isPlatformAdmin } from '../../store/session'

const platformAdmin = computed(() => isPlatformAdmin())
const editable = computed(() => detail.value.status === 'ACTIVE')
const clubs = ref([])
const clubId = ref(null)
const detail = ref({})
const loading = ref(false)
const errorText = ref('')
const saving = ref(false)
const formRef = ref(null)

const form = reactive({
  name: '',
  intro: '',
  contact: ''
})

const rules = {
  name: [{ required: true, message: '请输入社团名称', trigger: 'blur' }]
}

async function loadClubs() {
  errorText.value = ''
  try {
    const data = await listManagedClubs()
    clubs.value = Array.isArray(data) ? data : []
    if (!clubs.value.length) {
      clubId.value = null
      return
    }
    if (!clubId.value || !clubs.value.some((club) => club.id === clubId.value)) {
      clubId.value = clubs.value[0].id
    }
    await loadDetail()
  } catch (err) {
    clubs.value = []
    clubId.value = null
    errorText.value = `${err.message || '加载社团失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
    notifyError(err)
  }
}

async function loadDetail() {
  if (!clubId.value) return
  loading.value = true
  errorText.value = ''
  try {
    const data = await getClub(clubId.value)
    detail.value = data || {}
    form.name = detail.value.name || ''
    form.intro = detail.value.intro || ''
    form.contact = detail.value.contact || ''
  } catch (err) {
    errorText.value = `${err.message || '加载社团详情失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
    notifyError(err)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (saving.value || !clubId.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  saving.value = true
  try {
    const data = await updateClub(clubId.value, {
      name: form.name.trim(),
      intro: form.intro.trim(),
      contact: form.contact.trim()
    })
    detail.value = data || detail.value
    ElMessage.success('社团资料已保存')
  } catch (err) {
    notifyError(err)
  } finally {
    saving.value = false
  }
}

onMounted(loadClubs)
</script>
