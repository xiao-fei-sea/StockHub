<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, RefreshRight, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { EChartsCoreOption } from 'echarts/core'
import { AppChart, LoadState, PageHeader, WarehouseSelect } from '@/components'
import { getDashboardSummary, getInOutTrend, getCategoryDistribution, getTopProducts, getWarehouseOccupancy } from '@/api/stats'
import { getAlerts, type AlertRecord } from '@/api/alert'
import { pathToRouteName } from '@/store/permission'
import { formatNumber, formatQty, today } from '@/utils/format'

defineOptions({ name: 'Dashboard' })

const router = useRouter()

/* ---------------- 区块加载状态（各自独立 loading/error/重试） ---------------- */
const state = reactive({
  summaryLoading: true, summaryError: false,
  trendLoading: true, trendError: false,
  catLoading: true, catError: false,
  occLoading: true, occError: false,
  topLoading: true, topError: false,
  alertLoading: true, alertError: false,
})

const summary = ref<Awaited<ReturnType<typeof getDashboardSummary>> | null>(null)
const trend = ref<Awaited<ReturnType<typeof getInOutTrend>> | null>(null)
const category = ref<Awaited<ReturnType<typeof getCategoryDistribution>> | null>(null)
const occupancy = ref<Awaited<ReturnType<typeof getWarehouseOccupancy>> | null>(null)
const topProducts = ref<Awaited<ReturnType<typeof getTopProducts>> | null>(null)
const alerts = ref<AlertRecord[]>([])

const whId = ref<number | null>(null)
const trendDays = ref(30)

/* ---------------- 各区块加载 ---------------- */
async function loadSummary() {
  state.summaryLoading = true
  state.summaryError = false
  try {
    summary.value = await getDashboardSummary(whId.value ? { warehouseId: whId.value } : undefined)
  } catch {
    state.summaryError = true
    summary.value = null
  } finally {
    state.summaryLoading = false
  }
}

async function loadTrend() {
  state.trendLoading = true
  state.trendError = false
  try {
    trend.value = await getInOutTrend({
      days: trendDays.value,
      ...(whId.value ? { warehouseId: whId.value } : {}),
    })
  } catch {
    state.trendError = true
    trend.value = null
  } finally {
    state.trendLoading = false
  }
}

async function loadCategory() {
  state.catLoading = true
  state.catError = false
  try {
    category.value = await getCategoryDistribution(whId.value ? { warehouseId: whId.value } : undefined)
  } catch {
    state.catError = true
    category.value = null
  } finally {
    state.catLoading = false
  }
}

async function loadOccupancy() {
  state.occLoading = true
  state.occError = false
  try {
    occupancy.value = await getWarehouseOccupancy(
      whId.value ? { warehouseId: whId.value } : undefined,
    )
  } catch {
    state.occError = true
    occupancy.value = null
  } finally {
    state.occLoading = false
  }
}

async function loadTop() {
  state.topLoading = true
  state.topError = false
  try {
    topProducts.value = await getTopProducts({
      limit: 10,
      type: 'total',
      days: trendDays.value,
      ...(whId.value ? { warehouseId: whId.value } : {}),
    })
  } catch {
    state.topError = true
    topProducts.value = null
  } finally {
    state.topLoading = false
  }
}

async function loadAlerts() {
  state.alertLoading = true
  state.alertError = false
  try {
    const res = await getAlerts({ status: '未处理', pageNum: 1, pageSize: 5, ...(whId.value ? { warehouseId: whId.value } : {}) })
    alerts.value = res.list ?? []
  } catch {
    state.alertError = true
    alerts.value = []
  } finally {
    state.alertLoading = false
  }
}

function reloadAll() {
  loadSummary()
  loadTrend()
  loadCategory()
  loadOccupancy()
  loadTop()
  loadAlerts()
}

onMounted(reloadAll)

function onWarehouseChange() {
  reloadAll()
}

