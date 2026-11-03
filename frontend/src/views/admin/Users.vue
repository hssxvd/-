<template>
  <div class="admin-users">
    <div class="page-header">
      <h2>用户管理</h2>
      <el-button type="primary" @click="openCreate">+ 新增用户</el-button>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-radio-group v-model="filter.userType" size="default" @change="reload">
        <el-radio-button label="">全部 ({{ stats.all }})</el-radio-button>
        <el-radio-button label="CONSUMER">消费者 ({{ stats.consumer }})</el-radio-button>
        <el-radio-button label="MERCHANT">商家 ({{ stats.merchant }})</el-radio-button>
        <el-radio-button label="ADMIN">管理员 ({{ stats.admin }})</el-radio-button>
        <el-radio-button label="PICKUP_STAFF">取货点工作人员 ({{ stats.pickup }})</el-radio-button>
      </el-radio-group>
      <el-select v-model="filter.status" clearable placeholder="全部状态" style="width: 140px" @change="reload">
        <el-option label="启用" value="ACTIVE" />
        <el-option label="禁用" value="DISABLED" />
      </el-select>
      <el-input v-model="filter.keyword" placeholder="搜索用户名/手机号" clearable
        style="width: 220px" :prefix-icon="Search" @keyup.enter="reload" @clear="reload" />
    </div>

    <!-- 表格 -->
    <el-table :data="records" stripe style="margin-top: 12px" v-loading="loading" empty-text="暂无用户">
      <el-table-column label="用户名" prop="username" width="160">
        <template #default="{ row }">
          <el-avatar :size="32" :src="row.avatar" style="vertical-align: middle; margin-right: 8px" />
          {{ row.username }}
        </template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag :type="typeTagColor(row.userType)" effect="plain" size="small">{{ typeLabel(row.userType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="昵称" prop="nickname" width="120" />
      <el-table-column label="手机号" prop="phone" width="140" />
      <el-table-column label="绑定" width="150">
        <template #default="{ row }">
          <span v-if="row.merchantId">商家#{{ row.merchantId }}</span>
          <span v-else-if="row.communityId">社区#{{ row.communityId }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="c in row.roleCodes" :key="c" size="small" effect="plain"
            :type="roleTagColor(c)" style="margin-right: 4px">{{ roleLabel(c) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" effect="dark" size="small">
            {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="openEdit(row)">编辑</el-button>
          <el-button size="small" link @click="toggleStatus(row)">
            {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
          </el-button>
          <el-button size="small" link @click="openRoles(row)">角色</el-button>
          <el-button size="small" type="warning" link @click="openReset(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination :current-page.sync="filter.pageNum" :page-size.sync="filter.pageSize"
        :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="reload" @current-change="reload" />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="editDialog.visible" :title="editDialog.mode === 'create' ? '新增用户' : '编辑用户'" width="480px">
      <el-form :model="editDialog.form" :rules="editRules" ref="editFormRef" label-width="90px">
        <el-form-item label="用户名" prop="username" v-if="editDialog.mode === 'create'">
          <el-input v-model="editDialog.form.username" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="editDialog.mode === 'create'">
          <el-input v-model="editDialog.form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="用户类型" prop="userType" v-if="editDialog.mode === 'create'">
          <el-select v-model="editDialog.form.userType" style="width: 100%">
            <el-option v-for="t in userTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="editDialog.form.nickname" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="editDialog.form.phone" />
        </el-form-item>
        <el-form-item label="绑定商家">
          <el-input-number v-model="editDialog.form.merchantId" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="绑定社区">
          <el-input-number v-model="editDialog.form.communityId" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit" :loading="editDialog.submitting">保存</el-button>
      </template>
    </el-dialog>

    <!-- 角色调整弹窗 -->
    <el-dialog v-model="rolesDialog.visible" :title="`调整角色 — ${rolesDialog.username}`" width="480px">
      <el-form>
        <el-form-item label="角色分配">
          <el-checkbox-group v-model="rolesDialog.roleIds">
            <el-checkbox v-for="r in roles" :key="r.id" :label="r.id" :value="r.id">
              {{ r.roleName }} <span class="muted">({{ r.roleCode }})</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rolesDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitRoles" :loading="rolesDialog.submitting">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码结果弹窗 -->
    <el-dialog v-model="resetDialog.visible" title="密码已重置" width="400px">
      <el-alert type="warning" :closable="false" style="margin-bottom: 12px">
        该密码仅本次显示，请立即通知用户修改。
      </el-alert>
      <div style="text-align: center; font-size: 28px; font-weight: 600; letter-spacing: 8px">
        {{ resetDialog.newPassword }}
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminListUsers, adminCreateUser, adminUpdateUser, adminUpdateUserStatus,
  adminUpdateUserRoles, adminResetUserPassword, adminRoleOptions
} from '@/api'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const roles = ref([])

const filter = reactive({ userType: '', status: '', keyword: '', pageNum: 1, pageSize: 10 })

// 统计卡（客户端聚合 — 数据量小，直接算）
const stats = computed(() => ({
  all: total.value,
  consumer: records.value.filter(r => r.userType === 'CONSUMER').length, // 仅当前页
  merchant: records.value.filter(r => r.userType === 'MERCHANT').length,
  admin: records.value.filter(r => r.userType === 'ADMIN').length,
  pickup: records.value.filter(r => r.userType === 'PICKUP_STAFF').length
}))

const userTypes = [
  { value: 'CONSUMER', label: '消费者' },
  { value: 'MERCHANT', label: '商家' },
  { value: 'ADMIN', label: '管理员' },
  { value: 'PICKUP_STAFF', label: '取货点工作人员' }
]

// ====== 新增/编辑弹窗 ======
const editFormRef = ref(null)
const editDialog = reactive({ visible: false, mode: 'create', submitting: false, form: {} })
const editRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码（至少 6 位）', trigger: 'blur', min: 6 }],
  userType: [{ required: true, message: '请选择用户类型', trigger: 'change' }]
}

function openCreate() {
  editDialog.mode = 'create'
  editDialog.form = { username: '', password: '', userType: 'CONSUMER', nickname: '', phone: '', merchantId: null, communityId: null }
  editDialog.visible = true
}
function openEdit(row) {
  editDialog.mode = 'edit'
  editDialog.form = { id: row.id, nickname: row.nickname, phone: row.phone, merchantId: row.merchantId, communityId: row.communityId }
  editDialog.visible = true
}
async function submitEdit() {
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  editDialog.submitting = true
  try {
    if (editDialog.mode === 'create') {
      await adminCreateUser(editDialog.form)
      ElMessage.success('创建成功')
    } else {
      await adminUpdateUser(editDialog.form.id, editDialog.form)
      ElMessage.success('保存成功')
    }
    editDialog.visible = false
    reload()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '操作失败') }
  finally { editDialog.submitting = false }
}

// ====== 启用/禁用 ======
async function toggleStatus(row) {
  const newStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await ElMessageBox.confirm(`确定${newStatus === 'DISABLED' ? '禁用' : '启用'}用户 ${row.username} 吗？`, '确认')
    await adminUpdateUserStatus(row.id, newStatus)
    ElMessage.success('状态已更新')
    reload()
  } catch { /* 取消 */ }
}

// ====== 角色调整 ======
const rolesDialog = reactive({ visible: false, userId: null, username: '', roleIds: [], submitting: false })
function openRoles(row) {
  rolesDialog.userId = row.id
  rolesDialog.username = row.username
  rolesDialog.roleIds = row.roleCodes
    .map(c => roles.value.find(r => r.roleCode === c)?.id)
    .filter(Boolean)
  rolesDialog.visible = true
}
async function submitRoles() {
  rolesDialog.submitting = true
  try {
    await adminUpdateUserRoles(rolesDialog.userId, rolesDialog.roleIds)
    ElMessage.success('角色已更新')
    rolesDialog.visible = false
    reload()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '保存失败') }
  finally { rolesDialog.submitting = false }
}

// ====== 重置密码 ======
const resetDialog = reactive({ visible: false, newPassword: '' })
async function openReset(row) {
  try {
    await ElMessageBox.confirm(`确定重置 ${row.username} 的密码吗？`, '危险操作', { type: 'warning' })
    const res = await adminResetUserPassword(row.id)
    resetDialog.newPassword = res.newPassword
    resetDialog.visible = true
  } catch { /* 取消 */ }
}

// ====== 工具 ======
function typeLabel(t) { return ({ CONSUMER: '消费者', MERCHANT: '商家', ADMIN: '管理员', PICKUP_STAFF: '取货点工作人员' })[t] || t }
function typeTagColor(t) { return ({ CONSUMER: '', MERCHANT: 'success', ADMIN: 'danger', PICKUP_STAFF: 'warning' })[t] || 'info' }
function roleLabel(c) { return roles.value.find(r => r.roleCode === c)?.roleName || c }
function roleTagColor(c) { return c === 'ROLE_SUPER_ADMIN' ? 'danger' : c === 'ROLE_ADMIN' || c === 'ROLE_COMMUNITY_ADMIN' ? 'warning' : c === 'ROLE_MERCHANT' ? 'success' : 'info' }

async function reload() {
  loading.value = true
  try {
    const res = await adminListUsers({ ...filter })
    records.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '加载失败')
  } finally { loading.value = false }
}

onMounted(async () => {
  try {
    roles.value = await adminRoleOptions()
  } catch { /* ignore */ }
  reload()
})
</script>

<style scoped>
.admin-users { padding: 16px 20px 40px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.page-header h2 { margin: 0; font-size: 20px; color: #303133; }
.filter-bar { background: #fff; padding: 12px 16px; border-radius: 8px; border: 1px solid #ebeef5; display: flex; gap: 12px; align-items: center; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 12px; }
.muted { color: #909399; font-size: 12px; }
</style>
