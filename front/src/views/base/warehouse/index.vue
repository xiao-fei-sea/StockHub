<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { OrderDetailDrawer, PageHeader, SearchForm, StatusTag, confirmDialog } from '@/components'
import type { SearchField } from '@/components'
import type { DetailField } from '@/components'
import {
  createWarehouse,
  deleteWarehouse,
  getWarehouseDetail,
  getWarehouses,
  updateWarehouse,
} from '@/api/warehouse'
import type { Warehouse, WarehouseForm } from '@/types/entity'
import { enableStatusInfo } from '@/utils/dict'
import { enableTagStatus } from '../components/status'
import { usePermission } from '@/composables/usePermission'

defineOptions({ name: 'BaseWarehouse' })

const { hasPerm } = usePermission()

/* ---------------- 状态分段 + 查询 ---------------- */
type SegValue = number | undefined
const segments: { label: string; value: SegValue }[] = [
  { label: '全部仓库', value: undefined },
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]
const statusSeg = ref<SegValue>(undefined)

const searchFields: SearchField[] = [
  { key: 'keyword', type: 'input', placeholder: '仓库编码 / 名称 / 地址' },
]
const query = reactive<{ keyword?: string }>({})

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const list = ref<Warehouse[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function loadList() {
  loading.value = true
  try {
    const res = await getWarehouses({
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

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const submitting = ref(false)

const emptyForm = (): WarehouseForm => ({
  warehouseCode: '',
  warehouseName: '',
  address: '',
  contactName: '',
  contactPhone: '',
  areaSize: null,
  status: 1,
  sort: 0,
  remark: '',
})
const form = reactive<WarehouseForm>(emptyForm())

const rules: FormRules<WarehouseForm> = {
  warehouseCode: [
    { required: true, message: '请输入仓库编码', trigger: 'blur' },
    { pattern: /^WH-[A-Za-z0-9-]+$/, message: '编码需以 WH- 开头', trigger: 'blur' },
  ],
  warehouseName: [
    { required: true, message: '请输入仓库名称', trigger: 'blur' },
    { min: 2, max: 50, message: '仓库名称长度 2-50 字', trigger: 'blur' },
  ],
  address: [{ max: 100, message: '地址不能超过 100 字', trigger: 'blur' }],
  contactName: [{ max: 20, message: '联系人不能超过 20 字', trigger: 'blur' }],
  contactPhone: [
    { pattern: /^(1[3-9]\d{9}|(\d{3,4}-)?\d{7,8})$/, message: '请输入正确的联系电话', trigger: 'blur' },
  ],
  areaSize: [{ type: 'number', min: 0, message: '面积需为不小于 0 的数值，最多 2 位小数', trigger: 'blur' }],
  sort: [{ type: 'integer', min: 0, message: '排序号需为不小于 0 的整数', trigger: 'change' }],
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

function openEdit(row: Warehouse) {
  isEdit.value = true
  editId.value = row.id
  resetForm()
  Object.assign(form, {
    warehouseCode: row.warehouseCode,
    warehouseName: row.warehouseName,
    address: row.address ?? '',
    contactName: row.contactName ?? '',
    contactPhone: row.contactPhone ?? '',
    areaSize: row.areaSize ?? null,
    status: row.status,
    sort: row.sort ?? 0,
    remark: row.remark ?? '',
  })
  dialogVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload: WarehouseForm = { ...form, areaSize: form.areaSize ?? undefined }
    if (isEdit.value && editId.value !== null) {
      await updateWarehouse(editId.value, payload)
    } else {
      await createWarehouse(payload)
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
async function onDelete(row: Warehouse) {
  const ok = await confirmDialog({
    title: '删除仓库',
    message: `确定删除仓库「${row.warehouseName}（${row.warehouseCode}）」吗？\n若仓库已被库区、库存或单据引用，将无法删除。`,
    confirmText: '删除',
    type: 'danger',
  })
  if (!ok) return
  try {
    await deleteWarehouse(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    /* 引用拦截等业务错误由 request 统一提示 */
  }
}

/* ---------------- 详情 ---------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<Warehouse | null>(null)

async function openDetail(row: Warehouse) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getWarehouseDetail(row.id)
  } catch {
    detail.value = row
  } finally {
    detailLoading.value = false
  }
}

const detailFields = computed<DetailField[]>(() => {
  const w = detail.value
  if (!w) return []
  return [
    { label: '仓库编码', value: w.warehouseCode },
    { label: '仓库名称', value: w.warehouseName },
    { label: '地址', value: w.address ?? '—' },
    { label: '联系人', value: w.contactName ?? '—' },
    { label: '联系电话', value: w.contactPhone ?? '—' },
    { label: '面积（㎡）', value: w.areaSize ?? '—' },
    { label: '排序号', value: w.sort ?? 0 },
    { label: '备注', value: w.remark ?? '—' },
    { label: '创建人', value: w.createBy ?? '—' },
    { label: '创建时间', value: w.createTime ?? '—' },
    { label: '更新人', value: w.updateBy ?? '—' },
    { label: '更新时间', value: w.updateTime ?? '—' },
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
    <PageHeader title="仓库管理" desc="维护仓库基础档案：编码、地址、联系人、面积与启用状态">
      <template #actions>
        <el-button v-permission="'sys:warehouse:add'" type="primary" :icon="Plus" @click="openCreate">
          新增仓库
        </el-button>
      </template>
    </PageHeader>

    <!-- 状态分段 + 关键字查询 -->
    <section class="panel wh-controls">
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
        class="wh-controls__search"
        :model-value="query"
        :fields="searchFields"
        :loading="loading"
        :collapsible="false"
        @update:model-value="onQueryChange"
        @search="onSearch"
        @reset="onReset"
      />
    </section>

    <!-- 仓库卡片栅格 -->
    <div v-loading="loading" class="wh-grid-wrap">
      <div v-if="list.length" class="wh-grid">
        <article v-for="w in list" :key="w.id" class="wh-card">
          <div class="wh-card__head">
            <div class="wh-card__id">
              <span class="wh-card__code mono">{{ w.warehouseCode }}</span>
              <h3 class="wh-card__name" :title="w.warehouseName">{{ w.warehouseName }}</h3>
            </div>
            <StatusTag :status="enableTagStatus(w.status)" :label="enableStatusInfo(w.status).label" />
          </div>

          <p class="wh-card__addr t-2 t-sm" :title="w.address ?? ''">
            {{ w.address || '未填写地址' }}
          </p>

          <dl class="wh-card__meta">
            <div>
              <dt>联系人</dt>
              <dd>{{ w.contactName || '—' }}</dd>
            </div>
            <div>
              <dt>联系电话</dt>
              <dd class="num">{{ w.contactPhone || '—' }}</dd>
            </div>
            <div>
              <dt>面积</dt>
              <dd class="num">{{ w.areaSize != null ? `${w.areaSize} ㎡` : '—' }}</dd>
            </div>
            <div>
              <dt>排序</dt>
              <dd class="num">{{ w.sort ?? 0 }}</dd>
            </div>
          </dl>

          <div class="wh-card__foot">
            <el-button link type="primary" @click="openDetail(w)">详情</el-button>
            <el-button v-permission="'sys:warehouse:edit'" link type="primary" @click="openEdit(w)">
              编辑
            </el-button>
            <el-button v-permission="'sys:warehouse:delete'" link type="danger" @click="onDelete(w)">
              删除
            </el-button>
          </div>
        </article>
      </div>

      <div v-else-if="!loading" class="wh-empty panel">
        <p class="t-2">暂无符合条件的仓库</p>
        <p class="t-3 t-sm">调整筛选条件，或新增一个仓库开始使用</p>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > 0" class="panel pager">
      <span class="t-sm t-3">共 {{ total }} 条</span>
      <el-pagination
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="sizes, prev, pager, next, jumper"
        @current-change="(p: number) => onPageChange({ pageNum: p, pageSize })"
        @size-change="(s: number) => onPageChange({ pageNum: 1, pageSize: s })"
      />
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑仓库' : '新增仓库'"
      width="680px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" @submit.prevent>
        <div class="form-grid">
          <el-form-item label="仓库编码" prop="warehouseCode">
            <el-input v-model="form.warehouseCode" placeholder="如 WH-001" maxlength="20" />
          </el-form-item>
          <el-form-item label="仓库名称" prop="warehouseName">
            <el-input v-model="form.warehouseName" placeholder="2-50 字符" maxlength="50" />
          </el-form-item>
          <el-form-item label="联系人" prop="contactName">
            <el-input v-model="form.contactName" placeholder="选填" maxlength="20" />
          </el-form-item>
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="form.contactPhone" placeholder="手机号或座机" maxlength="20" />
          </el-form-item>
          <el-form-item label="面积（㎡）" prop="areaSize">
            <el-input-number
              v-model="form.areaSize"
              :min="0"
              :precision="2"
              :controls="false"
              placeholder="选填"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="排序号" prop="sort">
            <el-input-number v-model="form.sort" :min="0" :precision="0" style="width: 100%" />
          </el-form-item>
          <el-form-item label="状态" prop="status" class="form-grid__full">
            <el-radio-group v-model="form.status">
              <el-radio :value="1">启用</el-radio>
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
    <OrderDetailDrawer
      v-model:visible="detailVisible"
      title="仓库详情"
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
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="hasPerm('sys:warehouse:edit')"
          type="primary"
          :loading="detailLoading"
          @click="onDetailEdit"
        >
          编辑该仓库
        </el-button>
      </template>
    </OrderDetailDrawer>
  </div>
</template>

<style scoped lang="scss">
.wh-controls {
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

/* 状态分段控件（纯色，无渐变） */
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

.wh-grid-wrap {
  min-height: 220px;
}

.wh-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--sp-4);
}

.wh-card {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding: var(--sp-4);
  background-color: var(--c-bg-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
  transition:
    border-color var(--dur) var(--ease-out),
    box-shadow var(--dur) var(--ease-out);

  &:hover {
    border-color: var(--c-primary-soft-border);
    box-shadow: var(--shadow-sm);
  }

  &__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--sp-3);
  }

  &__id {
    min-width: 0;
  }

  &__code {
    display: block;
    font-size: var(--fs-xs);
    color: var(--c-text-3);
    letter-spacing: 0.04em;
  }

  &__name {
    margin-top: 2px;
    font-size: var(--fs-md);
    font-weight: var(--fw-semibold);
    color: var(--c-text);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__addr {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    min-height: 2.6em;
    margin: 0;
  }

  &__meta {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--sp-2) var(--sp-4);
    margin: 0;
    padding-top: var(--sp-3);
    border-top: 1px dashed var(--c-border);

    dt {
      font-size: var(--fs-xs);
      color: var(--c-text-3);
    }

    dd {
      margin: 0;
      font-size: var(--fs-sm);
      color: var(--c-text);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: var(--sp-1);
    margin-top: auto;
    padding-top: var(--sp-2);
    border-top: 1px solid var(--c-border);
  }
}

.wh-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-1);
  padding: var(--sp-12) var(--sp-4);
  text-align: center;

  p {
    margin: 0;
  }
}

.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--sp-4);
  padding: var(--sp-3) var(--sp-4);
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
  .wh-controls {
    flex-direction: column;
    align-items: stretch;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