function changeTrendDays(days: number) {
  if (trendDays.value === days) return
  trendDays.value = days
  loadTrend()
  loadTop()
}

/* ---------------- 指标 ---------------- */
const pendingTotal = computed(() => {
  const p = summary.value?.pending
  if (!p) return 0
  return p.inboundPendingCount + p.outboundPendingCount + p.stocktakePendingCount + p.transferPendingCount
})

/* ---------------- 下钻跳转（带菜单权限校验，交互设计 §5.3） ---------------- */
function go(path: string, query?: Record<string, string>) {
  if (!router.hasRoute(pathToRouteName(path))) {
    ElMessage.warning('无权访问该页面')
    return
  }
  router.push({ path, query })
}

function goInventory() {
  go('/inventory/query', whId.value ? { warehouseId: String(whId.value) } : undefined)
}

/* ---------------- 图表配置（纯色、无渐变、无大面积色光） ---------------- */
const trendOption = computed<EChartsCoreOption>(() => {
  const series = trend.value?.series ?? []
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#15171c',
      borderWidth: 0,
      padding: [8, 12],
      textStyle: { color: '#ffffff', fontSize: 12 },
      valueFormatter: (v: unknown) => `${formatQty(Number(v))} 件`,
    },
    legend: {
      data: ['入库', '出库'],
      right: 4,
      top: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 16,
      textStyle: { color: '#5a6270', fontSize: 12 },
    },
    grid: { left: 8, right: 16, top: 40, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: series.map((s) => s.date),
      axisLine: { lineStyle: { color: '#e4e7eb' } },
      axisTick: { show: false },
      axisLabel: { color: '#949ca8', fontSize: 11, formatter: (v: string) => v.slice(5) },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#eef0f3' } },
      axisLabel: { color: '#949ca8', fontSize: 11 },
      minInterval: 1,
    },
    series: [
      {
        name: '入库',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2, color: '#3b82f6' },
        itemStyle: { color: '#3b82f6' },
        data: series.map((s) => s.inboundQty),
      },
      {
        name: '出库',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2, color: '#64748b' },
        itemStyle: { color: '#64748b' },
        data: series.map((s) => s.outboundQty),
      },
    ],
  }
})

const trendSummaryText = computed(() => {
  const s = trend.value?.summary
  if (!s) return ''
  return `区间合计：入库 ${formatQty(s.inboundQtyTotal)} 件 · 出库 ${formatQty(s.outboundQtyTotal)} 件`
})

const categoryOption = computed<EChartsCoreOption>(() => {
  const items = category.value?.items ?? []
  const palette = ['#3b82f6', '#22c55e', '#f59e0b', '#64748b', '#93c5fd', '#1f232b', '#cbd5e1']
  return {
    tooltip: {
      backgroundColor: '#15171c',
      borderWidth: 0,
      textStyle: { color: '#ffffff', fontSize: 12 },
      formatter: (p: { name: string; value: number; percent: number }) =>
        `${p.name}<br/>${formatQty(p.value)} 件（${p.percent}%）`,
    },
    legend: {
      orient: 'vertical',
      right: 0,
      top: 'middle',
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 12,
      textStyle: { color: '#5a6270', fontSize: 12 },
      formatter: (name: string) => {
        const hit = items.find((i) => i.categoryName === name)
        return hit ? `${name} ${Math.round(hit.qtyRatio * 100)}%` : name
      },
    },
    series: [
      {
        type: 'pie',
        radius: ['58%', '78%'],
        center: ['30%', '50%'],
        avoidLabelOverlap: true,
        label: { show: false },
        itemStyle: { borderColor: '#ffffff', borderWidth: 2 },
        emphasis: { scaleSize: 4 },
        data: items.map((i, idx) => ({
          name: i.categoryName,
          value: i.qty,
          itemStyle: { color: palette[idx % palette.length] },
        })),
      },
    ],
  }
})

