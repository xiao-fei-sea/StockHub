<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { AppTable, OrderDetailDrawer, PageHeader, SearchForm, StatusTag, confirmDialog } from '@/components'
import type { SearchField, TableColumn, DetailField } from '@/components'
import { createSupplier, deleteSupplier, getSuppliers, updateSupplier } from '@/api/supplier'
import type { Supplier, SupplierForm } from '@/types/entity'
import { enableStatusInfo } from '@/utils/dict'
import { enableTagStatus } from '../components/status'
import { usePermission } from '@/composables/usePermission'

defineOptions({ name: 'BaseSupplier' })

const { hasPerm } = usePermission()

/* ---------------- 状态分段 + 查询 ---------------- */
type SegValue = number | undefined
const segments: { label: string; value: SegValue }[] = [
  { label: '全部', value: undefined },
  { label: '正常', value: 1 },
  { label: '停用', value: 0 },
]
const statusSeg = ref<SegValue>(undefined)

const searchFields: SearchField[] = [
  { key: 'keyword', type: 'input', placeholder: '供应商编码 / 名称 / 联系人' },
]
const query = reactive<{ keyword?: string }>({})

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const list = ref<Supplier[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function loadList() {
  loading.value = true
  try {
    const res = await getSuppliers({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(query.keyword ? { keyword: query.keyword } : {}),
      ...(statusSeg.value !== undefined ? { status: statusSeg.value } : {}),
    })
    list.value = res.list ?? []
    total.value = res.total ?? 0
    if (!list.value.length && pageNum.value > 1) {
      pageNum.value -= 1
      await loadList()
    }
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNum.value = 1
  loadList()
}

function onReset() {
  query.keyword = undefined
}

function onQueryChange(value: Record<string, unknown>) {
  Object.assign(query, value)
}

function switchSeg(value: SegValue) {
  if (statusSeg.value === value) return
  statusSeg.value = value
  onSearch()
}

function onPageChange({ pageNum: p, pageSize: s }: { pageNum: number; pageSize: number }) {
  pageNum.value = p
  pageSize.value = s
  loadList()
}

const columns: TableColumn[] = [
  { prop: 'supplierCode', label: '供应商编码', width: 140 },
  { prop: 'supplierName', label: '供应商名称', minWidth: 180, showOverflowTooltip: true },
  { prop: 'contactName', label: '联系人', width: 110 },
  { prop: 'contactPhone', label: '联系电话', width: 140 },
  { prop: 'email', label: '邮箱', minWidth: 160, showOverflowTooltip: true },
  { prop: 'status', label: '状态', width: 90, align: 'center' },
  { prop: 'updateTime', label: '更新时间', width: 170 },
  { prop: 'actions', label: '操作', width: 150, align: 'right' },
]

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const submitting = ref(false)

const emptyForm = (): SupplierForm => ({
  supplierCode: '',
  supplierName: '',
  contactName: '',
  contactPhone: '',
  email: '',
  address: '',
  bankInfo: '',
  status: 1,
  remark: '',
})
const form = reactive<SupplierForm>(emptyForm())

const rules: FormRules<SupplierForm> = {
  supplierCode: [
    { required: true, message: '请输入供应商编码', trigger: 'blur' },
    { pattern: /^S-[A-Za-z0-9-]+$/, message: '编码需以 S- 开头', trigger: 'blur' },
  ],
  supplierName: [
    { required: true, message: '请输入供应商名称', trigger: 'blur' },
    { min: 2, max: 50, message: '供应商名称长度 2-50 字', trigger: 'blur' },
  ],
  contactName: [{ max: 20, message: '联系人不能超过 20 字', trigger: 'blur' }],
  contactPhone: [
    { pattern: /^(1[3-9]\d{9}|(\d{3,4}-)?\d{7,8})$/, message: '请输入正确的联系电话', trigger: 'blur' },
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
    { max: 50, message: '邮箱不能超过 50 字', trigger: 'blur' },
  ],
  address: [{ max: 100, message: '地址不能超过 100 字', trigger: 'blur' }],
  bankInfo: [{ max: 100, message: '银行信息不能超过 100 字', trigger: 'blur' }],
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
  dialogVisible.value = true
}

function openEdit(row: Supplier) {
  isEdit.value = true
  editId.value = row.id
  resetForm()
  Object.assign(form, {
    supplierCode: row.supplierCode,
    supplierName: row.supplierName,
    contactName: row.contactName ?? '',
    contactPhone: row.contactPhone ?? '',
    email: row.email ?? '',
    address: row.address ?? '',
    bankInfo: row.bankInfo ?? '',
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
    if (isEdit.value && editId.value !== null) {
      await updateSupplier(editId.value, { ...form })
    } else {
      await createSupplier({ ...form })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadList()
  } catch {
    /* 业务错误由 request 统一提示 */
  } finally {
    submitting.value = false
  }
}

/* ---------------- 删除 ---------------- */
async function onDelete(row: Supplier) {
  const ok = await confirmDialog({
    title: '删除供应商',
    message: `确定删除供应商「${row.supplierName}（${row.supplierCode}）」吗？\n若供应商已被入库单引用，将无法删除。`,
    confirmText: '删除',
    type: 'danger',
  })
  if (!ok) return
  try {
    await deleteSupplier(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    /* 引用拦截等业务错误由 request 统一提示 */
  }
}

/* ---------------- 详情（直接用行数据渲染，无独立详情接口） ---------------- */
const detailVisible = ref(false)
const detail = ref<Supplier | null>(null)

function openDetail(row: Supplier) {
  detail.value = row
  detailVisible.value = true
}

const detailFields = computed<DetailField[]>(() => {
  const s = detail.value
  if (!s) return []
  return [
    { label: '供应商编码', value: s.supplierCode },
    { label: '供应商名称', value: s.supplierName },
    { label: '联系人', value: s.contactName ?? '—' },
    { label: '联系电话', value: s.contactPhone ?? '—' },
    { label: '邮箱', value: s.email ?? '—' },
    { label: '地址', value: s.address ?? '—' },
    { label: '银行信息', value: s.bankInfo ?? '—' },
    { label: '备注', value: s.remark ?? '—' },
    { label: '创建人', value: s.createBy ?? '—' },
    { label: '创建时间', value: s.createTime ?? '—' },
    { label: '更新人', value: s.updateBy ?? '—' },
    { label: '更新时间', value: s.updateTime ?? '—' },
  ]
})

function onDetailEdit() {
  if (detail.value) {
    detailVisible.value = false
    openEdit(detail.value)
  }
}

onMounted(loadList)
</script>

<template>
  <div class="page">
    <PageHeader title="供应商管理" desc="维护入库业务往来的供应商档案与联系信息">
      <template #actions>
        <el-button v-permission="'sys:supplier:add'" type="primary" :icon="Plus" @click="openCreate">
          新增供应商
        </el-button>
      </template>
    </PageHeader>

    <section class="panel sup-controls">
      <div class="seg" role="tablist" aria-label="按状态筛选">
        <button
          v-for="s in segments"
          :key="String(s.value)"
          type="button"
          class="seg__item"
          :class="{ 'is-active': statusSeg === s.value }"
          @click="switchSeg(s.value)"
        >
          {{ s.label }}
        </button>
      </div>
      <SearchForm
        class="sup-controls__search"
        :model-value="query"
        :fields="searchFields"
        :loading="loading"
        :collapsible="false"
        @update:model-value="onQueryChange"
        @search="onSearch"
        @reset="onReset"
      />
    </section>

    <AppTable
      :columns="columns"
      :data="(list as unknown as Record<string, unknown>[])"
      :loading="loading"
      :pagination="{ total, pageNum, pageSize }"
      @page-change="onPageChange"
    >
      <template #col-supplierCode="{ row }">
        <span class="mono t-2">{{ row.supplierCode }}</span>
      </template>
      <template #col-supplierName="{ row }">
        <span class="sup-name">{{ row.supplierName }}</span>
      </template>
      <template #col-contactName="{ row }">
        <span :class="{ 't-3': !row.contactName }">{{ row.contactName || '—' }}</span>
      </template>
      <template #col-contactPhone="{ row }">
        <span class="num" :class="{ 't-3': !row.contactPhone }">{{ row.contactPhone || '—' }}</span>
      </template>
      <template #col-email="{ row }">
        <span :class="{ 't-3': !row.email }">{{ row.email || '—' }}</span>
      </template>
      <template #col-status="{ row }">
        <StatusTag :status="enableTagStatus(row.status)" :label="enableStatusInfo(row.status).label" />
      </template>
      <template #col-updateTime="{ row }">
        <span class="t-3 t-sm">{{ row.updateTime || '—' }}</span>
      </template>
      <template #col-actions="{ row }">
        <div class="row-actions" @click.stop>
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button v-permission="'sys:supplier:edit'" link type="primary" @click="openEdit(row)">
            编辑
          </el-button>
          <el-button v-permission="'sys:supplier:delete'" link type="danger" @click="onDelete(row)">
            删除
          </el-button>
        </div>
      </template>
    </AppTable>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑供应商' : '新增供应商'"
      width="680px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" @submit.prevent>
        <div class="form-grid">
          <el-form-item label="供应商编码" prop="supplierCode">
            <el-input v-model="form.supplierCode" placeholder="如 S-001" maxlength="20" />
          </el-form-item>
          <el-form-item label="供应商名称" prop="supplierName">
            <el-input v-model="form.supplierName" placeholder="2-50 字符" maxlength="50" />
          </el-form-item>
          <el-form-item label="联系人" prop="contactName">
            <el-input v-model="form.contactName" placeholder="选填" maxlength="20" />
          </el-form-item>
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="form.contactPhone" placeholder="手机号或座机" maxlength="20" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" placeholder="选填" maxlength="50" />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio :value="1">正常</el-radio>
              <el-radio :value="0">停用</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="地址" prop="address" class="form-grid__full">
            <el-input v-model="form.address" placeholder="选填，最多 100 字" maxlength="100" />
          </el-form-item>
          <el-form-item label="银行信息" prop="bankInfo" class="form-grid__full">
            <el-input v-model="form.bankInfo" placeholder="选填，最多 100 字" maxlength="100" />
          </el-form-item>
          <el-form-item label="备注" prop="remark" class="form-grid__full">
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="3"
              placeholder="选填，最多 200 字"
              maxlength="200"
              show-word-limit
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <OrderDetailDrawer
      v-model:visible="detailVisible"
      title="供应商详情"
      :fields="detailFields"
    >
      <template #head-extra>
        <StatusTag
          v-if="detail"
          :status="enableTagStatus(detail.status)"
          :label="enableStatusInfo(detail.status).label"
        />
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="hasPerm('sys:supplier:edit')" type="primary" @click="onDetailEdit">
          编辑该供应商
        </el-button>
      </template>
    </OrderDetailDrawer>
  </div>
</template>

<style scoped lang="scss">
.sup-controls {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-4);
  padding: var(--sp-3) var(--sp-4);
  flex-wrap: wrap;

  &__search {
    padding: 0;
    border: none;
    background: transparent;
    box-shadow: none;
    flex-shrink: 0;
  }
}

.seg {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background-color: var(--c-bg-muted);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-md);

  &__item {
    border: none;
    background: transparent;
    padding: 5px 14px;
    font-family: inherit;
    font-size: var(--fs-sm);
    color: var(--c-text-2);
    border-radius: var(--radius-sm);
    cursor: pointer;
    transition:
      background-color var(--dur-fast) var(--ease-out),
      color var(--dur-fast) var(--ease-out);

    &:hover {
      color: var(--c-text);
    }

    &.is-active {
      background-color: var(--c-bg-surface);
      color: var(--c-primary);
      font-weight: var(--fw-medium);
      box-shadow: var(--shadow-xs);
    }
  }
}

.sup-name {
  font-weight: var(--fw-medium);
  color: var(--c-text);
}

.row-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: var(--sp-5);

  &__full {
    grid-column: 1 / -1;
  }
}

@media (max-width: 768px) {
  .sup-controls {
    flex-direction: column;
    align-items: stretch;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
