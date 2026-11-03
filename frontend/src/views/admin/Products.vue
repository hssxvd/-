<template>
  <div class="admin-products-page">
    <!-- 筛选 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" placeholder="商品名/编码" clearable style="width: 200px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px">
            <el-option label="上架中" value="ON_SHELF" />
            <el-option label="已下架" value="OFF_SHELF" />
            <el-option label="草稿" value="DRAFT" />
            <el-option label="售罄" value="SOLD_OUT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 商品表格 -->
    <el-card>
      <el-table :data="records" v-loading="loading" stripe height="calc(100vh - 260px)">
        <el-table-column label="主图" width="80">
          <template #default="{ row }">
            <el-image v-if="row.product.mainImage" :src="row.product.mainImage"
              :preview-src-list="[row.product.mainImage]" fit="cover"
              style="width:56px;height:56px;border-radius:4px" preview-teleported />
            <div v-else class="no-img">无图</div>
          </template>
        </el-table-column>
        <el-table-column label="商品信息" min-width="200">
          <template #default="{ row }">
            <div class="pname">{{ row.product.productName }}</div>
            <div class="muted">code: {{ row.product.productCode }}｜分类ID: {{ row.product.categoryId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="商家" width="130">
          <template #default="{ row }">{{ row.merchantName || ('商家#' + row.product.merchantId) }}</template>
        </el-table-column>
        <el-table-column label="价格" width="90" align="right">
          <template #default="{ row }">¥{{ row.product.price?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="单位" width="70" align="center">
          <template #default="{ row }">{{ row.product.unit }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.product.status)" size="small">{{ statusLabel(row.product.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="160" prop="product.updateTime" sortable />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑图片</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
          :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
          @size-change="load" @current-change="load" />
      </div>
    </el-card>

    <!-- 编辑图片弹窗 -->
    <el-dialog v-model="editVisible" title="编辑商品主图 / 描述" width="640px" @close="onEditClose">
      <template v-if="editing">
        <el-descriptions :column="2" border size="small" class="mb12">
          <el-descriptions-item label="商品">{{ editing.product.productName }}</el-descriptions-item>
          <el-descriptions-item label="商家">{{ editing.merchantName || ('商家#' + editing.product.merchantId) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(editing.product.status)" size="small">{{ statusLabel(editing.product.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="价格">¥{{ editing.product.price?.toFixed(2) }} / {{ editing.product.unit }}</el-descriptions-item>
        </el-descriptions>

        <el-form label-position="top">
          <el-form-item label="主图 URL">
            <div class="img-row">
              <el-input v-model="editForm.mainImage" placeholder="https://... 或 /images/xxx.jpg" />
              <el-image v-if="editForm.mainImage" :src="editForm.mainImage" fit="cover"
                style="width:80px;height:80px;border-radius:4px;margin-left:12px" />
              <div v-else class="no-img big">无图预览</div>
            </div>
            <div class="form-tip">支持 http(s) 网络图片，或前端 public/ 下的相对路径如 /images/xxx.jpg</div>
          </el-form-item>
          <el-form-item label="副图（逗号分隔，可选）">
            <el-input v-model="editForm.images" type="textarea" :rows="2" placeholder="https://a.jpg,https://b.jpg" />
          </el-form-item>
          <el-form-item label="商品描述">
            <el-input v-model="editForm.description" type="textarea" :rows="4" placeholder="商品详细描述（可选）" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">保存修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminProductList, adminProductUpdate } from '@/api'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive({ keyword: '', status: '' })

const editVisible = ref(false)
const saving = ref(false)
const editing = ref(null)
const editForm = reactive({ mainImage: '', images: '', description: '' })

const STATUS_MAP = {
  ON_SHELF: '上架中', OFF_SHELF: '已下架', DRAFT: '草稿', SOLD_OUT: '售罄'
}
function statusLabel(s) { return STATUS_MAP[s] || s || '—' }
function statusTag(s) {
  const map = { ON_SHELF: 'success', OFF_SHELF: 'info', DRAFT: 'warning', SOLD_OUT: 'danger' }
  return map[s] || 'info'
}

async function load() {
  loading.value = true
  try {
    const r = await adminProductList({
      pageNum: pageNum.value, pageSize: pageSize.value,
      keyword: filters.keyword || undefined,
      status: filters.status || undefined
    })
    records.value = r.records || []
    total.value = r.total || 0
  } catch (e) { ElMessage.error('加载商品列表失败') }
  finally { loading.value = false }
}

function onReset() { filters.keyword = ''; filters.status = ''; pageNum.value = 1; load() }

function openEdit(row) {
  editing.value = row
  editForm.mainImage = row.product.mainImage || ''
  editForm.images = row.product.images || ''
  editForm.description = row.product.description || ''
  editVisible.value = true
}

function onEditClose() { editing.value = null }

async function doSave() {
  if (!editForm.mainImage && !editForm.description) {
    ElMessage.warning('主图或描述至少填一个')
    return
  }
  saving.value = true
  try {
    const data = { id: editing.value.product.id }
    if (editForm.mainImage !== editing.value.product.mainImage) data.mainImage = editForm.mainImage
    if (editForm.images !== editing.value.product.images) data.images = editForm.images
    if (editForm.description !== editing.value.product.description) data.description = editForm.description

    if (Object.keys(data).length <= 1) {
      ElMessage.info('没有字段变化')
      editVisible.value = false
      return
    }

    await adminProductUpdate(editing.value.product.id, data)
    ElMessage.success('保存成功')
    editVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => load())
</script>

<style scoped>
.admin-products-page { padding: 16px; }
.filter-card { margin-bottom: 12px; }
.filter-card :deep(.el-card__body) { padding: 12px 16px 0; }
.pagination-wrap { display: flex; justify-content: flex-end; padding: 12px 4px; }
.pname { font-weight: 500; }
.muted { color: #909399; font-size: 12px; }
.no-img { width: 56px; height: 56px; background: #f5f7fa; color: #c0c4cc; font-size: 12px; border-radius: 4px; display: flex; align-items: center; justify-content: center; }
.no-img.big { width: 80px; height: 80px; }
.img-row { display: flex; align-items: center; }
.form-tip { color: #909399; font-size: 12px; margin-top: 4px; }
.mb12 { margin-bottom: 12px; }
</style>