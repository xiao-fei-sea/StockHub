<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { OrderDetailDrawer, PageHeader, SearchForm, StatusTag, confirmDialog } from '@/components'
import type { DetailField, SearchField, TableColumn } from '@/components'
import { AppTable } from '@/components'
import {
  createProduct,
  deleteProduct,
  getProductDetail,
  getProducts,
  updateProduct,
} from '@/api/product'
import { createCategory, deleteCategory, getCategoryTree, updateCategory } from '@/api/category'
import type { CategoryForm, CategoryNode, Product, ProductForm } from '@/types/entity'
import { ENABLE_STATUS, enableStatusInfo } from '@/utils/dict'
import { enableTagStatus } from '../components/status'
import { usePermission } from '@/composables/usePermission'

defineOptions({ name: 'BaseProduct' })

const { hasPerm } = usePermission()

/* ---------------- 左侧：商品分类树 ---------------- */
interface CatTreeNode {
  key: string
  id: number
  label: string
  code: string
  level: number
  isAll?: boolean
  raw?: CategoryNode
  children?: CatTreeNode[]
}

const categories = ref<CategoryNode[]>([])
const treeLoading = ref(false)

const ALL_KEY = -1

function wrapCat(n: CategoryNode): CatTreeNode {
  return {
    key: `cat-${n.id}`,
    id: n.id,
    label: n.categoryName,
    code: n.categoryCode,
    level: n.level,
    raw: n,
    children: n.children?.length ? n.children.map(wrapCat) : undefined,
  }
}

const displayTree = computed<CatTreeNode[]>(() => [
  { key: 'cat-all', id: ALL_KEY, label: '全部商品', code: 'ALL', level: 0, isAll: true, children: categories.value.map(wrapCat) },
])

async function loadCategories() {
  treeLoading.value = true
  try {
    categories.value = (await getCategoryTree()) ?? []
  } finally {
    treeLoading.value = false
  }
}

function findCat(id: number, list: CategoryNode[] = categories.value): { node: CategoryNode; list: CategoryNode[] } | null {
  for (const n of list) {
    if (n.id === id) return { node: n, list }
    const found = n.children?.length ? findCat(id, n.children) : null
    if (found) return found
  }
  return null
}

/** 当前选中的分类（null = 全部）。 */
const selectedCatId = ref<number | null>(null)

function onCatClick(data: CatTreeNode) {
  selectedCatId.value = data.isAll ? null : data.id
  searchProducts()
}

/* ---------------- 分类表单 ---------------- */
const catVisible = ref(false)
const catEditId = ref<number | null>(null)
const catSubmitting = ref(false)
const catFormRef = ref<FormInstance>()
const catForm = reactive<CategoryForm>({ parentId: 0, categoryCode: '', categoryName: '', sort: 0 })

const catSelectData = computed(() => [{ id: 0, categoryName: '顶级分类', level: 0, children: categories.value }])

const catRules: FormRules<CategoryForm> = {
  parentId: [{ type: 'number', min: 0, message: '请选择上级分类', trigger: 'change' }],
  categoryCode: [
    { required: true, message: '请输入分类编码', trigger: 'blur' },
    { pattern: /^FL-[A-Za-z0-9-]+$/, message: '编码需以 FL- 开头', trigger: 'blur' },
  ],
  categoryName: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { min: 2, max: 30, message: '分类名称长度 2-30 字', trigger: 'blur' },
  ],
  sort: [{ type: 'integer', min: 0, message: '排序号需为不小于 0 的整数', trigger: 'change' }],
}

function openCatCreate(parentId = 0, parentLevel = 0) {
  if (parentLevel >= 3) {
    ElMessage.warning('分类层级最多 3 级')
    return
  }
  catEditId.value = null
  Object.assign(catForm, { parentId, categoryCode: '', categoryName: '', sort: 0 })
  catFormRef.value?.clearValidate()
  catVisible.value = true
}

