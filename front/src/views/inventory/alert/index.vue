<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search, Lightning } from '@element-plus/icons-vue'
import { AppTable, PageHeader, WarehouseSelect, confirmDialog } from '@/components'
import type { TableColumn } from '@/components'
import {
  createAlertRule,
  deleteAlertRule,
  getAlertRule,
  getAlertRules,
  getAlerts,
  handleAlert,
  scanAlerts,
  updateAlertRule,
} from '@/api/alert'
import type { AlertRecord } from '@/api/alert'
import type { AlertRule, AlertRuleForm, AlertScanResult } from '@/types/entities/inventory'
import type { AlertRuleQuery } from '@/api/alert'
import { ALERT_LEVEL, alertTypeInfo, type TagType } from '@/utils/dict'
import { formatQty } from '@/utils/format'
import { pathToRouteName } from '@/store/permission'
import { usePermission } from '@/composables/usePermission'
import {
  ALERT_HANDLE_STATUS_OPTIONS,
  ALERT_TYPE_OPTIONS,
  alertConditionText,
  alertTypeLabel,
} from './constants'
import RecordHandleDialog from './RecordHandleDialog.vue'
import RuleFormDialog from './RuleFormDialog.vue'

defineOptions({ name: 'InventoryAlert' })

const route = useRoute()
const router = useRouter()
const { hasPerm } = usePermission()

/* ---------------- 双 Tab（支持 ?tab=record 定位） ---------------- */
type TabKey = 'record' | 'rule'
const activeTab = ref<TabKey>('record')

function switchTab(tab: TabKey) {
  if (activeTab.value === tab) return
  activeTab.value = tab
}

