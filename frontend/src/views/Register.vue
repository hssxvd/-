<!--
  注册页：三角色 Tab 共用
  消费者 / 商家 / 自提点核销员
-->
<template>
  <div class="register-page">
    <div class="register-box">
      <div class="register-header">
        <el-icon :size="44" color="#E6A23C"><Shop /></el-icon>
        <h2>邻里团购 · 注册</h2>
        <p>加入社区团购，一起享受邻里好价</p>
      </div>

      <!-- 角色 Tab -->
      <el-tabs v-model="activeRole" class="role-tabs">
        <el-tab-pane label="👤 我是消费者" name="CONSUMER" />
        <el-tab-pane label="🏪 我是商家" name="MERCHANT" />
        <el-tab-pane label="📦 自提点核销员" name="PICKUP_STAFF" />
      </el-tabs>

      <el-form
        ref="formRef"
        :model="form"
        :rules="currentRules"
        label-width="110px"
        @keyup.enter="onSubmit"
      >
        <!-- 用户名（所有角色共用） -->
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="3-32 字符，字母/数字/下划线"
            :prefix-icon="User"
            @blur="onUsernameBlur"
          >
            <template #suffix>
              <span v-if="usernameStatus === 'checking'" class="tip muted">校验中…</span>
              <span v-else-if="usernameStatus === 'available'" class="tip ok">✓ 可用</span>
              <span v-else-if="usernameStatus === 'taken'" class="tip err">× 已占用</span>
            </template>
          </el-input>
        </el-form-item>

        <!-- 密码 -->
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" :prefix-icon="Lock" />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入密码" />
        </el-form-item>

        <!-- 手机号（选填，但校验格式） -->
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="选填，11 位手机号"
            :prefix-icon="Iphone"
            @blur="onPhoneBlur"
          >
            <template #suffix>
              <span v-if="phoneStatus === 'checking'" class="tip muted">校验中…</span>
              <span v-else-if="phoneStatus === 'available'" class="tip ok">✓ 可用</span>
              <span v-else-if="phoneStatus === 'taken'" class="tip err">× 已占用</span>
            </template>
          </el-input>
        </el-form-item>

        <!-- 昵称 -->
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="选填，默认与用户名相同" />
        </el-form-item>

        <!-- 公共：社区选择（消费者+商家必填） -->
        <el-form-item v-if="activeRole === 'CONSUMER' || activeRole === 'MERCHANT'" label="选择社区" prop="communityId">
          <el-select
            v-model="form.communityId"
            filterable
            placeholder="请选择所在社区"
            style="width: 100%"
          >
            <el-option
              v-for="c in communities"
              :key="c.id"
              :label="`${c.name} (${c.code || ''})`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>

        <!-- 商家专属：商家信息 -->
        <template v-if="activeRole === 'MERCHANT'">
          <el-form-item label="商家名称" prop="merchantName">
            <el-input v-model="form.merchantName" placeholder="如：老张蔬果" />
          </el-form-item>
          <el-form-item label="商家编码" prop="merchantCode">
            <el-input v-model="form.merchantCode" placeholder="唯一编码，如 M001" />
          </el-form-item>
          <el-form-item label="经营范围">
            <el-input v-model="form.businessScope" placeholder="选填，如：蔬菜水果" />
          </el-form-item>
        </template>

        <!-- 核销员专属：自提点 -->
        <el-form-item v-if="activeRole === 'PICKUP_STAFF'" label="自提点" prop="pickupPointId">
          <el-select
            v-model="form.pickupPointId"
            filterable
            placeholder="请选择绑定的自提点"
            style="width: 100%"
          >
            <el-option
              v-for="p in pickupPoints"
              :key="p.id"
              :label="`${p.name} (社区#${p.communityId})`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>

        <!-- 提交 -->
        <el-form-item>
          <el-button type="primary" :loading="submitting" class="register-btn" @click="onSubmit">
            立即注册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="register-footer">
        已有账号？<router-link to="/login" class="login-link">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Shop, User, Lock, Iphone } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { register, checkUsername, checkPhone, getCommunities, getRegisterPickupPoints } from '@/api'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const submitting = ref(false)
const activeRole = ref('CONSUMER')

const usernameStatus = ref('idle')   // idle | checking | available | taken
const phoneStatus = ref('idle')

const communities = ref([])
const pickupPoints = ref([])

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  phone: '',
  nickname: '',
  userType: 'CONSUMER',
  communityId: null,
  merchantName: '',
  merchantCode: '',
  businessScope: '',
  contactName: '',
  contactPhone: '',
  pickupPointId: null,
})