function openCatEdit(data: CatTreeNode) {
  const raw = data.raw as CategoryNode
  catEditId.value = raw.id
  Object.assign(catForm, {
    parentId: raw.parentId ?? 0,
    categoryCode: raw.categoryCode,
    categoryName: raw.categoryName,
    sort: raw.sort ?? 0,
  })
  catFormRef.value?.clearValidate()
  catVisible.value = true
}

async function submitCat() {
  const valid = await catFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (catEditId.value !== null && catForm.parentId === catEditId.value) {
    ElMessage.warning('不能选择自身作为上级分类')
    return
  }
  catSubmitting.value = true
  try {
    if (catEditId.value !== null) {
      const old = findCat(catEditId.value)
      await updateCategory(catEditId.value, { ...catForm })
      if (old && old.node.parentId === catForm.parentId) {
        old.node.categoryCode = catForm.categoryCode
        old.node.categoryName = catForm.categoryName
        old.node.sort = catForm.sort
      } else {
        await loadCategories()
      }
      ElMessage.success('保存成功')
    } else {
      const id = await createCategory({ ...catForm })
      const node: CategoryNode = {
        id,
        parentId: catForm.parentId,
        categoryCode: catForm.categoryCode,
        categoryName: catForm.categoryName,
        level: 1,
        sort: catForm.sort,
        status: 1,
        children: [],
      }
      if (catForm.parentId === 0) {
        categories.value = [...categories.value, node]
      } else {
        const parent = findCat(catForm.parentId)
        if (parent) {
          parent.node.children = [...(parent.node.children ?? []), node]
          node.level = parent.node.level + 1
        } else {
          await loadCategories()
        }
      }
      ElMessage.success('保存成功')
    }
    catVisible.value = false
  } catch {
    /* 业务错误由 request 统一提示 */
  } finally {
    catSubmitting.value = false
  }
}

async function onDeleteCat(data: CatTreeNode) {
  const ok = await confirmDialog({
    title: '删除分类',
    message: `确定删除分类「${data.label}（${data.code}）」吗？\n若存在下级分类或已关联商品，将无法删除。`,
    confirmText: '删除',
    type: 'danger',
  })
  if (!ok) return
  try {
    await deleteCategory(data.id)
    ElMessage.success('删除成功')
    const found = findCat(data.id)
    if (found) found.list = found.list.filter((n) => n.id !== data.id)
    if (selectedCatId.value === data.id) {
      selectedCatId.value = null
      searchProducts()
    }
    await loadCategories()
  } catch {
    /* 引用拦截由 request 统一提示 */
  }
}

/* ---------------- 右侧：商品列表 ---------------- */
const searchFields: SearchField[] = [
  { key: 'keyword', type: 'input', placeholder: '商品编码 / 名称 / 品牌' },
  {
    key: 'status',
    type: 'select',
    placeholder: '启用状态',
    options: ENABLE_STATUS.map((s) => ({ label: s.label, value: s.value })),
  },
]
const query = reactive<{ keyword?: string; status?: number | undefined }>({})