function parseNum(v: unknown): number | null {
  if (v === undefined || v === null || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

function routeKey(): string {
  return [route.query.tab ?? '', route.query.productId ?? '', route.query.warehouseId ?? ''].join('|')
}

function applyRouteQuery() {
  const tab = String(route.query.tab ?? '')
  if (tab === 'rule' || tab === 'rules') activeTab.value = 'rule'
  else if (tab === 'record' || tab === 'records') activeTab.value = 'record'
  recordFilters.productId = parseNum(route.query.productId)
  recordFilters.warehouseId = parseNum(route.query.warehouseId)
}

/* ---------------- Tab1：预警记录 ---------------- */
const recordFilters = reactive({
  alertType: undefined as string | undefined,
  status: undefined as string | undefined,
  level: undefined as string | undefined,
  warehouseId: null as number | null,
  productId: null as number | null,
})
const recordDateRange = ref<string[]>([])
const recordPageNum = ref(1)
const recordPageSize = ref(20)
const recordLoading = ref(false)
const recordList = ref<AlertRecord[]>([])
const recordTotal = ref(0)

function buildRecordParams(): NonNullable<Parameters<typeof getAlerts>[0]> {
  const p: Record<string, unknown> = {
    pageNum: recordPageNum.value,
    pageSize: recordPageSize.value,
    alertType: recordFilters.alertType,
    status: recordFilters.status,
    level: recordFilters.level,
    warehouseId: recordFilters.warehouseId ?? undefined,
    productId: recordFilters.productId ?? undefined,
    startDate: recordDateRange.value[0] || undefined,
    endDate: recordDateRange.value[1] || undefined,
  }
  for (const k of Object.keys(p)) {
    const v = p[k]
    if (v === null || v === undefined || v === '') delete p[k]
  }
  return p as NonNullable<Parameters<typeof getAlerts>[0]>
}

async function loadRecords() {
  recordLoading.value = true
  try {
    const res = await getAlerts(buildRecordParams())
    recordList.value = res.list ?? []
    recordTotal.value = res.total ?? 0
    if (!recordList.value.length && recordPageNum.value > 1) {
      recordPageNum.value -= 1
      await loadRecords()
    }
  } catch {
    recordList.value = []
    recordTotal.value = 0
  } finally {
    recordLoading.value = false
  }
}

function onRecordSearch() {
  recordPageNum.value = 1
  loadRecords()
}

function onRecordReset() {
  recordFilters.alertType = undefined
  recordFilters.status = undefined
  recordFilters.level = undefined
  recordFilters.warehouseId = null
  recordFilters.productId = null
  recordDateRange.value = []
  onRecordSearch()
}

function onRecordPageChange({ pageNum, pageSize }: { pageNum: number; pageSize: number }) {
  recordPageNum.value = pageNum
  recordPageSize.value = pageSize
  loadRecords()
}

function clearRouteFilter(key: 'productId' | 'warehouseId') {
  recordFilters[key] = null
  recordPageNum.value = 1
  void loadRecords()
}

/* 类型/级别展示 */
function typeTag(alertType: string, desc?: string | null): { label: string; type: TagType } {
  const info = alertTypeInfo(alertType)
  return { label: desc || alertTypeLabel(alertType), type: info.type ?? 'info' }
}

function levelType(level?: string): 'info' | 'warning' | 'danger' {
  const hit = ALERT_LEVEL.find((l) => l.value === level)
  return hit?.type === 'warning' || hit?.type === 'danger' ? hit.type : 'info'
}

/* 手动扫描（POST /alerts/scan） */
const scanLoading = ref(false)

async function onScan() {
  if (scanLoading.value) return
  scanLoading.value = true
  try {
    const res: AlertScanResult = await scanAlerts()
    ElMessage.success(
      `扫描完成：启用规则 ${res?.ruleCount ?? 0} 条，新增预警 ${res?.newRecordCount ?? 0} 条`,
    )
    await loadRecords()
  } catch {
    // 错误由 request 层统一提示
  } finally {
    scanLoading.value = false
  }
}

/* 处理预警（PUT /alerts/{id}/handle） */
const handleVisible = ref(false)
const handleRecord = ref<AlertRecord | null>(null)
const handlePending = ref(false)

function openHandle(row: AlertRecord) {
  handleRecord.value = row
  handleVisible.value = true
}

async function onHandleConfirm(remark: string) {
  if (!handleRecord.value || handlePending.value) return
  handlePending.value = true
  try {
    await handleAlert(handleRecord.value.id, { handleRemark: remark })
    ElMessage.success('已标记为已处理')
    handleVisible.value = false
    handleRecord.value = null
    await loadRecords()
  } catch {
    // 错误由 request 层统一提示，弹窗保留
  } finally {
    handlePending.value = false
  }
}

/* 下钻：定位库存 */
function go(path: string, queryArgs?: Record<string, string>) {
  if (!router.hasRoute(pathToRouteName(path))) {
    ElMessage.warning('无权访问该页面')
    return
  }
  void router.push({ path, query: queryArgs })
}

function goQuery(row: AlertRecord) {
  const q: Record<string, string> = {}
  if (row.productId) q.productId = String(row.productId)
  if (row.warehouseId) q.warehouseId = String(row.warehouseId)
  go('/inventory/query', q)
}

/* ---------------- Tab2：预警规则 ---------------- */
const ruleFilters = reactive({
  keyword: '',
  warehouseId: null as number | null,
  alertType: undefined as string | undefined,
  status: undefined as number | undefined,
})
const rulePageNum = ref(1)
const rulePageSize = ref(20)
const ruleLoading = ref(false)
const ruleList = ref<AlertRule[]>([])
const ruleTotal = ref(0)

function buildRuleParams(): AlertRuleQuery {
  const p: Record<string, unknown> = {
    pageNum: rulePageNum.value,
    pageSize: rulePageSize.value,
    keyword: ruleFilters.keyword.trim() || undefined,
    warehouseId: ruleFilters.warehouseId ?? undefined,
    alertType: ruleFilters.alertType,
    status: ruleFilters.status,
  }
  for (const k of Object.keys(p)) {
    const v = p[k]
    if (v === null || v === undefined || v === '') delete p[k]
  }
  return p as AlertRuleQuery
}

async function loadRules() {
  ruleLoading.value = true
  try {
    const res = await getAlertRules(buildRuleParams())
    ruleList.value = res.list ?? []
    ruleTotal.value = res.total ?? 0
    if (!ruleList.value.length && rulePageNum.value > 1) {
      rulePageNum.value -= 1
      await loadRules()
    }
  } catch {
    ruleList.value = []
    ruleTotal.value = 0
  } finally {
    ruleLoading.value = false
  }
}

function onRuleSearch() {
  rulePageNum.value = 1
  loadRules()
}

function onRuleReset() {
  ruleFilters.keyword = ''
  ruleFilters.warehouseId = null
  ruleFilters.alertType = undefined
  ruleFilters.status = undefined
  onRuleSearch()
}

function onRulePageChange({ pageNum, pageSize }: { pageNum: number; pageSize: number }) {
  rulePageNum.value = pageNum
  rulePageSize.value = pageSize
  loadRules()
}

function ruleScope(row: AlertRule): string {
  const parts = [
    row.warehouseName ?? (row.warehouseId != null ? `仓库 #${row.warehouseId}` : '全部仓库'),
    row.categoryName ?? (row.categoryId != null ? `分类 #${row.categoryId}` : '全部分类'),
    row.productName ?? (row.productId != null ? `商品 #${row.productId}` : '全部商品'),
  ]
  return parts.join(' · ')
}

/* 规则新建/编辑 */
const ruleDialogVisible = ref(false)
const editingRule = ref<AlertRule | null>(null)

function openCreateRule() {
  editingRule.value = null
  ruleDialogVisible.value = true
}

async function openEditRule(row: AlertRule) {
  // 以详情接口为准回填（API-ALR-005），失败时退回列表行数据
  let detail: AlertRule = row
  try {
    detail = await getAlertRule(row.id)
  } catch {
    detail = row
  }
  editingRule.value = detail
  ruleDialogVisible.value = true
}

async function onSaveRule(payload: AlertRuleForm) {
  try {
    if (editingRule.value) {
      await updateAlertRule(editingRule.value.id, payload)
      ElMessage.success('规则已更新')
    } else {
      await createAlertRule(payload)
      ElMessage.success('规则创建成功')
    }
    ruleDialogVisible.value = false
    await loadRules()
  } catch {
    // 错误由 request 层统一提示，弹窗保留供重试
  }
}

async function onDeleteRule(row: AlertRule) {
  const ok = await confirmDialog({
    title: '删除预警规则',
    message: `确定删除规则「${row.ruleName}」吗？已生成的预警记录保留，删除后不再触发新预警。`,
    confirmText: '删除',
    type: 'danger',
  })
  if (!ok) return
  try {
    await deleteAlertRule(row.id)
    ElMessage.success('删除成功')
    await loadRules()
  } catch {
    // 错误由 request 层统一提示
  }
}

/* 启停用（状态随开关翻转，PUT 全量提交） */
const togglingId = ref<number | null>(null)

async function toggleRule(row: AlertRule) {
  if (togglingId.value) return
  togglingId.value = row.id
  try {
    const next = row.status === 1 ? 0 : 1
    await updateAlertRule(row.id, {
      ruleName: row.ruleName,
      warehouseId: row.warehouseId,
      categoryId: row.categoryId,
      productId: row.productId,
      alertType: row.alertType,
      minQty: row.alertType === 'LOW_STOCK' ? row.minQty : undefined,
      maxQty: row.alertType === 'HIGH_STOCK' ? row.maxQty : undefined,
      nearDays: row.alertType === 'NEAR_EXPIRY' ? row.nearDays : undefined,
      status: next,
      remark: row.remark || undefined,
    })
    ElMessage.success(next === 1 ? '规则已启用' : '规则已停用')
    await loadRules()
  } catch {
    // 失败时开关随列表刷新回原状态
    await loadRules()
  } finally {
    togglingId.value = null
  }
}

/* ---------------- 表格列 ---------------- */
const recordColumns: TableColumn[] = [
  { prop: 'alertType', label: '预警类型', width: 116, align: 'center' },
  { prop: 'level', label: '级别', width: 76, align: 'center' },
  { prop: 'status', label: '处理状态', width: 92, align: 'center' },
  { prop: 'warehouseName', label: '仓库', width: 116, showOverflowTooltip: true },
  { prop: 'productCode', label: '商品编码', width: 108 },
  { prop: 'productName', label: '商品名称', minWidth: 164, showOverflowTooltip: true },
  { prop: 'currentValue', label: '当前值', width: 96, align: 'right' },
  { prop: 'thresholdValue', label: '阈值', width: 96, align: 'right' },
  { prop: 'ruleName', label: '来源规则', width: 140, showOverflowTooltip: true },
  { prop: 'handleRemark', label: '处理情况', minWidth: 176 },
  { prop: 'createTime', label: '生成时间', width: 164 },
  { prop: 'actions', label: '操作', width: 178, fixed: 'right', align: 'center' },
]

const ruleColumns: TableColumn[] = [
  { prop: 'ruleName', label: '规则名称', minWidth: 172, showOverflowTooltip: true },
  { prop: 'alertType', label: '预警类型', width: 112, align: 'center' },
  { prop: 'scope', label: '适用范围', minWidth: 216 },
  { prop: 'condition', label: '阈值条件', width: 148 },
  { prop: 'status', label: '启停用', width: 84, align: 'center' },
  { prop: 'remark', label: '备注', minWidth: 130, showOverflowTooltip: true },
  { prop: 'createTime', label: '创建时间', width: 164 },
  { prop: 'actions', label: '操作', width: 132, fixed: 'right', align: 'center' },
]

/* ---------------- 初始化（route.query tab / 商品 / 仓库下钻） ---------------- */
let lastRouteKey = ''

onMounted(() => {
  lastRouteKey = routeKey()
  applyRouteQuery()
  void loadRecords()
  void loadRules()
})

watch(route, () => {
  const key = routeKey()
  if (key === lastRouteKey) return
  lastRouteKey = key
  applyRouteQuery()
  recordPageNum.value = 1
  void loadRecords()
})
</script>

<template>
  <div class="page">
    <PageHeader title="库存预警" desc="低/高库存、临期与过期预警：记录处理与规则配置统一管理">
      <template #actions>
        <el-button
          v-if="hasPerm('sys:alert:scan')"
          :icon="Lightning"
          :loading="scanLoading"
          @click="onScan"
        >
          手动扫描
        </el-button>
        <el-button
          :icon="Refresh"
          :loading="recordLoading || ruleLoading"
          @click="activeTab === 'record' ? loadRecords() : loadRules()"
        >
          刷新
        </el-button>
      </template>
    </PageHeader>

    <!-- 双 Tab（记录 / 规则） -->
    <section class="panel al-panel">
      <div class="atabs" role="tablist">
        <button
          type="button"
          class="atabs__item"
          :class="{ 'is-active': activeTab === 'record' }"
          role="tab"
          :aria-selected="activeTab === 'record'"
          @click="switchTab('record')"
        >
          预警记录
          <span v-if="recordTotal" class="atabs__count">{{ recordTotal > 999 ? '999+' : recordTotal }}</span>
        </button>
        <button
          type="button"
          class="atabs__item"
          :class="{ 'is-active': activeTab === 'rule' }"
          role="tab"
          :aria-selected="activeTab === 'rule'"
          @click="switchTab('rule')"
        >
          预警规则
        </button>
      </div>

      <!-- ============ Tab1：预警记录 ============ -->
      <div v-show="activeTab === 'record'" class="pane" role="tabpanel">
        <div class="af-filters">
          <div class="fitem">
            <span class="fitem__label">预警类型</span>
            <el-select v-model="recordFilters.alertType" placeholder="全部" clearable style="width: 132px">
              <el-option
                v-for="o in ALERT_TYPE_OPTIONS"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>
          </div>
          <div class="fitem">
            <span class="fitem__label">处理状态</span>
            <el-select v-model="recordFilters.status" placeholder="全部" clearable style="width: 108px">
              <el-option
                v-for="o in ALERT_HANDLE_STATUS_OPTIONS"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>
          </div>
          <div class="fitem">
            <span class="fitem__label">级别</span>
            <el-select v-model="recordFilters.level" placeholder="全部" clearable style="width: 100px">
              <el-option v-for="l in ALERT_LEVEL" :key="String(l.value)" :label="l.label" :value="String(l.value)" />
            </el-select>
          </div>
          <div class="fitem">
            <span class="fitem__label">仓库</span>
            <WarehouseSelect v-model="recordFilters.warehouseId" placeholder="全部" style="width: 156px" />
          </div>
          <div class="fitem">
            <span class="fitem__label">生成日期</span>
            <el-date-picker
              v-model="recordDateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始"
              end-placeholder="结束"
              value-format="YYYY-MM-DD"
              style="width: 236px"
            />
          </div>
          <div class="af-filters__actions">
            <el-button type="primary" :icon="Search" :loading="recordLoading" @click="onRecordSearch">
              查询
            </el-button>
            <el-button @click="onRecordReset">重置</el-button>
          </div>
        </div>

        <div v-if="recordFilters.productId || recordFilters.warehouseId" class="chips">
          <el-tag
            v-if="recordFilters.productId"
            closable
            size="small"
            @close="clearRouteFilter('productId')"
          >
            商品 ID {{ recordFilters.productId }}
          </el-tag>
          <el-tag
            v-if="recordFilters.warehouseId"
            closable
            size="small"
            @close="clearRouteFilter('warehouseId')"
          >
            仓库 {{ recordFilters.warehouseId }}
          </el-tag>
        </div>

        <AppTable
          :columns="recordColumns"
          :data="(recordList as unknown as Record<string, unknown>[])"
          :loading="recordLoading"
          :serial="true"
          stripe
          :pagination="{ total: recordTotal, pageNum: recordPageNum, pageSize: recordPageSize }"
          :empty-text="recordFilters.alertType || recordFilters.status || recordFilters.level || recordFilters.warehouseId || recordFilters.productId || recordDateRange.length ? '未查询到符合条件的预警记录' : '暂无预警记录，可点击右上角「手动扫描」立即检查'"
          @page-change="onRecordPageChange"
        >
          <template #col-alertType="{ row }">
            <el-tag
              size="small"
              effect="light"
              :type="typeTag((row as AlertRecord).alertType, (row as AlertRecord).alertTypeDesc).type"
            >
              {{ typeTag((row as AlertRecord).alertType, (row as AlertRecord).alertTypeDesc).label }}
            </el-tag>
          </template>
          <template #col-level="{ row }">
            <el-tag size="small" effect="light" :type="levelType((row as AlertRecord).level)">
              {{ (row as AlertRecord).level }}
            </el-tag>
          </template>
          <template #col-status="{ row }">
            <el-tag
              size="small"
              effect="light"
              :type="(row as AlertRecord).status === '已处理' ? 'success' : 'warning'"
            >
              {{ (row as AlertRecord).status }}
            </el-tag>
          </template>
          <template #col-currentValue="{ row }">
            <span class="num t-danger">{{ formatQty((row as AlertRecord).currentValue) }}</span>
          </template>
          <template #col-thresholdValue="{ row }">
            <span class="num">{{ formatQty((row as AlertRecord).thresholdValue) }}</span>
          </template>
          <template #col-handleRemark="{ row }">
            <template v-if="(row as AlertRecord).status === '已处理'">
              <div class="handle">
                <span class="t-xs">{{ (row as AlertRecord).handleUser ?? '—' }} {{ (row as AlertRecord).handleTime ?? '' }}</span>
                <span class="t-xs t-3 handle__remark">
                  {{ (row as AlertRecord).handleRemark || '—' }}
                </span>
              </div>
            </template>
            <span v-else class="t-3">—</span>
          </template>
          <template #col-actions="{ row }">
            <el-button
              v-if="(row as AlertRecord).status === '未处理' && hasPerm('sys:alert:handle')"
              link
              type="primary"
              @click.stop="openHandle(row as AlertRecord)"
            >
              处理
            </el-button>
            <el-button link type="primary" @click.stop="goQuery(row as AlertRecord)">
              定位库存
            </el-button>
          </template>
        </AppTable>
      </div>

      <!-- ============ Tab2：预警规则 ============ -->
      <div v-show="activeTab === 'rule'" class="pane" role="tabpanel">
        <div class="af-filters">
          <div class="fitem">
            <span class="fitem__label">关键字</span>
            <el-input
              v-model="ruleFilters.keyword"
              placeholder="规则名称"
              clearable
              style="width: 168px"
              @keyup.enter="onRuleSearch"
            />
          </div>
          <div class="fitem">
            <span class="fitem__label">仓库</span>
            <WarehouseSelect v-model="ruleFilters.warehouseId" placeholder="全部" style="width: 156px" />
          </div>
          <div class="fitem">
            <span class="fitem__label">预警类型</span>
            <el-select v-model="ruleFilters.alertType" placeholder="全部" clearable style="width: 132px">
              <el-option
                v-for="o in ALERT_TYPE_OPTIONS"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>
          </div>
          <div class="fitem">
            <span class="fitem__label">启停用</span>
            <el-select v-model="ruleFilters.status" placeholder="全部" clearable style="width: 100px">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </div>
          <div class="af-filters__actions">
            <el-button type="primary" :icon="Search" :loading="ruleLoading" @click="onRuleSearch">
              查询
            </el-button>
            <el-button @click="onRuleReset">重置</el-button>
            <el-button v-if="hasPerm('sys:alert:add')" type="primary" :icon="Plus" @click="openCreateRule">
              新增规则
            </el-button>
          </div>
        </div>

        <AppTable
          :columns="ruleColumns"
          :data="(ruleList as unknown as Record<string, unknown>[])"
          :loading="ruleLoading"
          :serial="true"
          stripe
          :pagination="{ total: ruleTotal, pageNum: rulePageNum, pageSize: rulePageSize }"
          :empty-text="ruleFilters.keyword || ruleFilters.warehouseId || ruleFilters.alertType || ruleFilters.status !== undefined ? '未查询到符合条件的规则' : '暂无预警规则，点击「新增规则」创建'"
          @page-change="onRulePageChange"
        >
          <template #col-alertType="{ row }">
            <el-tag size="small" effect="light" :type="alertTypeInfo((row as AlertRule).alertType).type">
              {{ alertTypeLabel((row as AlertRule).alertType) }}
            </el-tag>
          </template>
          <template #col-scope="{ row }">
            <span class="t-sm">{{ ruleScope(row as AlertRule) }}</span>
          </template>
          <template #col-condition="{ row }">
            <span class="num">{{ alertConditionText(row as AlertRule) }}</span>
          </template>
          <template #col-status="{ row }">
            <el-switch
              :model-value="(row as AlertRule).status === 1"
              size="small"
              :loading="togglingId === (row as AlertRule).id"
              :disabled="!hasPerm('sys:alert:edit') || togglingId !== null"
              @change="toggleRule(row as AlertRule)"
            />
          </template>
          <template #col-actions="{ row }">
            <el-button
              v-if="hasPerm('sys:alert:edit')"
              link
              type="primary"
              @click.stop="openEditRule(row as AlertRule)"
            >
              编辑
            </el-button>
            <el-button
              v-if="hasPerm('sys:alert:delete')"
              link
              type="danger"
              @click.stop="onDeleteRule(row as AlertRule)"
            >
              删除
            </el-button>
          </template>
        </AppTable>
      </div>
    </section>

    <!-- 处理预警 -->
    <RecordHandleDialog
      v-model:visible="handleVisible"
      :record="handleRecord"
      :pending="handlePending"
      @confirm="onHandleConfirm"
    />

    <!-- 规则表单 -->
    <RuleFormDialog
      v-model:visible="ruleDialogVisible"
      :rule="editingRule"
      @save="onSaveRule"
    />
  </div>
</template>

<style scoped lang="scss">
.al-panel {
  padding: 0;
  overflow: hidden;
}

/* 下划线式双 Tab（区别于 query 页汇总卡与单据页分段控件） */
.atabs {
  display: flex;
  gap: var(--sp-6);
  padding: 0 var(--sp-5);
  border-bottom: 1px solid var(--c-border);

  &__item {
    position: relative;
    border: none;
    background: transparent;
    padding: var(--sp-4) 2px;
    font-family: inherit;
    font-size: var(--fs-md);
    color: var(--c-text-2);
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: var(--sp-2);
    transition: color var(--dur-fast) var(--ease-out);

    &::after {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      bottom: -1px;
      height: 2px;
      background-color: transparent;
    }

    &:hover {
      color: var(--c-text);
    }

    &.is-active {
      color: var(--c-primary);
      font-weight: var(--fw-medium);

      &::after {
        background-color: var(--c-primary);
      }
    }
  }

  &__count {
    font-size: var(--fs-xs);
    color: var(--c-text-3);
    background-color: var(--c-bg-muted);
    border: 1px solid var(--c-border);
    border-radius: 999px;
    padding: 0 6px;
    line-height: 16px;
  }
}

.pane {
  padding: var(--sp-4) var(--sp-5) var(--sp-5);
}

.af-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-3) var(--sp-4);
  margin-bottom: var(--sp-4);

  &__actions {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    margin-left: auto;
  }
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin: calc(-1 * var(--sp-2)) 0 var(--sp-3);
}

.fitem {
  display: flex;
  align-items: center;
  gap: var(--sp-2);

  &__label {
    font-size: var(--fs-sm);
    color: var(--c-text-2);
    flex-shrink: 0;
  }
}

.handle {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: var(--lh-tight);

  &__remark {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

@media (max-width: 768px) {
  .af-filters__actions {
    margin-left: 0;
  }

  .fitem {
    width: 100%;

    :deep(.el-input),
    :deep(.el-select),
    :deep(.el-date-editor) {
      flex: 1;
      width: auto !important;
    }
  }
}
</style>