const topOption = computed<EChartsCoreOption>(() => {
  const items = [...(topProducts.value?.items ?? [])].reverse()
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: '#15171c',
      borderWidth: 0,
      textStyle: { color: '#ffffff', fontSize: 12 },
      formatter: (params: { name: string; value: number; dataIndex: number }[]) => {
        const p = params[0]
        const item = items[p.dataIndex]
        return item ? `${item.productName}<br/>${formatQty(p.value)} 件` : ''
      },
    },
    grid: { left: 4, right: 44, top: 8, bottom: 0, containLabel: true },
    xAxis: {
      type: 'value',
      splitLine: { show: false },
      axisLabel: { show: false },
    },
    yAxis: {
      type: 'category',
      data: items.map((i) => (i.productName.length > 10 ? `${i.productName.slice(0, 10)}…` : i.productName)),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#5a6270', fontSize: 11 },
    },
    series: [
      {
        type: 'bar',
        barWidth: 10,
        data: items.map((i) => i.qty),
        itemStyle: { color: '#3b82f6', borderRadius: [0, 3, 3, 0] },
        label: {
          show: true,
          position: 'right',
          color: '#949ca8',
          fontSize: 11,
          formatter: (p: { value: number }) => formatNumber(p.value),
        },
      },
    ],
  }
})

const occOption = computed<EChartsCoreOption>(() => {
  const items = occupancy.value?.items ?? []
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: '#15171c',
      borderWidth: 0,
      textStyle: { color: '#ffffff', fontSize: 12 },
      formatter: (params: { name: string; value: number }[]) => {
        const p = params[0]
        const item = items.find((i) => i.warehouseName === p.name)
        return `${p.name}<br/>利用率 ${Math.round(p.value * 100)}%<br/>占用 ${item ? formatNumber(item.locationUsed) : '-'} / ${item ? formatNumber(item.locationTotal) : '-'} 个货位`
      },
    },
    grid: { left: 4, right: 20, top: 24, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      data: items.map((i) => i.warehouseName),
      axisLine: { lineStyle: { color: '#e4e7eb' } },
      axisTick: { show: false },
      axisLabel: { color: '#5a6270', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      max: 1,
      splitLine: { lineStyle: { color: '#eef0f3' } },
      axisLabel: { color: '#949ca8', fontSize: 11, formatter: (v: number) => `${Math.round(v * 100)}%` },
    },
    series: [
      {
        type: 'bar',
        barWidth: 28,
        data: items.map((i) => i.occupancyRate),
        itemStyle: { color: '#3b82f6', borderRadius: [3, 3, 0, 0] },
        label: {
          show: true,
          position: 'top',
          color: '#5a6270',
          fontSize: 11,
          formatter: (p: { value: number }) => `${Math.round((p.value as number) * 100)}%`,
        },
      },
    ],
  }
})

function levelType(level: string): 'info' | 'warning' | 'danger' {
  if (level === '严重') return 'danger'
  if (level === '警告') return 'warning'
  return 'info'
}
</script>

