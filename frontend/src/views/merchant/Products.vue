<!--
  商家商品管理 Products.vue
  约束 23.2：PC 后台风格，Tab 状态筛选 + 表格列表 + 快速操作
  数据源（后端全已实现，V1.11 加了 merchant_id 自动隔离）：
    GET /products?keyword=&status=&categoryId=&pageNum=&pageSize=
    POST /products/{id}/shelf
    POST /products/{id}/off-shelf
    DELETE /products/{id}
-->
<template>
  <div class="merchant-products">
    <!-- ① 顶部：状态 Tab + 新增按钮（上一行）+ 搜索（下一行） -->
    <el-card class="toolbar" shadow="never">
      <div class="toolbar-row">
        <el-radio-group v-model="statusTab" @change="onTabChange" size="default">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="ON_SHELF">上架中</el-radio-button>
          <el-radio-button label="OFF_SHELF">已下架</el-radio-button>
          <el-radio-button label="DRAFT">草稿</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="showEditForm(null)">
          <el-icon><Plus /></el-icon> 新增商品
        </el-button>
      </div>
      <div class="toolbar-row toolbar-row--sub">
        <el-input
          v-model="keyword"
          placeholder="搜索商品名 / 编码"
          clearable
          @clear="loadData"
          @keyup.enter="loadData"
          style="width: 260px"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="loadData">搜索</el-button>
        <el-button @click="onReset" v-if="keyword || statusTab">重置</el-button>
      </div>
    </el-card>

    <!-- ② 商品表格 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="list" v-loading="loading" stripe size="default" empty-text="暂无商品">
        <el-table-column label="商品" min-width="220">
          <template #default="{ row }">
            <div class="product-cell">
              <div class="product-img">
                <!-- 约束 11.2：真实商品图片；无图时用占位 -->
                <el-image
                  v-if="row.mainImage"
                  :src="row.mainImage"
                  fit="cover"
                  style="width:100%;height:100%;border-radius:6px"
                  :preview-src-list="[row.mainImage]"
                  preview-teleported
                />
                <el-icon v-else :size="24" color="#bbb"><Picture /></el-icon>
              </div>
              <div class="product-info">
                <div class="product-name">{{ row.productName }}</div>
                <div class="product-code">{{ row.productCode }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="categoryName" label="分类" width="120">
          <template #default="{ row }">{{ row.categoryName || '-' }}</template>
        </el-table-column>

        <el-table-column label="价格" width="110" align="right">
          <template #default="{ row }">
            <span class="price">¥{{ row.price?.toFixed(2) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="unit" label="单位" width="70" align="center" />

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button-group size="small">
              <el-button
                v-if="row.status === 'ON_SHELF'"
                link type="warning"
                @click="toggleShelf(row, false)"
              >下架</el-button>
              <el-button
                v-else-if="row.status !== 'DRAFT'"
                link type="success"
                @click="toggleShelf(row, true)"
              >上架</el-button>
              <el-button link type="primary" @click="showEditForm(row)">编辑</el-button>
              <el-popconfirm title="确定删除该商品？删除后不可恢复" @confirm="doDelete(row)">
                <template #reference>
                  <el-button link type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- ③ 新增/编辑商品 Dialog（简化版，只支持核心字段）-->
    <el-dialog
      v-model="editDialog"
      :title="editing ? '编辑商品' : '新增商品'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="90px">
        <el-form-item label="商品名称" prop="productName">
          <el-input v-model="form.productName" placeholder="如 有机崇明青菜 500g" maxlength="100" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-tree-select
            v-model="form.categoryId"
            :data="categoryTree"
            :props="{ label: 'categoryName', value: 'id', children: 'children' }"
            placeholder="请选择分类"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="价格" prop="price">
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="0.1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="form.unit" placeholder="如 份 / 箱 / 瓶" maxlength="10" />
        </el-form-item>

        <!-- 主图 URL（约束 11.2：商品必须有主图展示） -->
        <el-form-item label="主图 URL">
          <div style="width:100%;display:flex;gap:10px;align-items:flex-start">
            <el-input
              v-model="form.mainImage"
              placeholder="网络图片链接，如 https://xxx.com/a.jpg"
              clearable
              style="flex:1"
            />
            <div class="img-preview">
              <el-image
                v-if="form.mainImage"
                :src="form.mainImage"
                fit="cover"
                style="width:80px;height:80px;border-radius:8px;border:1px solid #eee;display:block"
              />
              <div v-else class="img-placeholder">预览</div>
            </div>
          </div>
          <div class="form-tip">支持 http(s) 网络图片，或前端 public/ 下的相对路径如 /images/xxx.jpg</div>
        </el-form-item>

        <el-form-item label="状态" v-if="editing">
          <el-tag :type="statusTagType(form.status)">{{ statusLabel(form.status) }}</el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus, Picture } from '@element-plus/icons-vue'
import { getProducts, getCategories } from '@/api/index'
import request from '@/utils/request'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const statusTab = ref('')
const keyword = ref('')
const categoryTree = ref([])

// 编辑弹窗
const editDialog = ref(false)
const editing = ref(null)     // 当前编辑的 Product 对象，null 表示新增
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({
  productName: '',
  categoryId: null,
  price: 0,
  unit: '',
  status: 'DRAFT',
  mainImage: ''      // V1.12 新增：商品主图 URL（网络链接或 public/ 相对路径）
})
const formRules = {
  productName: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId:  [{ required: true, message: '请选择分类', trigger: 'change' }],
  price:       [{ required: true, message: '请输入价格', trigger: 'blur' }],
  unit:        [{ required: true, message: '请输入单位', trigger: 'blur' }]
}

function onTabChange() { pageNum.value = 1; loadData() }
function onReset() { keyword.value = ''; statusTab.value = ''; pageNum.value = 1; loadData() }

function formatTime(t) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 16)
}