// ===== 校验规则（按角色） =====
const baseRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '3-32 字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '只允许字母/数字/下划线', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_r, v, cb) => v === form.password ? cb() : cb(new Error('两次密码不一致')),
      trigger: 'blur'
    }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
}

const consumerRules = {
  ...baseRules,
  communityId: [{ required: true, message: '请选择社区', trigger: 'change' }],
}
const merchantRules = {
  ...baseRules,
  communityId: [{ required: true, message: '请选择社区', trigger: 'change' }],
  merchantName: [{ required: true, message: '请填写商家名称', trigger: 'blur' }],
  merchantCode: [{ required: true, message: '请填写商家编码', trigger: 'blur' }],
}
const pickupRules = {
  ...baseRules,
  pickupPointId: [{ required: true, message: '请选择自提点', trigger: 'change' }],
}

const currentRules = computed(() => ({
  CONSUMER: consumerRules,
  MERCHANT: merchantRules,
  PICKUP_STAFF: pickupRules,
}[activeRole.value]))

// ===== 数据加载 =====
onMounted(async () => {
  try {
    communities.value = await getCommunities()
  } catch { /* 忽略，可能没初始化社区数据 */ }
  try {
    pickupPoints.value = await getRegisterPickupPoints()
  } catch {}
})

// ===== 实时校验 =====
let uNameTimer = null
let phoneTimer = null
async function onUsernameBlur() {
  if (!form.username || form.username.length < 3) { usernameStatus.value = 'idle'; return }
  clearTimeout(uNameTimer)
  usernameStatus.value = 'checking'
  uNameTimer = setTimeout(async () => {
    try {
      const res = await checkUsername(form.username)
      usernameStatus.value = res.available ? 'available' : 'taken'
    } catch { usernameStatus.value = 'idle' }
  }, 400)
}
async function onPhoneBlur() {
  if (!form.phone) { phoneStatus.value = 'idle'; return }
  if (!form.phone.match(/^1[3-9]\d{9}$/)) return
  clearTimeout(phoneTimer)
  phoneStatus.value = 'checking'
  phoneTimer = setTimeout(async () => {
    try {
      const res = await checkPhone(form.phone)
      phoneStatus.value = res.available ? 'available' : 'taken'
    } catch { phoneStatus.value = 'idle' }
  }, 400)
}

// ===== 提交 =====
async function onSubmit() {
  form.userType = activeRole.value
  if (formRef.value) {
    try { await formRef.value.validate() } catch { return }
  }

  // 用户名/手机号实时校验兜底
  if (usernameStatus.value === 'taken') { ElMessage.error('用户名已被占用'); return }
  if (phoneStatus.value === 'taken') { ElMessage.error('手机号已被注册'); return }

  const body = {
    username: form.username,
    password: form.password,
    phone: form.phone || null,
    nickname: form.nickname || null,
    userType: form.userType,
    communityId: form.communityId,
    merchantName: form.merchantName || null,
    merchantCode: form.merchantCode || null,
    businessScope: form.businessScope || null,
    contactName: form.contactName || null,
    contactPhone: form.contactPhone || null,
    pickupPointId: form.pickupPointId,
  }

  submitting.value = true
  try {
    const res = await register(body)
    ElMessage.success('注册成功，正在为您登录…')
    // 后端已签发 token，但 userStore.login() 会再走一遍后端获取完整权限列表
    await userStore.login(form.username, form.password)
    router.push(userStore.redirectByRole())
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '注册失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.register-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #fff3e0 0%, #e3f2fd 100%);
  display: flex; justify-content: center; align-items: center;
  padding: 20px;
}

.register-box {
  width: 540px; max-width: 100%;
  background: #fff; border-radius: 12px; padding: 32px 36px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.08);
}

.register-header {
  text-align: center; margin-bottom: 20px;
  h2 { margin: 8px 0 4px; font-size: 22px; color: #303133; }
  p { margin: 0; font-size: 13px; color: #909399; }
}

.role-tabs { margin-bottom: 16px; }
.register-btn { width: 100%; }

.register-footer {
  margin-top: 16px; text-align: center; font-size: 13px; color: #909399;
  .login-link { color: #E6A23C; font-weight: 600; text-decoration: none; }
}

.tip { font-size: 12px; padding-right: 4px; }
.ok { color: #67C23A; }
.err { color: #F56C6C; }
.muted { color: #c0c4cc; }
</style>
