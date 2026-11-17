<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { OrderDetailDrawer, PageHeader, StatusTag, confirmDialog } from '@/components'
import type { DetailField } from '@/components'
import { createCustomer, deleteCustomer, getCustomers, updateCustomer } from '@/api/customer'
import type { Customer, CustomerForm } from '@/types/entity'
import { enableStatusInfo } from '@/utils/dict'
import { enableTagStatus } from '../components/status'
import { usePermission } from '@/composables/usePermission'

defineOptions({ name: 'BaseCustomer' })

const { hasPerm } = usePermission()

/* ---------------- 状态分段 + 搜索 ---------------- */
type SegValue = number | undefined
const segments: { label: string; value: SegValue }[] = [
  { label: '全部', value: undefined },
  { label: '正常', value: 1 },
  { label: '停用', value: 0 },
]
const statusSeg = ref<SegValue>(undefined)

const query = reactive<{ keyword?: string }>({})

/* ---------------- 列表（行式布局，与供应商表格页区分） ---------------- */
const loading = ref(false)
const list = ref<Customer[]>([])
const pageNum = ref(1)
const pageSize = ref(8)
const total = ref(0)

async function loadList() {
  loading.value = true
  try {
    const res = await getCustomers({
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

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const submitting = ref(false)

const emptyForm = (): CustomerForm => ({
  customerCode: '',
  customerName: '',
  contactName: '',
  contactPhone: '',
  email: '',
  address: '',
  status: 1,
  remark: '',
})
const form = reactive<CustomerForm>(emptyForm())

const rules: FormRules<CustomerForm> = {
  customerCode: [
    { required: true, message: '请输入客户编码', trigger: 'blur' },
    { pattern: /^C-[A-Za-z0-9-]+$/, message: '编码需以 C- 开头', trigger: 'blur' },
  ],
  customerName: [
    { required: true, message: '请输入客户名称', trigger: 'blur' },
    { min: 2, max: 50, message: '客户名称长度 2-50 字', trigger: 'blur' },
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

function openEdit(row: Customer) {
  isEdit.value = true
  editId.value = row.id
  resetForm()
  Object.assign(form, {
    customerCode: row.customerCode,
    customerName: row.customerName,
    contactName: row.contactName ?? '',
    contactPhone: row.contactPhone ?? '',
    email: row.email ?? '',
    address: row.address ?? '',
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
      await updateCustomer(editId.value, { ...form })
    } else {
      await createCustomer({ ...form })
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
async function onDelete(row: Customer) {
  const ok = await confirmDialog({
    title: '删除客户',
    message: `确定删除客户「${row.customerName}（${row.customerCode}）」吗？\n若客户已被出库单引用，将无法删除。`,
    confirmText: '删除',
    type: 'danger',
  })
  if (!ok) return
  try {
    await deleteCustomer(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    /* 引用拦截等业务错误由 request 统一提示 */
  }
}

/* ---------------- 详情 ---------------- */
const detailVisible = ref(false)
const detail = ref<Customer | null>(null)

function openDetail(row: Customer) {
  detail.value = row
  detailVisible.value = true
}

const detailFields = computed<DetailField[]>(() => {
  const c = detail.value
  if (!c) return []
  return [
    { label: '客户编码', value: c.customerCode },
    { label: '客户名称', value: c.customerName },
    { label: '联系人', value: c.contactName ?? '—' },
    { label: '联系电话', value: c.contactPhone ?? '—' },
    { label: '邮箱', value: c.email ?? '—' },
    { label: '地址', value: c.address ?? '—' },
    { label: '备注', value: c.remark ?? '—' },
    { label: '创建人', value: c.createBy ?? '—' },
    { label: '创建时间', value: c.createTime ?? '—' },
    { label: '更新人', value: c.updateBy ?? '—' },
    { label: '更新时间', value: c.updateTime ?? '—' },
  ]
})

function onDetailEdit() {
  if (detail.value) {
    detailVisible.value = false
    openEdit(detail.value)
  }
}

const countText = computed(() => (total.value ? `共 ${total.value} 位客户` : ''))

onMounted(loadList)
</script>

<template>
  <div class="page">
    <PageHeader title="客户管理" desc="维护出库业务往来的客户档案与联系信息">
      <template #actions>
        <el-button v-permission="'sys:customer:add'" type="primary" :icon="Plus" @click="openCreate">
          新增客户
        </el-button>
      </template>
    </PageHeader>

    <!-- 行式列表：编码/名称主行 + 联系信息副行 + 右侧状态与操作 -->
    <section class="panel cus-panel">
      <div class="cus-bar">
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
        <div class="cus-bar__right">
          <span v-if="countText" class="t-xs t-3">{{ countText }}</span>
          <div class="cus-search">
            <el-input
              v-model="query.keyword"
              placeholder="客户编码 / 名称 / 联系人"
              clearable
              :prefix-icon="Search"
              @keyup.enter="onSearch"
              @clear="onSearch"
            />
            <el-button :loading="loading" @click="onSearch">搜索</el-button>
          </div>
        </div>
      </div>

      <ul v-loading="loading" class="cus-list">
        <li v-for="row in list" :key="row.id" class="cus-row">
          <div class="cus-row__main">
            <span class="cus-row__code mono">{{ row.customerCode }}</span>
            <span class="cus-row__name">{{ row.customerName }}</span>
            <StatusTag :status="enableTagStatus(row.status)" :label="enableStatusInfo(row.status).label" />
          </div>
          <div class="cus-row__meta">
            <span>
              <em>联系人</em>
              {{ row.contactName || '—' }}
            </span>
            <span>
              <em>电话</em>
              <b class="num">{{ row.contactPhone || '—' }}</b>
            </span>
            <span>
              <em>邮箱</em>
              {{ row.email || '—' }}
            </span>
            <span>
              <em>地址</em>
              {{ row.address || '—' }}
            </span>
          </div>
          <div class="cus-row__ops" @click.stop>
            <span class="t-xs t-3">{{ row.updateTime || '' }}</span>
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button
              v-permission="'sys:customer:edit'"
              link
              type="primary"
              size="small"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'sys:customer:delete'"
              link
              type="danger"
              size="small"
              @click="onDelete(row)"
            >
              删除
            </el-button>
          </div>
        </li>

        <li v-if="!loading && !list.length" class="cus-empty">
          <el-icon :size="28"><Search /></el-icon>
          <p>暂无客户数据</p>
          <el-button v-if="hasPerm('sys:customer:add')" type="primary" plain size="small" @click="openCreate">
            新增第一位客户
          </el-button>
        </li>
      </ul>

      <div class="cus-pager">
        <el-pagination
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="pageNum"
          :page-size="pageSize"
          :page-sizes="[8, 15, 30, 50]"
          background
          @current-change="(p: number) => onPageChange({ pageNum: p, pageSize })"
          @size-change="(s: number) => onPageChange({ pageNum: 1, pageSize: s })"
        />
      </div>
    </section>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑客户' : '新增客户'"
      width="640px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" @submit.prevent>
        <div class="form-grid">
          <el-form-item label="客户编码" prop="customerCode">
            <el-input v-model="form.customerCode" placeholder="如 C-001" maxlength="20" />
          </el-form-item>
          <el-form-item label="客户名称" prop="customerName">
            <el-input v-model="form.customerName" placeholder="2-50 字符" maxlength="50" />
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
    <OrderDetailDrawer v-model:visible="detailVisible" title="客户详情" :fields="detailFields">
      <template #head-extra>
        <StatusTag
          v-if="detail"
          :status="enableTagStatus(detail.status)"
          :label="enableStatusInfo(detail.status).label"
        />
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="hasPerm('sys:customer:edit')" type="primary" @click="onDetailEdit">
          编辑该客户
        </el-button>
      </template>
    </OrderDetailDrawer>
  </div>
</template>

<style scoped lang="scss">
.cus-panel {
  padding: 0;
  overflow: hidden;
}

.cus-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  flex-wrap: wrap;
  padding: var(--sp-3) var(--sp-5);
  border-bottom: 1px solid var(--c-border);

  &__right {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
  }
}

.cus-search {
  display: flex;
  align-items: center;
  gap: var(--sp-2);

  :deep(.el-input) {
    width: 240px;
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

.cus-list {
  margin: 0;
  padding: 0;
  list-style: none;
  min-height: 260px;
}

.cus-row {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) minmax(0, 2fr) auto;
  align-items: center;
  gap: var(--sp-4);
  padding: var(--sp-3) var(--sp-5);
  border-bottom: 1px solid var(--c-border);
  transition: background-color var(--dur-fast) var(--ease-out);

  &:hover {
    background-color: var(--c-bg-subtle);
  }

  &:last-child {
    border-bottom: none;
  }

  &__main {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    min-width: 0;
  }

  &__code {
    font-size: var(--fs-xs);
    color: var(--c-text-3);
    background-color: var(--c-bg-muted);
    border: 1px solid var(--c-border);
    border-radius: var(--radius-sm);
    padding: 1px 6px;
    white-space: nowrap;
  }

  &__name {
    font-size: var(--fs-md);
    font-weight: var(--fw-medium);
    color: var(--c-text);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    gap: var(--sp-2) var(--sp-5);
    font-size: var(--fs-xs);
    color: var(--c-text-2);
    min-width: 0;

    em {
      font-style: normal;
      color: var(--c-text-3);
      margin-right: 4px;
    }

    b {
      font-weight: normal;
    }

    span {
      max-width: 240px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }

  &__ops {
    display: flex;
    align-items: center;
    gap: var(--sp-1);
    white-space: nowrap;
  }
}

.cus-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-10) 0;
  color: var(--c-text-3);

  p {
    font-size: var(--fs-sm);
  }
}

.cus-pager {
  display: flex;
  justify-content: flex-end;
  padding: var(--sp-3) var(--sp-5);
  border-top: 1px solid var(--c-border);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: var(--sp-5);

  &__full {
    grid-column: 1 / -1;
  }
}

@media (max-width: 1280px) {
  .cus-row {
    grid-template-columns: 1fr;
    gap: var(--sp-2);

    &__ops {
      justify-content: flex-start;
    }
  }
}

@media (max-width: 768px) {
  .cus-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