const loading = ref(false)
const list = ref<Product[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const columns: TableColumn[] = [
  { prop: 'productCode', label: '商品编码', width: 150 },
  { prop: 'productName', label: '商品名称', minWidth: 200, showOverflowTooltip: true },
  { prop: 'categoryName', label: '分类', width: 120, showOverflowTooltip: true },
  { prop: 'brand', label: '品牌', width: 110, showOverflowTooltip: true },
  { prop: 'unit', label: '单位', width: 72, align: 'center' },
  { prop: 'spec', label: '规格型号', minWidth: 120, showOverflowTooltip: true },
  { prop: 'price', label: '参考价', width: 110, align: 'right' },
  { prop: 'status', label: '状态', width: 88, align: 'center' },
  { prop: 'op', label: '操作', width: 150, align: 'right' },
]

function onQueryChange(value: Record<string, unknown>) {
  Object.assign(query, value)
}

async function loadProducts() {
  loading.value = true
  try {
    const res = await getProducts({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(query.keyword ? { keyword: query.keyword } : {}),
      ...(selectedCatId.value != null ? { categoryId: selectedCatId.value } : {}),
      ...(typeof query.status === 'number' ? { status: query.status } : {}),
    })
    list.value = res.list ?? []
    total.value = res.total ?? 0
    if (!list.value.length && pageNum.value > 1) {
      pageNum.value -= 1
      await loadProducts()
    }
  } finally {
    loading.value = false
  }
}

function searchProducts() {
  pageNum.value = 1
  loadProducts()
}

function onPageChange({ pageNum: p, pageSize: s }: { pageNum: number; pageSize: number }) {
  pageNum.value = p
  pageSize.value = s
  loadProducts()
}

/* ---------------- 商品表单 ---------------- */
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const UNIT_OPTIONS = ['件', '箱', '包', '台', '千克', '桶', '袋', '托']

const emptyForm = (): ProductForm => ({
  productCode: '',
  productName: '',
  categoryId: null,
  brand: '',
  unit: '件',
  spec: '',
  weight: null,
  volume: null,
  shelfLifeFlag: 0,
  shelfLifeDays: null,
  batchFlag: 1,
  minStock: null,
  maxStock: null,
  price: null,
  imageUrl: '',
  status: 1,
  remark: '',
})
const form = reactive<ProductForm>(emptyForm())

/** 仅允许选择末级分类（表单校验规则 §5）。 */
function validateLeafCategory(_rule: unknown, value: unknown, cb: (e?: Error) => void) {
  if (value == null) {
    cb(new Error('请选择商品分类'))
    return
  }
  const found = findCat(Number(value))
  if (found && found.node.children?.length) {
    cb(new Error('请选择具体分类（末级）'))
    return
  }
  cb()
}

function validateShelfLifeDays(_rule: unknown, value: unknown, cb: (e?: Error) => void) {
  if (form.shelfLifeFlag !== 1) {
    cb()
    return
  }
  if (value == null || value === '') {
    cb(new Error('请输入保质期天数'))
  } else if (!Number.isInteger(Number(value)) || Number(value) < 1 || Number(value) > 3650) {
    cb(new Error('保质期天数需为 1-3650 的整数'))
  } else {
    cb()
  }
}

function validateMaxStock(_rule: unknown, value: unknown, cb: (e?: Error) => void) {
  if (value == null || value === '') {
    cb()
    return
  }
  if (form.minStock != null && Number(value) <= Number(form.minStock)) {
    cb(new Error('最高库存需大于最低库存'))
  } else {
    cb()
  }
}

function validateImageUrl(_rule: unknown, value: unknown, cb: (e?: Error) => void) {
  const v = String(value ?? '').trim()
  if (!v || /^(https?:\/\/|\/)\S+$/i.test(v)) {
    cb()
  } else {
    cb(new Error('请输入正确的图片地址'))
  }
}

const rules: FormRules<ProductForm> = {
  productCode: [
    { required: true, message: '请输入商品编码', trigger: 'blur' },
    { pattern: /^P-[A-Za-z0-9-]+$/, message: '编码需以 P- 开头', trigger: 'blur' },
  ],
  productName: [
    { required: true, message: '请输入商品名称', trigger: 'blur' },
    { min: 2, max: 50, message: '商品名称长度 2-50 字', trigger: 'blur' },
  ],
  categoryId: [{ required: true, validator: validateLeafCategory, trigger: 'change' }],
  brand: [{ max: 30, message: '品牌不能超过 30 字', trigger: 'blur' }],
  unit: [
    { required: true, message: '请选择或输入单位', trigger: 'change' },
    { max: 10, message: '单位不能超过 10 字', trigger: 'blur' },
  ],
  spec: [{ max: 50, message: '规格型号不能超过 50 字', trigger: 'blur' }],
  weight: [{ type: 'number', min: 0, message: '重量需为不小于 0 的数值，最多 3 位小数', trigger: 'blur' }],
  volume: [{ type: 'number', min: 0, message: '体积需为不小于 0 的数值，最多 3 位小数', trigger: 'blur' }],
  shelfLifeFlag: [{ required: true, message: '请选择是否启用保质期管理', trigger: 'change' }],
  shelfLifeDays: [{ validator: validateShelfLifeDays, trigger: 'blur' }],
  batchFlag: [{ required: true, message: '请选择是否启用批次管理', trigger: 'change' }],
  minStock: [{ type: 'number', min: 0, message: '最低库存需为不小于 0 的数值', trigger: 'blur' }],
  maxStock: [{ validator: validateMaxStock, trigger: 'blur' }],
  price: [{ type: 'number', min: 0, message: '价格需为不小于 0 的数值，最多 2 位小数', trigger: 'blur' }],
  imageUrl: [{ validator: validateImageUrl, trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  remark: [{ max: 200, message: '备注不能超过 200 字', trigger: 'blur' }],
}

function resetForm() {
  Object.assign(form, emptyForm())
  formRef.value?.clearValidate()
}

function openCreate() {
  isEdit.value = false
  editId.value = null
  resetForm()
  if (selectedCatId.value != null && findCat(selectedCatId.value)) {
    form.categoryId = selectedCatId.value
  }
  dialogVisible.value = true
}

function openEdit(row: Product) {
  isEdit.value = true
  editId.value = row.id
  resetForm()
  Object.assign(form, {
    productCode: row.productCode,
    productName: row.productName,
    categoryId: row.categoryId ?? null,
    brand: row.brand ?? '',
    unit: row.unit,
    spec: row.spec ?? '',
    weight: row.weight ?? null,
    volume: row.volume ?? null,
    shelfLifeFlag: row.shelfLifeFlag,
    shelfLifeDays: row.shelfLifeDays ?? null,
    batchFlag: row.batchFlag,
    minStock: row.minStock ?? null,
    maxStock: row.maxStock ?? null,
    price: row.price ?? null,
    imageUrl: row.imageUrl ?? '',
    status: row.status,
    remark: row.remark ?? '',
  })
  dialogVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload: ProductForm = { ...form, categoryId: Number(form.categoryId) }
    if (isEdit.value && editId.value !== null) {
      await updateProduct(editId.value, payload)
    } else {
      await createProduct(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadProducts()
  } catch {
    /* 业务错误由 request 统一提示 */
  } finally {
    submitting.value = false
  }
}

async function onDelete(row: Product) {
  const ok = await confirmDialog({
    title: '删除商品',
    message: `确定删除商品「${row.productName}（${row.productCode}）」吗？\n若存在库存、批次、流水或被单据引用，将无法删除。`,
    confirmText: '删除',
    type: 'danger',
  })
  if (!ok) return
  try {
    await deleteProduct(row.id)
    ElMessage.success('删除成功')
    loadProducts()
  } catch {
    /* 引用拦截由 request 统一提示 */
  }
}

/* ---------------- 商品详情抽屉 ---------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<Product | null>(null)

async function openDetail(row: Product) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getProductDetail(row.id)
  } catch {
    detail.value = row
  } finally {
    detailLoading.value = false
  }
}

function fmt(v?: number | null, suffix = ''): string {
  return v != null ? `${v}${suffix}` : '—'
}

const detailFields = computed<DetailField[]>(() => {
  const p = detail.value
  if (!p) return []
  return [
    { label: '商品编码', value: p.productCode },
    { label: '商品名称', value: p.productName },
    { label: '所属分类', value: p.categoryName ?? '—' },
    { label: '品牌', value: p.brand ?? '—' },
    { label: '单位', value: p.unit },
    { label: '规格型号', value: p.spec ?? '—' },
    { label: '重量', value: fmt(p.weight, ' kg') },
    { label: '体积', value: fmt(p.volume, ' m³') },
    { label: '保质期管理', value: p.shelfLifeFlag === 1 ? `启用（${fmt(p.shelfLifeDays, ' 天')}）` : '不启用' },
    { label: '批次管理', value: p.batchFlag === 1 ? '启用' : '不启用' },
    { label: '最低库存', value: fmt(p.minStock) },
    { label: '最高库存', value: fmt(p.maxStock) },
    { label: '参考采购价', value: p.price != null ? `¥ ${Number(p.price).toFixed(2)}` : '—' },
    { label: '备注', value: p.remark ?? '—' },
    { label: '创建人', value: p.createBy ?? '—' },
    { label: '创建时间', value: p.createTime ?? '—' },
  ]
})

function onDetailEdit() {
  if (detail.value) {
    detailVisible.value = false
    openEdit(detail.value)
  }
}

onMounted(() => {
  loadCategories()
  loadProducts()
})
</script>

<template>
  <div class="page">
    <PageHeader title="商品管理" desc="分类树维护与商品档案管理：编码、规格、保质期与库存阈值">
      <template #actions>
        <el-button v-permission="'sys:product:add'" type="primary" :icon="Plus" @click="openCreate">
          新增商品
        </el-button>
      </template>
    </PageHeader>

    <div class="split split--tree pd-layout">
      <!-- 左侧：分类树 -->
      <aside class="panel pd-cats">
        <div class="panel__header">
          <span class="panel__title">
            商品分类
            <span class="panel__desc">最多 3 级</span>
          </span>
          <div class="pd-cats__ops">
            <el-button size="small" :icon="Refresh" :loading="treeLoading" @click="loadCategories">刷新</el-button>
            <el-button
              v-permission="'sys:category:add'"
              size="small"
              type="primary"
              plain
              :icon="Plus"
              @click="openCatCreate()"
            >
              新增分类
            </el-button>
          </div>
        </div>

        <div v-loading="treeLoading" class="pd-cats__body">
          <el-tree
            :data="displayTree"
            node-key="key"
            highlight-current-row
            default-expand-all
            :expand-on-click-node="false"
            :props="{ label: 'label', children: 'children' }"
            empty-text="暂无分类数据"
            @node-click="onCatClick"
          >
            <template #default="{ data }">
              <span class="tree-node" :class="{ 'is-inactive': data.raw && data.raw.status === 0 }">
                <span class="tree-node__label" :title="data.label">{{ data.label }}</span>
                <span class="tree-node__code mono">{{ data.code }}</span>
                <span class="tree-node__actions" @click.stop>
                  <template v-if="!data.isAll">
                    <el-button
                      v-if="hasPerm('sys:category:add')"
                      link
                      size="small"
                      :icon="Plus"
                      :disabled="data.level >= 3"
                      :title="data.level >= 3 ? '分类层级最多 3 级' : '新增子分类'"
                      @click="openCatCreate(data.id, data.level)"
                    />
                    <el-button
                      v-if="hasPerm('sys:category:edit')"
                      link
                      size="small"
                      :icon="Edit"
                      title="编辑分类"
                      @click="openCatEdit(data)"
                    />
                    <el-button
                      v-if="hasPerm('sys:category:delete')"
                      link
                      size="small"
                      :icon="Delete"
                      class="is-danger"
                      title="删除分类"
                      @click="onDeleteCat(data)"
                    />
                  </template>
                  <el-button
                    v-else
                    link
                    size="small"
                    :icon="Plus"
                    title="新增顶级分类"
                    @click="openCatCreate(0, 0)"
                  />
                </span>
              </span>
            </template>
          </el-tree>
        </div>
      </aside>

      <!-- 右侧：商品列表 -->
      <section class="pd-main">
        <SearchForm
          class="pd-search"
          :model-value="query"
          :fields="searchFields"
          :loading="loading"
          :collapsible="false"
          @update:model-value="onQueryChange"
          @search="searchProducts"
          @reset="() => { query.keyword = undefined; query.status = undefined }"
        />

        <AppTable
          :columns="columns"
          :data="list"
          :loading="loading"
          :pagination="{ total, pageNum, pageSize }"
          row-key="id"
          @row-click="(row) => openDetail(row as never)"
          @page-change="onPageChange"
        >
          <template #cell-price="{ row }">
            <span class="num">{{ row.price != null ? `¥ ${Number(row.price).toFixed(2)}` : '—' }}</span>
          </template>
          <template #col-status="{ row }">
            <StatusTag :status="enableTagStatus(row.status)" :label="enableStatusInfo(row.status).label" />
          </template>
          <template #col-op="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-permission="'sys:product:edit'" link type="primary" size="small" @click.stop="openEdit(row)">
              编辑
            </el-button>
            <el-button v-permission="'sys:product:delete'" link type="danger" size="small" @click.stop="onDelete(row)">
              删除
            </el-button>
          </template>
        </AppTable>
      </section>
    </div>

    <!-- 分类表单 -->
    <el-dialog
      v-model="catVisible"
      :title="catEditId !== null ? '编辑分类' : '新增分类'"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form
        ref="catFormRef"
        :model="catForm"
        :rules="catRules"
        label-width="96px"
        @submit.prevent
      >
        <el-form-item label="上级分类" prop="parentId">
          <el-tree-select
            v-model="catForm.parentId"
            :data="catSelectData"
            :props="{ label: 'categoryName', children: 'children' }"
            node-key="id"
            check-strictly
            default-expand-all
            placeholder="选择上级分类"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="分类编码" prop="categoryCode">
          <el-input v-model="catForm.categoryCode" placeholder="如 FL-001" maxlength="20" />
        </el-form-item>
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="catForm.categoryName" placeholder="2-30 字符" maxlength="30" />
        </el-form-item>
        <el-form-item label="排序号" prop="sort">
          <el-input-number v-model="catForm.sort" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catVisible = false">取消</el-button>
        <el-button type="primary" :loading="catSubmitting" @click="submitCat">保存</el-button>
      </template>
    </el-dialog>

    <!-- 商品表单 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑商品' : '新增商品'"
      width="760px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" @submit.prevent>
        <div class="form-grid">
          <el-form-item label="商品编码" prop="productCode">
            <el-input v-model="form.productCode" placeholder="如 P-0001" maxlength="30" />
          </el-form-item>
          <el-form-item label="商品名称" prop="productName">
            <el-input v-model="form.productName" placeholder="2-50 字符" maxlength="50" />
          </el-form-item>
          <el-form-item label="所属分类" prop="categoryId">
            <el-tree-select
              v-model="form.categoryId"
              :data="catSelectData"
              :props="{ label: 'categoryName', children: 'children' }"
              node-key="id"
              check-strictly
              :render-after-expand="false"
              placeholder="请选择分类"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="品牌" prop="brand">
            <el-input v-model="form.brand" placeholder="选填" maxlength="30" />
          </el-form-item>
          <el-form-item label="单位" prop="unit">
            <el-select
              v-model="form.unit"
              filterable
              allow-create
              default-first-option
              placeholder="请选择或输入"
              style="width: 100%"
            >
              <el-option v-for="u in UNIT_OPTIONS" :key="u" :label="u" :value="u" />
            </el-select>
          </el-form-item>
          <el-form-item label="规格型号" prop="spec">
            <el-input v-model="form.spec" placeholder="选填，最多 50 字" maxlength="50" />
          </el-form-item>
          <el-form-item label="重量（kg）" prop="weight">
            <el-input-number v-model="form.weight" :min="0" :precision="3" :controls="false" placeholder="选填" style="width: 100%" />
          </el-form-item>
          <el-form-item label="体积（m³）" prop="volume">
            <el-input-number v-model="form.volume" :min="0" :precision="3" :controls="false" placeholder="选填" style="width: 100%" />
          </el-form-item>
          <el-form-item label="保质期管理" prop="shelfLifeFlag">
            <el-switch v-model="form.shelfLifeFlag" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="不启用" />
          </el-form-item>
          <el-form-item v-if="form.shelfLifeFlag === 1" label="保质期天数" prop="shelfLifeDays">
            <el-input-number v-model="form.shelfLifeDays" :min="1" :max="3650" :precision="0" placeholder="1-3650" style="width: 100%" />
          </el-form-item>
          <el-form-item label="批次管理" prop="batchFlag">
            <el-switch v-model="form.batchFlag" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="不启用" />
          </el-form-item>
          <el-form-item label="参考采购价" prop="price">
            <el-input-number v-model="form.price" :min="0" :precision="2" :controls="false" placeholder="选填" style="width: 100%" />
          </el-form-item>
          <el-form-item label="最低库存" prop="minStock">
            <el-input-number v-model="form.minStock" :min="0" :precision="3" :controls="false" placeholder="选填" style="width: 100%" />
          </el-form-item>
          <el-form-item label="最高库存" prop="maxStock">
            <el-input-number v-model="form.maxStock" :min="0" :precision="3" :controls="false" placeholder="需大于最低库存" style="width: 100%" />
          </el-form-item>
          <el-form-item label="状态" prop="status" class="form-grid__full">
            <el-radio-group v-model="form.status">
              <el-radio :value="1">启用</el-radio>
              <el-radio :value="0">停用</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="图片地址" prop="imageUrl" class="form-grid__full">
            <el-input v-model="form.imageUrl" placeholder="URL 或相对路径，选填" maxlength="200" />
          </el-form-item>
          <el-form-item label="备注" prop="remark" class="form-grid__full">
            <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="选填" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 商品详情 -->
    <OrderDetailDrawer
      v-model:visible="detailVisible"
      title="商品详情"
      :fields="detailFields"
      :loading="detailLoading"
    >
      <template #head-extra>
        <StatusTag
          v-if="detail"
          :status="enableTagStatus(detail.status)"
          :label="enableStatusInfo(detail.status).label"
        />
      </template>
      <template #extra>
        <div v-if="detail && detail.imageUrl" class="pd-img">
          <el-image
            :src="detail.imageUrl"
            fit="contain"
            :preview-src-list="[detail.imageUrl]"
            preview-teleported
            class="pd-img__img"
          >
            <template #error>
              <div class="pd-img__fallback t-xs t-3">图片加载失败</div>
            </template>
          </el-image>
        </div>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="hasPerm('sys:product:edit')" type="primary" :loading="detailLoading" @click="onDetailEdit">
          编辑该商品
        </el-button>
      </template>
    </OrderDetailDrawer>
  </div>
</template>

<style scoped lang="scss">
.pd-layout {
  min-height: 480px;
}

.pd-cats {
  display: flex;
  flex-direction: column;
  min-height: 0;

  &__ops {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__body {
    flex: 1;
    min-height: 0;
    overflow: auto;
    padding: var(--sp-2);
  }
}

.pd-main {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  min-width: 0;
}

.pd-search {
  padding: var(--sp-3) var(--sp-4);
}

/* 分类树节点 */
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
  padding-right: var(--sp-1);
  font-size: var(--fs-sm);

  &__label {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__code {
    font-size: var(--fs-xs);
    color: var(--c-text-3);
    flex-shrink: 0;
  }

  &__actions {
    display: flex;
    align-items: center;
    opacity: 0;
    flex-shrink: 0;
    transition: opacity var(--dur-fast) var(--ease-out);

    :deep(.el-button + .el-button) {
      margin-left: 0;
    }

    .is-danger:hover {
      color: var(--c-danger);
    }
  }

  &:hover &__actions,
  &__actions:focus-within {
    opacity: 1;
  }

  &.is-inactive {
    .tree-node__label {
      color: var(--c-text-3);
    }
  }
}

.pd-img {
  margin-top: var(--sp-3);

  &__img {
    width: 120px;
    height: 120px;
    border: 1px solid var(--c-border);
    border-radius: var(--radius-md);
    background-color: var(--c-bg-muted);
  }

  &__fallback {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
  }
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: var(--sp-5);

  &__full {
    grid-column: 1 / -1;
  }
}

:deep(.el-tree-node__content) {
  height: 30px;
  border-radius: var(--radius-sm);

  &:hover {
    background-color: var(--c-bg-subtle);
  }
}

:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: var(--c-primary-soft);
  color: var(--c-primary);

  .tree-node__code {
    color: var(--c-primary);
  }
}

@media (max-width: 768px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
