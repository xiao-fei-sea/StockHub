<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { AppTable } from '@/components'
import type { TableColumn } from '@/components'
import { getInventoryFlows } from '@/api/inventory'
import type { InventoryFlow, InventoryFlowQuery } from '@/types/entities/inventory'
import { FLOW_DIRECTION, FLOW_TYPE } from '@/utils/dict'
import type { DictOption } from '@/utils/dict'
import { formatQty } from '@/utils/format'

/** 行内「查看流水」弹窗入参（来自库存行，服务端按 warehouseId + productId 过滤）。 */
export interface FlowPreset {
  warehouseId?: number
  warehouseName?: string
  productId?: number
  productCode?: string
  productName?: string
  batchNo?: string
}

defineOptions({ name: 'FlowDialog' })

const props = defineProps<{
  visible: boolean
  preset?: FlowPreset | null
}>()

const emit = defineEmits<{ 'update:visible': [value: boolean] }>()

const query = ref<InventoryFlowQuery>({ pageNum: 1, pageSize: 10 })
const dateRange = ref<string[]>([])
const list = ref<InventoryFlow[]>([])
const total = ref(0)
const loading = ref(false)

const preset = computed(() => props.preset ?? {})

const title = computed(() => {
  const name = preset.value.productName ?? '库存'
  const code = preset.value.productCode ? `（${preset.value.productCode}）` : ''
  const batch = preset.value.batchNo ? ` · 批次 ${preset.value.batchNo}` : ''
  return `${name}${code} 库存流水${batch}`
})

const typeOptions = FLOW_TYPE.map((o) => ({ label: o.label, value: String(o.value) }))
const directionOptions = FLOW_DIRECTION.map((o) => ({ label: o.label, value: String(o.value) }))

function buildParams(): InventoryFlowQuery {
  const p: Record<string, unknown> = {
    ...query.value,
    pageNum: query.value.pageNum ?? 1,
    pageSize: query.value.pageSize ?? 10,
    warehouseId: preset.value.warehouseId,
    productId: preset.value.productId,
  }
  for (const k of Object.keys(p)) {
    const v = p[k]
    if (v === null || v === undefined || v === '') delete p[k]
  }
  return p as InventoryFlowQuery
}

async function load() {
  loading.value = true
  try {
    const res = await getInventoryFlows(buildParams())
    list.value = res.list ?? []
    total.value = res.total
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (visible) => {
    if (!visible) return
    query.value = { pageNum: 1, pageSize: 10 }
    dateRange.value = []
    load()
  },
)

function onSearch() {
  query.value = {
    ...query.value,
    pageNum: 1,
    startDate: dateRange.value[0] || undefined,
    endDate: dateRange.value[1] || undefined,
  }
  load()
}

function onReset() {
  dateRange.value = []
  query.value = {
    pageNum: 1,
    pageSize: 10,
    flowType: undefined,
    direction: undefined,
    refNo: undefined,
    startDate: undefined,
    endDate: undefined,
  }
  load()
}

function onPageChange({ pageNum, pageSize }: { pageNum: number; pageSize: number }) {
  query.value = { ...query.value, pageNum, pageSize }
  load()
}

const columns: TableColumn[] = [
  { prop: 'flowNo', label: '流水号', width: 158 },
  { prop: 'operTime', label: '操作时间', width: 168 },
  { prop: 'flowType', label: '流水类型', width: 96 },
  { prop: 'qty', label: '变动数量', width: 110, align: 'right' },
  { prop: 'stock', label: '变动前 → 变动后', width: 152, align: 'right' },
  { prop: 'batchNo', label: '批次', width: 110, showOverflowTooltip: true },
  { prop: 'locationCode', label: '货位', width: 148, showOverflowTooltip: true },
  { prop: 'refNo', label: '关联单号', width: 150, showOverflowTooltip: true },
  { prop: 'operatorName', label: '操作人', minWidth: 90 },
]

function directionTagType(row: InventoryFlow): DictOption['type'] {
  return FLOW_DIRECTION.find((d) => d.value === row.direction)?.type ?? 'info'
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="1080px"
    top="6vh"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="flow-filters">
      <el-select
        v-model="query.flowType"
        placeholder="流水类型"
        clearable
        style="width: 140px"
        @change="onSearch"
      >
        <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-select
        v-model="query.direction"
        placeholder="变动方向"
        clearable
        style="width: 130px"
        @change="onSearch"
      >
        <el-option v-for="o in directionOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-input
        v-model="query.refNo"
        placeholder="关联单号（模糊）"
        clearable
        style="width: 180px"
        @keyup.enter="onSearch"
      />
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        style="width: 240px"
        @change="onSearch"
      />
      <el-button type="primary" :icon="Search" :loading="loading" @click="onSearch">查询</el-button>
      <el-button :icon="Refresh" @click="onReset">重置</el-button>
      <span v-if="preset.warehouseName" class="flow-filters__scope t-sm t-3">
        仓库：{{ preset.warehouseName }}
      </span>
    </div>

    <AppTable
      :columns="columns"
      :data="(list as unknown as Record<string, unknown>[])"
      :loading="loading"
      :pagination="{ total, pageNum: query.pageNum ?? 1, pageSize: query.pageSize ?? 10 }"
      size="small"
      empty-text="该商品暂无库存流水"
      @page-change="onPageChange"
    >
      <template #col-flowType="{ row }">
        <el-tag :type="directionTagType(row as InventoryFlow)" size="small" effect="light">
          {{ (row as InventoryFlow).flowType }}
        </el-tag>
      </template>
      <template #col-qty="{ row }">
        <span class="num" :class="(row as InventoryFlow).direction === 'IN' ? 't-success' : 't-danger'">
          {{ (row as InventoryFlow).direction === 'IN' ? '+' : '-'
          }}{{ formatQty((row as InventoryFlow).qty) }}
        </span>
      </template>
      <template #col-stock="{ row }">
        <span class="num t-3">{{ formatQty((row as InventoryFlow).beforeQty) }}</span>
        <span class="arrow">→</span>
        <span class="num">{{ formatQty((row as InventoryFlow).afterQty) }}</span>
      </template>
      <template #col-operatorName="{ row }">
        {{ (row as InventoryFlow).operatorName ?? '—' }}
      </template>
    </AppTable>

    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.flow-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-bottom: var(--sp-4);

  &__scope {
    margin-left: auto;
  }
}

.arrow {
  margin: 0 var(--sp-2);
  color: var(--c-text-3);
}
</style>
