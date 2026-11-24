<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-head">
        <h1>校园活动报名与社团信息管理系统</h1>
        <p>登录后可浏览活动、提交报名、查看审核结果与站内消息</p>
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
          <el-input
            v-model="form.username"
            placeholder="请输入账号"
            clearable
            autocomplete="username"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" class="w-full" :loading="loading" @click="submit">登录</el-button>
      </el-form>

      <div class="auth-footer">
        还没有账号？<router-link to="/register">注册学生账号</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { setSession } from '../store/session'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const errorText = ref('')

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
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
    const data = await login({ username: form.username.trim(), password: form.password })
    if (!data.accessToken || !data.user) {
      throw new Error('登录响应缺少令牌或用户信息')
    }
    setSession(data.accessToken, data.user)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/activities'
    router.replace(redirect.startsWith('/') && !redirect.startsWith('/login') ? redirect : '/activities')
  } catch (err) {
    if (err.status === 401) {
      errorText.value = '账号或密码错误，请重新输入'
    } else {
      errorText.value = err.message || '登录失败，请稍后重试'
    }
    loading.value = false
  }
}
</script>
