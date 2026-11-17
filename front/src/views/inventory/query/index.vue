<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { AppTable, LocationSelect, PageHeader, ProductSelect, SearchForm, WarehouseSelect } from '@/components'
import type { SearchField, TableColumn } from '@/components'
import { getCategoryTree, getInventory, getInventorySummary } from '@/api/inventory'
import type { CategoryNode, InventoryItem, InventoryQuery, InventorySummary } from '@/types/entities/inventory'
import { pathToRouteName } from '@/store/permission'
import { formatNumber, formatQty, today } from '@/utils/format'
import FlowDialog from './FlowDialog.vue'
import type { FlowPreset } from './FlowDialog.vue'

defineOptions({ name: 'InventoryQuery' })

const route = useRoute()
const router = useRouter()

/* ---------------- 查询条件（支持看板/预警页下钻参数） ---------------- */
function numParam(v: unknown): number | undefined {
  if (typeof v === 'string' && v !== '' && !Number.isNaN(Number(v))) return Number(v)
  return undefined
}

const query = ref<InventoryQuery>({
  pageNum: 1,
  pageSize: 20,
  warehouseId: numParam(route.query.warehouseId),
  productId: numParam(route.query.productId),
  keyword: typeof route.query.keyword === 'string' && route.query.keyword ? route.query.keyword : undefined,
  // route.query.tab 在本页无对应 Tab（约定：无该 Tab 时忽略）
})

const fields: SearchField[] = [
  { key: 'warehouseId', label: '仓库', type: 'select' },
  { key: 'keyword', label: '关键字', type: 'input', placeholder: '商品编码 / 名称 / 货位编码' },
  { key: 'categoryId', label: '商品分类', type: 'select' },
  { key: 'productId', label: '商品', type: 'select' },
  { key: 'locationId', label: '货位', type: 'select' },
  { key: 'batchNo', label: '批次号', type: 'input', placeholder: '模糊匹配' },
  { key: 'onlyPositive', label: '库存范围', type: 'select' },
]

function setField(key: keyof InventoryQuery, value: unknown) {
  query.value = { ...query.value, [key]: value ?? undefined } as InventoryQuery
}

function onQueryPatch(v: Record<string, unknown>) {
  query.value = v as InventoryQuery
}

const categoryTree = ref<CategoryNode[]>([])

const hasQuery = computed(() => {
  const q = query.value
  return Boolean(
    q.warehouseId || q.productId || q.locationId || q.categoryId || q.keyword || q.batchNo || q.onlyPositive !== undefined,
  )
})

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const list = ref<InventoryItem[]>([])
const total = ref(0)

function buildParams(): InventoryQuery {
  const p: Record<string, unknown> = { ...query.value }
  for (const k of Object.keys(p)) {
    const v = p[k]
    if (v === null || v === undefined || v === '') delete p[k]
  }
  return p as InventoryQuery
}