<template>
  <div class="page dash">
    <!-- ============ 页头：标题 + 仓库范围 + 刷新 ============ -->
    <PageHeader title="数据看板" desc="当前库存、今日出入库与待办事项的实时概况">
      <template #actions>
        <div class="dash__scope">
          <span class="t-xs t-3">仓库范围</span>
          <WarehouseSelect v-model="whId" style="width: 180px" placeholder="全部仓库（数据权限内）" @change="onWarehouseChange" />
        </div>
        <el-button :icon="RefreshRight" @click="reloadAll">刷新</el-button>
      </template>
    </PageHeader>

    <!-- ============ 指标带（整块面板 + 发丝线分隔，非独立卡片堆叠） ============ -->
    <section class="kpi-strip">
      <button class="kpi" type="button" @click="goInventory">
        <span class="kpi__label">当前库存</span>
        <span class="kpi__value num">{{ state.summaryLoading ? '—' : formatQty(summary?.inventory?.totalQty) }}</span>
        <span class="kpi__sub">
          SKU {{ summary ? formatNumber(summary.inventory.skuCount) : '—' }} · 批次 {{ summary ? formatNumber(summary.inventory.batchCount) : '—' }}
        </span>
        <el-icon class="kpi__arrow"><ArrowRight /></el-icon>
      </button>

      <button class="kpi" type="button" @click="go('/inbound/order', { startDate: today() })">
        <span class="kpi__label">今日入库</span>
        <span class="kpi__value num">{{ summary ? summary.today.inboundOrderCount : '—' }}<em>单</em></span>
        <span class="kpi__sub">数量 {{ summary ? formatQty(summary.today.inboundQty) : '—' }} 件</span>
        <el-icon class="kpi__arrow"><ArrowRight /></el-icon>
      </button>

      <button class="kpi" type="button" @click="go('/outbound/order', { startDate: today() })">
        <span class="kpi__label">今日出库</span>
        <span class="kpi__value num">{{ summary ? summary.today.outboundOrderCount : '—' }}<em>单</em></span>
        <span class="kpi__sub">数量 {{ summary ? formatQty(summary.today.outboundQty) : '—' }} 件</span>
        <el-icon class="kpi__arrow"><ArrowRight /></el-icon>
      </button>

      <button class="kpi" type="button" @click="go('/inbound/order', { status: '1' })">
        <span class="kpi__label">待审核单据</span>
        <span class="kpi__value num">{{ state.summaryLoading ? '—' : pendingTotal }}</span>
        <span class="kpi__sub" v-if="summary">
          入库 {{ summary.pending.inboundPendingCount }} · 出库 {{ summary.pending.outboundPendingCount }} · 调拨 {{ summary.pending.transferPendingCount }} · 盘点 {{ summary.pending.stocktakePendingCount }}
        </span>
        <el-icon class="kpi__arrow"><ArrowRight /></el-icon>
      </button>

      <button class="kpi kpi--alert" type="button" @click="go('/inventory/alert', { tab: 'record' })">
        <span class="kpi__label">
          <el-icon><Warning /></el-icon>未处理预警
        </span>
        <span class="kpi__value num" :class="{ 'kpi__value--danger': (summary?.alert?.seriousCount ?? 0) > 0 }">
          {{ state.summaryLoading ? '—' : summary?.alert?.unhandledCount ?? '—' }}
        </span>
        <span class="kpi__sub">严重 {{ summary?.alert?.seriousCount ?? '—' }} 条</span>
        <el-icon class="kpi__arrow"><ArrowRight /></el-icon>
      </button>
    </section>

    <!-- ============ 趋势 + 分类 ============ -->
    <section class="dash-grid dash-grid--main">
      <article class="panel">
        <div class="panel__header">
          <span class="panel__title">出入库趋势<em class="panel__desc">{{ trendSummaryText }}</em></span>
          <div class="segment">
            <button
              v-for="d in [7, 30, 90]"
              :key="d"
              type="button"
              class="segment__btn"
              :class="{ 'is-active': trendDays === d }"
              @click="changeTrendDays(d)"
            >
              {{ d }}天
            </button>
          </div>
        </div>
        <div class="panel__body">
          <LoadState
            :loading="state.trendLoading"
            :error="state.trendError"
            :empty="!state.trendLoading && !state.trendError && (trend?.series?.length ?? 0) === 0"
            height="280px"
            @retry="loadTrend"
          >
            <AppChart :option="trendOption" height="280px" />
          </LoadState>
        </div>
      </article>

      <article class="panel">
        <div class="panel__header">
          <span class="panel__title">库存分类分布<em class="panel__desc">按一级分类 · 当前库存数量</em></span>
        </div>
        <div class="panel__body donut">
          <LoadState
            :loading="state.catLoading"
            :error="state.catError"
            :empty="!state.catLoading && !state.catError && (category?.items?.length ?? 0) === 0"
            height="260px"
            @retry="loadCategory"
          >
            <div class="donut__wrap">
              <AppChart :option="categoryOption" height="260px" />
              <div class="donut__center">
                <span class="donut__num num">{{ formatNumber(category?.totalSku) }}</span>
                <span class="donut__unit">SKU 总数</span>
              </div>
            </div>
          </LoadState>
        </div>
      </article>
    </section>

    <!-- ============ Top 榜 + 利用率 + 预警 ============ -->
    <section class="dash-grid dash-grid--sub">
      <article class="panel">
        <div class="panel__header">
          <span class="panel__title">出入库 Top 商品<em class="panel__desc">近 {{ trendDays }} 天 · 出入库合计数量</em></span>
        </div>
        <div class="panel__body">
          <LoadState
            :loading="state.topLoading"
            :error="state.topError"
            :empty="!state.topLoading && !state.topError && (topProducts?.items?.length ?? 0) === 0"
            height="300px"
            @retry="loadTop"
          >
            <AppChart :option="topOption" height="300px" />
          </LoadState>
        </div>
      </article>

      <article class="panel">
        <div class="panel__header">
          <span class="panel__title">货位利用率<em class="panel__desc">分母为启用货位</em></span>
        </div>
        <div class="panel__body">
          <LoadState
            :loading="state.occLoading"
            :error="state.occError"
            :empty="!state.occLoading && !state.occError && (occupancy?.items?.length ?? 0) === 0"
            height="240px"
            @retry="loadOccupancy"
          >
            <div class="occ-summary">
              <span>总货位 <b class="num">{{ formatNumber(occupancy?.summary?.locationTotal) }}</b></span>
              <span>已占用 <b class="num">{{ formatNumber(occupancy?.summary?.locationUsed) }}</b></span>
              <span>空闲 <b class="num">{{ formatNumber(occupancy?.summary?.locationIdle) }}</b></span>
            </div>
            <AppChart :option="occOption" height="220px" />
          </LoadState>
        </div>
      </article>

      <article class="panel">
        <div class="panel__header">
          <span class="panel__title">预警提醒<em class="panel__desc">未处理 {{ summary?.alert?.unhandledCount ?? '—' }} 条</em></span>
          <el-button link type="primary" size="small" @click="go('/inventory/alert', { tab: 'record' })">
            全部<el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <div class="panel__body panel__body--flush">
          <LoadState
            :loading="state.alertLoading"
            :error="state.alertError"
            :empty="!state.alertLoading && !state.alertError && alerts.length === 0"
            height="300px"
            @retry="loadAlerts"
          >
            <ul class="alert-list">
              <li
                v-for="a in alerts"
                :key="a.id"
                class="alert-row"
                @click="go('/inventory/alert', { tab: 'record', productId: String(a.productId) })"
              >
                <div class="alert-row__head">
                  <el-tag :type="levelType(a.level)" size="small" effect="light">{{ a.level }}</el-tag>
                  <span class="alert-row__product text-ellipsis">{{ a.productName }}</span>
                </div>
                <div class="alert-row__meta">
                  <span>{{ a.alertTypeDesc || a.alertType }}</span>
                  <span class="num">{{ formatQty(a.currentValue) }} / {{ formatQty(a.thresholdValue) }}</span>
                </div>
                <div class="alert-row__foot">
                  <span>{{ a.warehouseName }}</span>
                  <span>{{ a.createTime }}</span>
                </div>
              </li>
            </ul>
          </LoadState>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped lang="scss">
