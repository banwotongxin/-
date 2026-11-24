<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h2>个人资料</h2>
      <p class="muted">维护本人姓名与学号，账号与角色由平台统一管理</p>
    </div>

    <el-alert v-if="errorText" type="error" :title="errorText" show-icon :closable="false" class="mb-16">
      <el-button size="small" class="mt-8" @click="load">重新加载</el-button>
    </el-alert>

    <el-card v-else shadow="never" style="max-width: 640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="账号">
          <span class="text-strong">{{ form.username || '—' }}</span>
        </el-form-item>
        <el-form-item label="角色">
          <el-tag v-for="role in form.roles" :key="role" size="small" type="info" class="mr-8">
            {{ roleLabel(role) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" maxlength="50" show-word-limit placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="学号" prop="studentNo">
          <el-input v-model="form.studentNo" maxlength="32" show-word-limit placeholder="请输入学号" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="submit">保存修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchMe, updateProfile } from '../api/auth'
import { ROLE_LABELS, updateUser } from '../store/session'
import { notifyError } from '../utils/feedback'

const loading = ref(false)
const errorText = ref('')
const saving = ref(false)
const formRef = ref(null)

const form = reactive({
  username: '',
  realName: '',
  studentNo: '',
  roles: []
})

const rules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  studentNo: [{ required: true, message: '请输入学号', trigger: 'blur' }]
}

function roleLabel(role) {
  return ROLE_LABELS[role] || role
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await fetchMe()
    form.username = data.username || ''
    form.realName = data.realName || ''
    form.studentNo = data.studentNo || ''
    form.roles = Array.isArray(data.roles) ? data.roles : []
    updateUser(data)
  } catch (err) {
    errorText.value = `${err.message || '加载个人资料失败'}${err.traceId ? `（traceId：${err.traceId}）` : ''}`
    notifyError(err)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (saving.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  saving.value = true
  try {
    const data = await updateProfile({
      realName: form.realName.trim(),
      studentNo: form.studentNo.trim()
    })
    form.realName = data.realName || form.realName
    form.studentNo = data.studentNo || form.studentNo
    form.roles = Array.isArray(data.roles) ? data.roles : form.roles
    updateUser(data)
    ElMessage.success('个人资料已保存')
  } catch (err) {
    notifyError(err)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