async function loadList() {
  loading.value = true
  try {
    const res = await getInventory(buildParams())
    list.value = res.list ?? []
    total.value = res.total
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.value = { ...query.value, pageNum: 1 }
  loadList()
}

function onPageChange({ pageNum, pageSize }: { pageNum: number; pageSize: number }) {
  query.value = { ...query.value, pageNum, pageSize }
  loadList()
}

/* ---------------- 汇总指标条 ---------------- */
const summary = ref<InventorySummary | null>(null)
const summaryLoading = ref(true)

async function loadSummary() {
  summaryLoading.value = true
  try {
    summary.value = await getInventorySummary({ groupBy: 'warehouse' })
  } catch {
    summary.value = null
  } finally {
    summaryLoading.value = false
  }
}

const metrics = computed(() => [
  { label: '当前总库存', value: formatQty(summary.value?.totalQty), note: '数据权限内仓库合计' },
  { label: '在库商品 SKU', value: formatNumber(summary.value?.totalSku), note: '去重商品数' },
  {
    label: '覆盖仓库',
    value: String(summary.value?.items?.length ?? 0),
    note: summary.value?.items?.[0]?.warehouseName ? `如 ${summary.value.items[0].warehouseName}` : '按数据权限统计',
  },
  {
    label: '商品分类数',
    value: String(summary.value?.byCategory?.length ?? 0),
    note: '按一级分类聚合',
  },
])

/* ---------------- 流水弹窗 ---------------- */
const flowVisible = ref(false)
const flowPreset = ref<FlowPreset | null>(null)

function openFlow(row: InventoryItem) {
  flowPreset.value = {
    warehouseId: row.warehouseId,
    warehouseName: row.warehouseName,
    productId: row.productId,
    productCode: row.productCode,
    productName: row.productName,
    batchNo: row.batchNo,
  }
  flowVisible.value = true
}

/* ---------------- 下钻跳转 ---------------- */
function go(path: string, queryArgs?: Record<string, string>) {
  if (!router.hasRoute(pathToRouteName(path))) {
    ElMessage.warning('无权访问该页面')
    return
  }
  router.push({ path, query: queryArgs })
}

function goAlert(row?: InventoryItem) {
  const q: Record<string, string> = { tab: 'record' }
  if (row) {
    q.productId = String(row.productId)
    q.warehouseId = String(row.warehouseId)
  }
  go('/inventory/alert', q)
}

/* ---------------- 表格 ---------------- */
const isExpired = (date?: string | null): boolean => Boolean(date && date < today())

const columns: TableColumn[] = [
  { prop: 'warehouseName', label: '仓库', width: 118, showOverflowTooltip: true },
  { prop: 'locationCode', label: '货位', width: 156, showOverflowTooltip: true },
  { prop: 'productCode', label: '商品编码', width: 118, showOverflowTooltip: true },
  { prop: 'productName', label: '商品名称', minWidth: 190, showOverflowTooltip: true },
  { prop: 'batchNo', label: '批次号', width: 116, showOverflowTooltip: true },
  { prop: 'productionDate', label: '生产日期', width: 112 },
  { prop: 'expiryDate', label: '到期日期', width: 112 },
  { prop: 'qty', label: '当前库存', width: 104, align: 'right' },
  { prop: 'lockQty', label: '锁定量', width: 92, align: 'right' },
  { prop: 'availableQty', label: '可用量', width: 104, align: 'right' },
  { prop: 'unit', label: '单位', width: 72, align: 'center' },
  { prop: 'updateTime', label: '更新时间', width: 164 },
  { prop: 'actions', label: '操作', width: 188, fixed: 'right', align: 'center' },
]

function refreshAll() {
  loadSummary()
  loadList()
}

onMounted(async () => {
  await Promise.all([loadSummary(), loadList()])
  try {
    categoryTree.value = (await getCategoryTree()) ?? []
  } catch {
    categoryTree.value = []
  }
})
</script>

<template>
  <div class="page">
    <PageHeader title="库存查询" desc="多条件查询当前库存，支持汇总看板与库存变动流水追溯">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading || summaryLoading" @click="refreshAll">
          刷新
        </el-button>
        <el-button type="primary" plain @click="goAlert()">预警记录</el-button>
      </template>
    </PageHeader>

    <!-- 汇总指标条（GET /inventory/summary） -->
    <section class="metrics">
      <div v-loading="summaryLoading" v-for="m in metrics" :key="m.label" class="metric panel">
        <span class="metric__label">{{ m.label }}</span>
        <span class="metric__value num">{{ m.value }}</span>
        <span class="metric__note t-xs t-3">{{ m.note }}</span>
      </div>
    </section>

    <!-- 多条件筛选 -->
    <SearchForm
      :fields="fields"
      :model-value="query"
      :loading="loading"
      @update:model-value="onQueryPatch"
      @search="onSearch"
    >
      <template #field-warehouseId>
        <WarehouseSelect
          :model-value="query.warehouseId ?? null"
          style="width: 180px"
          @update:model-value="setField('warehouseId', $event)"
        />
      </template>
      <template #field-categoryId>
        <el-tree-select
          :model-value="query.categoryId ?? undefined"
          :data="categoryTree"
          :props="{ label: 'categoryName', children: 'children' }"
          node-key="id"
          placeholder="全部分类"
          clearable
          filterable
          check-strictly
          style="width: 180px"
          @update:model-value="setField('categoryId', $event)"
        />
      </template>
      <template #field-productId>
        <ProductSelect
          :model-value="query.productId ?? null"
          style="width: 240px"
          @update:model-value="setField('productId', $event)"
        />
      </template>
      <template #field-locationId>
        <LocationSelect
          :model-value="query.locationId ?? null"
          :warehouse-id="query.warehouseId ?? null"
          style="width: 200px"
          @update:model-value="setField('locationId', $event)"
        />
      </template>
      <template #field-onlyPositive>
        <el-select
          :model-value="query.onlyPositive ?? undefined"
          placeholder="全部记录"
          clearable
          style="width: 160px"
          @update:model-value="setField('onlyPositive', $event)"
        >
          <el-option label="仅显示有库存" :value="true" />
          <el-option label="显示全部记录" :value="false" />
        </el-select>
      </template>
    </SearchForm>

    <!-- 库存明细表 -->
    <AppTable
      :columns="columns"
      :data="(list as unknown as Record<string, unknown>[])"
      :loading="loading"
      :serial="true"
      stripe
      :pagination="{ total, pageNum: query.pageNum ?? 1, pageSize: query.pageSize ?? 20 }"
      :empty-text="hasQuery ? '未查询到符合条件的数据' : '暂无库存数据'"
      @page-change="onPageChange"
    >
      <template #col-expiryDate="{ row }">
        <span :class="isExpired((row as InventoryItem).expiryDate) ? 't-danger' : ''">
          {{ (row as InventoryItem).expiryDate ?? '—' }}
        </span>
      </template>
      <template #col-qty="{ row }">
        <span class="num">{{ formatQty((row as InventoryItem).qty) }}</span>
      </template>
      <template #col-lockQty="{ row }">
        <span class="num t-3">{{ formatQty((row as InventoryItem).lockQty) }}</span>
      </template>
      <template #col-availableQty="{ row }">
        <span class="num">{{ formatQty((row as InventoryItem).availableQty) }}</span>
      </template>
      <template #col-unit="{ row }">
        {{ (row as InventoryItem).unit ?? '—' }}
      </template>
      <template #col-actions="{ row }">
        <el-button
          link
          type="primary"
          v-permission="'sys:inventory:viewFlow'"
          @click.stop="openFlow(row as InventoryItem)"
        >
          查看流水
        </el-button>
        <el-button link type="primary" @click.stop="goAlert(row as InventoryItem)">库存预警</el-button>
      </template>
    </AppTable>

    <FlowDialog v-model:visible="flowVisible" :preset="flowPreset" />
  </div>
</template>

<style scoped lang="scss">
.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--sp-4);

  @media (max-width: 1280px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @media (max-width: 768px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.metric {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  padding: var(--sp-4) var(--sp-5);

  &__label {
    font-size: var(--fs-sm);
    color: var(--c-text-2);
  }

  &__value {
    font-size: var(--fs-3xl);
    line-height: var(--lh-tight);
    font-weight: var(--fw-semibold);
    color: var(--c-text);
    letter-spacing: -0.02em;
  }

  &__note {
    color: var(--c-text-3);
  }
}
</style>
