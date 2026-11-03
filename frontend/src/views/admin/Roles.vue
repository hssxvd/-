<template>
  <div class="admin-roles">
    <div class="page-header">
      <h2>角色权限</h2>
      <span class="tip">角色共 5 个（种子数据固定），权限调整勾选式覆盖保存</span>
    </div>

    <el-row :gutter="16">
      <!-- 左侧角色列表 -->
      <el-col :span="6">
        <div class="panel">
          <div class="panel-title">角色列表</div>
          <div class="role-list">
            <div v-for="r in roles" :key="r.id"
              class="role-item" :class="{ active: currentRoleId === r.id }"
              @click="selectRole(r.id)">
              <div class="role-name">
                {{ r.roleName }}
                <el-tag :type="r.id === 4 ? 'danger' : r.id === 3 ? 'warning' : r.id === 2 ? 'success' : 'info'"
                  size="small" effect="plain" style="margin-left: 6px">
                  {{ r.roleCode.replace('ROLE_', '') }}
                </el-tag>
              </div>
              <div class="role-desc muted">{{ r.description }}</div>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 右侧权限矩阵 -->
      <el-col :span="18">
        <div class="panel" v-if="currentRole">
          <div class="panel-title">
            权限矩阵 — {{ currentRole.roleName }}
            <span class="tip muted">（已分配 {{ assignedCount }} / {{ totalPermCount }}）</span>
            <div style="float: right">
              <el-button @click="reloadRole" :loading="loading">刷新</el-button>
              <el-button type="primary" @click="savePermissions" :loading="saving" :disabled="!isDirty">保存权限变更</el-button>
            </div>
          </div>

          <div v-if="loading" style="padding: 40px; text-align: center; color: #909399">加载中...</div>

          <div v-else-if="groupedPerms" class="perm-matrix">
            <div v-for="(perms, moduleKey) in groupedPerms" :key="moduleKey" class="perm-module">
              <div class="module-title">
                {{ moduleLabel(moduleKey) }}
                <el-tag size="small" type="info" effect="plain" style="margin-left: 6px">{{ perms.length }} 项</el-tag>
                <el-button link size="small" style="margin-left: auto; color: #409eff" @click="toggleAllInModule(perms, true)">全选</el-button>
                <el-button link size="small" style="color: #909399" @click="toggleAllInModule(perms, false)">全不选</el-button>
              </div>
              <el-checkbox-group v-model="checkedIds">
                <el-checkbox v-for="p in perms" :key="p.id" :label="p.id" :value="p.id"
                  class="perm-checkbox" :title="p.code">
                  {{ p.name }}
                  <span class="perm-code muted">{{ p.code }}</span>
                </el-checkbox>
              </el-checkbox-group>
            </div>

            <el-empty v-if="Object.keys(groupedPerms).length === 0" description="该角色暂无权限数据" />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminListRoles, adminGetRole, adminUpdateRolePermissions } from '@/api'

const roles = ref([])
const currentRoleId = ref(null)
const currentRole = computed(() => roles.value.find(r => r.id === currentRoleId.value))
const groupedPerms = ref(null)
const loading = ref(false)
const saving = ref(false)
const checkedIds = ref([])
const initialIds = ref([]) // 保存前的快照，用于 diff

const assignedCount = computed(() => checkedIds.value.length)
const totalPermCount = computed(() => {
  if (!groupedPerms.value) return 0
  return Object.values(groupedPerms.value).reduce((a, b) => a + b.length, 0)
})
const isDirty = computed(() => {
  const a = [...checkedIds.value].sort().join(',')
  const b = [...initialIds.value].sort().join(',')
  return a !== b
})

function selectRole(id) {
  if (isDirty.value) {
    ElMessageBox.confirm('有未保存的权限变更，切换角色会丢失。继续切换？', '提示', { type: 'warning' })
      .then(() => { currentRoleId.value = id })
      .catch(() => {})
  } else {
    currentRoleId.value = id
  }
}

async function reloadRole() {
  if (!currentRoleId.value) return
  loading.value = true
  try {
    const res = await adminGetRole(currentRoleId.value)
    groupedPerms.value = res.groupedPerms
    checkedIds.value = [...res.assignedIds]
    initialIds.value = [...res.assignedIds]
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '加载失败')
  } finally { loading.value = false }
}

watch(currentRoleId, () => {
  if (currentRoleId.value) reloadRole()
})

async function savePermissions() {
  if (!isDirty.value) return
  try {
    await ElMessageBox.confirm(
      `确定保存「${currentRole.value.roleName}」的权限变更吗？`,
      '确认', { type: 'warning' }
    )
    saving.value = true
    await adminUpdateRolePermissions(currentRoleId.value, checkedIds.value)
    ElMessage.success('权限已保存')
    initialIds.value = [...checkedIds.value]
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || '保存失败')
  } finally { saving.value = false }
}

function toggleAllInModule(perms, checked) {
  const ids = perms.map(p => p.id)
  if (checked) {
    checkedIds.value = [...new Set([...checkedIds.value, ...ids])]
  } else {
    checkedIds.value = checkedIds.value.filter(id => !ids.includes(id))
  }
}

// module 中文名
function moduleLabel(m) {
  return ({
    product: '商品', category: '分类', community: '社区/自提点',
    stock: '库存', supply: '供应计划', cart: '购物车', order: '订单',
    payment: '支付', fulfillment: '履约', aftersale: '售后', review: '评价',
    forecast: '预测', analysis: '经营分析', system: '系统管理'
  })[m] || m
}

onMounted(async () => {
  try {
    roles.value = await adminListRoles()
    if (roles.value.length > 0) currentRoleId.value = roles.value[0].id
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '加载角色列表失败')
  }
})
</script>

<style scoped>
.admin-roles { padding: 16px 20px 40px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.page-header h2 { margin: 0; font-size: 20px; color: #303133; }
.page-header .tip { color: #909399; font-size: 12px; }

.panel {
  background: #fff; border-radius: 8px; border: 1px solid #ebeef5;
  padding: 16px 18px; min-height: 500px;
}
.panel-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 12px; display: flex; align-items: center; gap: 8px; }

.role-list { display: flex; flex-direction: column; gap: 6px; }
.role-item { padding: 10px 12px; border-radius: 6px; cursor: pointer; border: 1px solid transparent; transition: all .15s; }
.role-item:hover { background: #f5f7fa; }
.role-item.active { background: #ecf5ff; border-color: #409eff; }
.role-name { font-size: 14px; font-weight: 600; color: #303133; display: flex; align-items: center; }
.role-desc { font-size: 12px; margin-top: 4px; }

.perm-matrix { display: flex; flex-direction: column; gap: 14px; }
.perm-module { border: 1px solid #ebeef5; border-radius: 6px; padding: 12px 14px; background: #fafbfc; }
.module-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 10px; display: flex; align-items: center; }
.perm-checkbox { margin-right: 18px; margin-bottom: 8px; }
.perm-code { color: #909399; font-size: 11px; margin-left: 4px; }
.muted { color: #909399; font-size: 12px; }
</style>
