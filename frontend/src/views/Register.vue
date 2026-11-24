<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-head">
        <h1>注册学生账号</h1>
        <p>注册后即可浏览校园活动并在线报名</p>
      </div>

      <el-alert
        v-if="errorText"
        :title="errorText"
        type="error"
        show-icon
        :closable="false"
        class="mb-16"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" placeholder="3-50 位账号" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="6-64 位密码" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入真实姓名" clearable />
        </el-form-item>
        <el-form-item label="学号" prop="studentNo">
          <el-input v-model="form.studentNo" placeholder="请输入学号" clearable />
        </el-form-item>
        <el-button type="primary" class="w-full" :loading="loading" @click="submit">注册</el-button>
      </el-form>

      <div class="auth-footer">
        已有账号？<router-link to="/login">返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '../api/auth'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const errorText = ref('')

const form = reactive({
  username: '',
  password: '',
  realName: '',
  studentNo: ''
})

const rules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 3, max: 50, message: '账号长度需在 3-50 之间', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度需在 6-64 之间', trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 50, message: '姓名长度不能超过 50', trigger: 'blur' }
  ],
  studentNo: [
    { required: true, message: '请输入学号', trigger: 'blur' },
    { max: 32, message: '学号长度不能超过 32', trigger: 'blur' }
  ]
}

async function submit() {
  if (loading.value) return
  errorText.value = ''
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    await register({
      username: form.username.trim(),
      password: form.password,
      realName: form.realName.trim(),
      studentNo: form.studentNo.trim()
    })
    ElMessage.success('注册成功，请使用新账号登录')
    router.replace({ path: '/login' })
  } catch (err) {
    if (err.code === 'USER_EXISTS') {
      errorText.value = '账号或学号已被占用，请更换后重试'
    } else {
      errorText.value = err.message || '注册失败，请稍后重试'
    }
    loading.value = false
  }
}
</script>
