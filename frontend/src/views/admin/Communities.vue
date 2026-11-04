<template>
  <div class="page">
    <div class="page-header">
      <h2>社区管理</h2>
      <el-button type="primary" @click="openCreate">+ 新增社区</el-button>
    </div>

    <div class="filter-bar">
      <el-input v-model="filter.keyword" placeholder="搜索社区名/编码/地址" clearable
        style="width: 260px" :prefix-icon="Search" @keyup.enter="reload" @clear="reload" />
      <el-select v-model="filter.status" clearable placeholder="全部状态" style="width: 120px" @change="reload">
        <el-option label="启用" value="ACTIVE" />
        <el-option label="禁用" value="DISABLED" />
      </el-select>
    </div>

    <el-table :data="records" stripe style="margin-top: 12px" v-loading="loading" empty-text="暂无社区">
      <el-table-column label="ID" prop="id" width="70" />
      <el-table-column label="社区名称" prop="communityName" min-width="150" />
      <el-table-column label="编码" prop="communityCode" width="120" />
      <el-table-column label="地址" prop="address" min-width="200" show-overflow-tooltip />
      <el-table-column label="联系人" width="140">
        <template #default="{ row }">{{ row.contactName || '—' }}</template>
      </el-table-column>
      <el-table-column label="电话" prop="contactPhone" width="130" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" effect="dark" size="small">
            {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="openEdit(row)">编辑</el-button>
          <el-button size="small" link @click="toggleStatus(row)">
            {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
          </el-button>
          <el-button size="small" type="danger" link @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination :current-page.sync="filter.page" :page-size.sync="filter.size"
        :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="reload" @current-change="reload" />
    </div>

    <el-dialog v-model="editDialog.visible" :title="editDialog.mode === 'create' ? '新增社区' : '编辑社区'" width="520px">
      <el-form :model="editDialog.form" :rules="rules" ref="editFormRef" label-width="90px">
        <el-form-item label="社区名称" prop="communityName">
          <el-input v-model="editDialog.form.communityName" placeholder="如：朝阳花园社区" />
        </el-form-item>
        <el-form-item label="编码" prop="communityCode">
          <el-input v-model="editDialog.form.communityCode" placeholder="如：CYHY" />
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input v-model="editDialog.form.address" placeholder="如：北京市朝阳区xxx街道" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="editDialog.form.contactName" placeholder="选填" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="editDialog.form.contactPhone" placeholder="选填" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="editDialog.form.status">
            <el-radio label="ACTIVE">启用</el-radio>
            <el-radio label="DISABLED">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="editDialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const filter = reactive({ page: 1, size: 10, keyword: '', status: '' })

const editDialog = reactive({ visible: false, mode: 'create', saving: false, form: {} })
const editFormRef = ref()

const rules = {
  communityName: [{ required: true, message: '请输入社区名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

async function reload() {
  loading.value = true
  try {
    const params = { page: filter.page, size: filter.size }
    if (filter.keyword) params.keyword = filter.keyword
    if (filter.status) params.status = filter.status
    const res = await request.get('/communities', { params })
    records.value = res?.records || []
    total.value = res?.total || 0
  } finally { loading.value = false }
}

function openCreate() {
  editDialog.mode = 'create'
  editDialog.form = { communityName: '', communityCode: '', address: '', contactName: '', contactPhone: '', status: 'ACTIVE' }
  editDialog.visible = true
}

function openEdit(row) {
  editDialog.mode = 'edit'
  editDialog.form = { ...row }
  editDialog.visible = true
}

async function save() {
  await editFormRef.value?.validate()
  editDialog.saving = true
  try {
    if (editDialog.mode === 'create') {
      await request.post('/communities', editDialog.form)
      ElMessage.success('新增成功')
    } else {
      await request.put(`/communities/${editDialog.form.id}`, editDialog.form)
      ElMessage.success('修改成功')
    }
    editDialog.visible = false
    reload()
  } finally { editDialog.saving = false }
}

async function toggleStatus(row) {
  await request.put(`/communities/${row.id}/status`)
  ElMessage.success('状态已切换')
  reload()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除社区「${row.communityName}」？此操作逻辑删除。`, '确认', { type: 'warning' })
  await request.delete(`/communities/${row.id}`)
  ElMessage.success('已删除')
  reload()
}

onMounted(reload)
</script>

<style scoped>
.page { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.page-header h2 { margin: 0; }
.filter-bar { display: flex; gap: 10px; align-items: center; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>