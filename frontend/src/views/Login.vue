<!--
  登录页：三端共用，根据 userType 自动跳转
  约束文档 8：BCrypt 密码（后端处理）
  风险文档：登录表单校验、错误提示
-->
<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-header">
        <el-icon :size="48" color="#E6A23C"><Shop /></el-icon>
        <h2>邻里团购电商平台</h2>
        <p>请选择角色并登录</p>
      </div>

      <!-- 角色切换 -->
      <el-tabs v-model="activeRole" class="role-tabs">
        <el-tab-pane label="消费者" name="CONSUMER" />
        <el-tab-pane label="商家" name="MERCHANT" />
        <el-tab-pane label="取货点工作人员" name="PICKUP_STAFF" />
        <el-tab-pane label="管理员" name="ADMIN" />
      </el-tabs>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" @keyup.enter="onSubmit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="login-btn" @click="onSubmit">
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 默认账号提示（约束文档：演示用模拟/脱敏数据） -->
      <div class="demo-accounts">
        <p>默认账号（密码均为 123456）：</p>
        <p>消费者: <code>consumer01</code> | 商家: <code>merchant01</code></p>
        <p>取货点工作人员: <code>pickup01</code> / <code>pickup02</code> | 管理员: <code>superadmin</code></p>
      </div>

      <div class="login-footer">
        没有账号？<router-link to="/register" class="register-link">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Shop, User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { login } from '@/api'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const activeRole = ref('CONSUMER')

const roleAccounts = {
  CONSUMER: 'consumer01',
  MERCHANT: 'merchant01',
  PICKUP_STAFF: 'pickup01',
  ADMIN: 'superadmin'
}

const form = reactive({
  username: roleAccounts[activeRole.value],
  password: '123456'
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const onSubmit = async () => {
  try {
    await formRef.value.validate()
    loading.value = true
    await userStore.login(form.username, form.password)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    router.push(redirect || userStore.redirectByRole())
  } catch (e) {
    if (e !== false && e?.message) {
      ElMessage.error(e.message)
    }
  } finally {
    loading.value = false
  }
}

// 角色切换时自动填充对应默认账号（v1.3 修复：用 watcher 响应 tab 切换）
import { watch } from 'vue'
watch(activeRole, (role) => {
  form.username = roleAccounts[role]
  form.password = '123456'
  formRef.value?.clearValidate()
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-box {
  width: 420px;
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.login-header {
  text-align: center;
  margin-bottom: 20px;

  h2 { margin: 12px 0 4px; color: #303133; }
  p { color: #909399; font-size: 14px; }
}

.role-tabs { margin-bottom: 20px; }
.login-btn { width: 100%; }

.demo-accounts {
  margin-top: 20px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 12px;
  color: #606266;
  text-align: center;

  p { margin: 4px 0; }
  code { background: #e6f0ff; padding: 1px 6px; border-radius: 3px; color: #409EFF; }
}

.login-footer {
  margin-top: 16px; text-align: center; font-size: 13px; color: #909399;
  .register-link { color: #E6A23C; font-weight: 600; text-decoration: none; }
}
</style>