.dash {
  gap: var(--sp-4);
}

.dash__scope {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-right: var(--sp-2);
}

/* ================= 指标带：整条面板 + 发丝线分栏 ================= */
.kpi-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  background-color: var(--c-bg-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
  overflow: hidden;
}

.kpi {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: var(--sp-5);
  border: none;
  border-right: 1px solid var(--c-border);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-out);

  &:last-child {
    border-right: none;
  }

  &:hover {
    background-color: var(--c-bg-subtle);

    .kpi__arrow {
      opacity: 1;
      transform: translateX(0);
    }
  }

  &__label {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: var(--fs-xs);
    color: var(--c-text-3);
  }

  &__value {
    font-size: var(--fs-3xl);
    line-height: 1.15;
    font-weight: var(--fw-semibold);
    letter-spacing: -0.02em;
    color: var(--c-text);

    em {
      font-style: normal;
      font-size: var(--fs-sm);
      font-weight: var(--fw-regular);
      color: var(--c-text-3);
      margin-left: 3px;
    }

    &--danger {
      color: var(--c-danger);
    }
  }

  &__sub {
    font-size: var(--fs-xs);
    color: var(--c-text-3);
    line-height: 1.5;
  }

  &__arrow {
    position: absolute;
    top: var(--sp-5);
    right: var(--sp-4);
    font-size: 13px;
    color: var(--c-text-3);
    opacity: 0;
    transform: translateX(-4px);
    transition: opacity var(--dur) var(--ease-out), transform var(--dur) var(--ease-out);
  }
}