function statusLabel(s) {
  return { DRAFT: '草稿', ON_SHELF: '上架中', OFF_SHELF: '已下架', DELETED: '已删除' }[s] || s
}
function statusTagType(s) {
  return { DRAFT: 'info', ON_SHELF: 'success', OFF_SHELF: 'warning', DELETED: 'danger' }[s] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (keyword.value) params.keyword = keyword.value
    if (statusTab.value) params.status = statusTab.value
    // request.js 拦截器 return res.data —— 已解包 R<Page>.data
    // 所以 res 就是 Page<Product>{ records, total }
    const res = await getProducts(params)
    list.value = res?.records || []
    total.value = res?.total || 0
  } catch (e) {
    ElMessage.warning('加载失败：' + (e.message || '网络异常'))
  } finally {
    loading.value = false
  }
}

async function loadCategoryTree() {
  try {
    // 拦截器已解包 —— 直接就是 List<Category>
    const tree = await getCategories()
    categoryTree.value = tree || []
  } catch (e) { /* 分类加载失败不阻塞主列表 */ }
}

async function toggleShelf(row, shelf) {
  try {
    const url = shelf ? `/products/${row.id}/shelf` : `/products/${row.id}/off-shelf`
    await request.post(url)
    ElMessage.success(shelf ? '已上架' : '已下架')
    loadData()
  } catch (e) {
    ElMessage.error('操作失败：' + (e.message || '网络异常'))
  }
}

async function doDelete(row) {
  try {
    await request.delete(`/products/${row.id}`)
    ElMessage.success('已删除')
    loadData()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || '网络异常'))
  }
}

function showEditForm(row) {
  editing.value = row
  if (row) {
    // 编辑：回填所有字段（含 mainImage）
    form.productName = row.productName
    form.categoryId = row.categoryId
    form.price = row.price
    form.unit = row.unit
    form.status = row.status
    form.mainImage = row.mainImage || ''
  } else {
    // 新增
    form.productName = ''
    form.categoryId = null
    form.price = 0
    form.unit = ''
    form.status = 'DRAFT'
    form.mainImage = ''
  }
  editDialog.value = true
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (editing.value) {
        // PUT /products/{id}
        await request.put(`/products/${editing.value.id}`, form)
        ElMessage.success('已更新')
      } else {
        // POST /products
        await request.post('/products', form)
        ElMessage.success('已创建')
      }
      editDialog.value = false
      loadData()
    } catch (e) {
      ElMessage.error('保存失败：' + (e.message || '网络异常'))
    } finally {
      submitting.value = false
    }
  })
}

onMounted(() => { loadData(); loadCategoryTree() })
</script>

<style scoped>
.merchant-products { padding: 20px; }

.toolbar {
  display: flex; flex-direction: column; gap: 12px;
  margin-bottom: 16px; border-radius: 10px; border: 1px solid #f0f0f0;
}
.toolbar :deep(.el-card__body) { padding: 16px 20px; }
.toolbar-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.toolbar-row--sub { justify-content: flex-start; }

.table-card { border-radius: 10px; border: 1px solid #f0f0f0; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }

/* 商品单元格 */
.product-cell { display: flex; align-items: center; gap: 12px; }
.product-img {
  width: 48px; height: 48px; border-radius: 8px; background: #f5f5f5;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.product-info { min-width: 0; }
.product-name { font-size: 14px; font-weight: 500; color: #262626; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.product-code { font-size: 12px; color: #8c8c8c; margin-top: 2px; font-family: Consolas, monospace; }
.price { color: #cf1322; font-weight: 600; font-family: 'Helvetica Neue', sans-serif; }

/* 图片预览相关 */
.img-preview { flex-shrink: 0; }
.img-placeholder {
  width: 80px; height: 80px;
  border-radius: 8px; border: 1px dashed #d9d9d9;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; color: #bbb; background: #fafafa;
}
.form-tip { font-size: 12px; color: #909399; margin-top: 4px; line-height: 1.4; }
</style>


