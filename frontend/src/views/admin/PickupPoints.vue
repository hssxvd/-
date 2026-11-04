<template>
  <div class="page">
    <div class="page-header">
      <h2>自提点管理</h2>
      <el-button type="primary" @click="openCreate">+ 新增自提点</el-button>
    </div>

    <div class="filter-bar">
      <el-select v-model="filter.communityId" clearable placeholder="全部社区" style="width: 180px" @change="reload">
        <el-option v-for="c in communities" :key="c.id" :label="c.communityName" :value="c.id" />
      </el-select>
      <el-input v-model="filter.keyword" placeholder="搜索名称/地址" clearable
        style="width: 220px" :prefix-icon="Search" @keyup.enter="reload" @clear="reload" />
      <el-select v-model="filter.status" clearable placeholder="全部状态" style="width: 120px" @change="reload">
        <el-option label="启用" value="ACTIVE" />
        <el-option label="禁用" value="DISABLED" />
      </el-select>
    </div>

    <el-table :data="records" stripe style="margin-top: 12px" v-loading="loading" empty-text="暂无自提点">
      <el-table-column label="ID" prop="id" width="70" />
      <el-table-column label="自提点名称" prop="pointName" min-width="160" />
      <el-table-column label="所属社区" width="140">
        <template #default="{ row }">{{ communityMap[row.communityId] || '—' }}</template>
      </el-table-column>
      <el-table-column label="地址" prop="address" min-width="200" show-overflow-tooltip />
      <el-table-column label="联系人" prop="contactName" width="100" />
      <el-table-column label="电话" prop="contactPhone" width="130" />
      <el-table-column label="营业时间" prop="openTime" width="140" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" effect="dark" size="small">
            {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
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

    <el-dialog v-model="editDialog.visible" :title="editDialog.mode === 'create' ? '新增自提点' : '编辑自提点'" width="520px">
      <el-form :model="editDialog.form" :rules="rules" ref="editFormRef" label-width="90px">
        <el-form-item label="所属社区" prop="communityId">
          <el-select v-model="editDialog.form.communityId" placeholder="选择社区" style="width: 100%">
            <el-option v-for="c in communities" :key="c.id" :label="c.communityName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="自提点名称" prop="pointName">
          <el-input v-model="editDialog.form.pointName" placeholder="如：朝阳花园东门自提点" />
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input v-model="editDialog.form.address" placeholder="详细地址" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="editDialog.form.contactName" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="editDialog.form.contactPhone" />
        </el-form-item>
        <el-form-item label="营业时间">
          <el-input v-model="editDialog.form.openTime" placeholder="如：08:00-20:00" />
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
const communities = ref([])
const communityMap = ref({})
const filter = reactive({ page: 1, size: 10, communityId: null, keyword: '', status: '' })

const editDialog = reactive({ visible: false, mode: 'create', saving: false, form: {} })
const editFormRef = ref()

const rules = {
  communityId: [{ required: true, message: '请选择社区', trigger: 'change' }],
  pointName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

async function loadCommunities() {
  const list = await request.get('/communities/active')
  communities.value = list || []
  const m = {}
  communities.value.forEach(c => { m[c.id] = c.communityName })
  communityMap.value = m
}

async function reload() {
  loading.value = true
  try {
    const params = { all: true }
    if (filter.communityId) params.communityId = filter.communityId
    let list = await request.get('/pickup-points', { params })
    list = Array.isArray(list) ? list : []
    if (filter.keyword) {
      list = list.filter(p =>
        (p.pointName || '').includes(filter.keyword) ||
        (p.address || '').includes(filter.keyword)
      )
    }
    if (filter.status) list = list.filter(p => p.status === filter.status)
    total.value = list.length
    const start = (filter.page - 1) * filter.size
    records.value = list.slice(start, start + filter.size)
  } finally { loading.value = false }
}

function openCreate() {
  editDialog.mode = 'create'
  editDialog.form = { communityId: null, pointName: '', address: '', contactName: '', contactPhone: '', openTime: '', status: 'ACTIVE' }
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
      await request.post('/pickup-points', editDialog.form)
      ElMessage.success('新增成功')
    } else {
      await request.put(`/pickup-points/${editDialog.form.id}`, editDialog.form)
      ElMessage.success('修改成功')
    }
    editDialog.visible = false
    reload()
  } finally { editDialog.saving = false }
}

async function toggleStatus(row) {
  const patch = { ...row, status: row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' }
  await request.put(`/pickup-points/${row.id}`, patch)
  ElMessage.success('已切换')
  reload()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除自提点「${row.pointName}」？此操作逻辑删除。`, '确认', { type: 'warning' })
  await request.delete(`/pickup-points/${row.id}`)
  ElMessage.success('已删除')
  reload()
}

onMounted(async () => { await loadCommunities(); reload() })
</script>

<style scoped>
.page { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.page-header h2 { margin: 0; }
.filter-bar { display: flex; gap: 10px; align-items: center; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>