/* ================= 布局栅格 ================= */
.dash-grid {
  display: grid;
  gap: var(--sp-4);

  &--main {
    grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  }

  &--sub {
    grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr) minmax(0, 1fr);
  }
}

/* ================= 分段控件 ================= */
.segment {
  display: inline-flex;
  padding: 2px;
  background-color: var(--c-bg-muted);
  border-radius: var(--radius-sm);

  &__btn {
    height: 24px;
    padding: 0 10px;
    border: none;
    border-radius: 3px;
    background: transparent;
    color: var(--c-text-2);
    font-size: var(--fs-xs);
    cursor: pointer;
    transition: background-color var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);

    &.is-active {
      background-color: var(--c-bg-surface);
      color: var(--c-text);
      font-weight: var(--fw-medium);
      box-shadow: var(--shadow-xs);
    }
  }
}

/* ================= 环图中心数字 ================= */
.donut {
  &__wrap {
    position: relative;
  }

  &__center {
    position: absolute;
    left: 30%;
    top: 50%;
    transform: translate(-50%, -50%);
    display: flex;
    flex-direction: column;
    align-items: center;
    pointer-events: none;
    width: 96px;
  }

  &__num {
    font-size: var(--fs-xl);
    font-weight: var(--fw-semibold);
    color: var(--c-text);
    letter-spacing: -0.02em;
  }

  &__unit {
    font-size: var(--fs-xs);
    color: var(--c-text-3);
  }
}

/* ================= 利用率摘要 ================= */
.occ-summary {
  display: flex;
  gap: var(--sp-5);
  margin-bottom: var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--c-text-3);

  b {
    color: var(--c-text);
    font-weight: var(--fw-semibold);
    margin-left: 4px;
  }
}

/* ================= 预警列表 ================= */
.alert-list {
  max-height: 320px;
  overflow-y: auto;
}

.alert-row {
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--c-border);
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-out);

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background-color: var(--c-bg-subtle);
  }

  &__head {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    min-width: 0;
  }

  &__product {
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    color: var(--c-text);
  }

  &__meta {
    display: flex;
    justify-content: space-between;
    gap: var(--sp-3);
    margin-top: 4px;
    font-size: var(--fs-xs);
    color: var(--c-text-2);
  }

  &__foot {
    display: flex;
    justify-content: space-between;
    gap: var(--sp-3);
    margin-top: 2px;
    font-size: var(--fs-xs);
    color: var(--c-text-3);
  }
}

/* ================= 响应式 ================= */
@media (max-width: 1280px) {
  .kpi-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));

    .kpi:nth-child(3) {
      border-right: none;
    }
  }

  .dash-grid--main,
  .dash-grid--sub {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .kpi-strip {
    grid-template-columns: 1fr;

    .kpi {
      border-right: none;
      border-bottom: 1px solid var(--c-border);
    }
  }
}
</style>
