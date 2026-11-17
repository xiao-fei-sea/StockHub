<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RefreshRight } from '@element-plus/icons-vue'
import type { EChartsCoreOption } from 'echarts/core'
import { AppChart, LoadState, PageHeader, WarehouseSelect } from '@/components'
import {
  getCategoryDistribution,
  getInOutTrend,
  getStockAging,
  getTopProducts,
  getWarehouseOccupancy,
} from '@/api/stats'
import type {
  CategoryDistribution,
  StockAging,
  TopProducts,
  TrendData,
  WarehouseOccupancy,
} from '@/types/entity'
import { formatNumber, formatQty } from '@/utils/format'

defineOptions({ name: 'StatsAnalysis' })

/* ---------------- 全局维度：仓库 + 时间范围 ---------------- */
const whId = ref<number | null>(null)
const days = ref(30)
const topType = ref<'inbound' | 'outbound' | 'total'>('total')

/* ---------------- 区块加载状态（独立 loading/error/retry） ---------------- */
const state = reactive({
  trendLoading: true, trendError: false,
  catLoading: true, catError: false,
  occLoading: true, occError: false,
  topLoading: true, topError: false,
  agingLoading: true, agingError: false,
})

const trend = ref<TrendData | null>(null)
const category = ref<CategoryDistribution | null>(null)
const occupancy = ref<WarehouseOccupancy | null>(null)
const top = ref<TopProducts | null>(null)
const aging = ref<StockAging | null>(null)

function whParams(): { warehouseId?: number } {
  return whId.value ? { warehouseId: whId.value } : {}
}

async function loadTrend() {
  state.trendLoading = true
  state.trendError = false
  try {
    trend.value = await getInOutTrend({ days: days.value, ...whParams() })
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
    category.value = await getCategoryDistribution(whParams())
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
    occupancy.value = await getWarehouseOccupancy(whParams())
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
    top.value = await getTopProducts({
      limit: 10,
      type: topType.value,
      days: days.value,
      ...whParams(),
    })
  } catch {
    state.topError = true
    top.value = null
  } finally {
    state.topLoading = false
  }
}

async function loadAging() {
  state.agingLoading = true
  state.agingError = false
  try {
    aging.value = await getStockAging({ topN: 10, ...whParams() })
  } catch {
    state.agingError = true
    aging.value = null
  } finally {
    state.agingLoading = false
  }
}

function reloadAll() {
  loadTrend()
  loadCategory()
  loadOccupancy()
  loadTop()
  loadAging()
}

onMounted(reloadAll)

function onWarehouseChange() {
  reloadAll()
}

function changeDays(d: number) {
  if (days.value === d) return
  days.value = d
  loadTrend()
  loadTop()
}

function changeTopType(t: 'inbound' | 'outbound' | 'total') {
  if (topType.value === t) return
  topType.value = t
  loadTop()
}

/* ---------------- 区间读数 ---------------- */
const rangeText = computed(() =>
  trend.value ? `${trend.value.startDate} ~ ${trend.value.endDate}` : '—',
)

const netQty = computed(() => {
  const s = trend.value?.summary
  if (!s) return null
  return s.inboundQtyTotal - s.outboundQtyTotal
})

const avgIn = computed(() => (trend.value ? trend.value.summary.inboundQtyTotal / days.value : null))
const avgOut = computed(() => (trend.value ? trend.value.summary.outboundQtyTotal / days.value : null))

const outInRatio = computed(() => {
  const s = trend.value?.summary
  if (!s || !s.inboundQtyTotal) return null
  return s.outboundQtyTotal / s.inboundQtyTotal
})

const peakDay = computed(() => {
  const series = trend.value?.series ?? []
  if (!series.length) return null
  return series.reduce((max, cur) =>
    cur.inboundQty + cur.outboundQty > max.inboundQty + max.outboundQty ? cur : max,
  )
})

/* ---------------- 图表（纯色、无渐变） ---------------- */
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

const agingOption = computed<EChartsCoreOption>(() => {
  const segments = aging.value?.agingSegments ?? []
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: '#15171c',
      borderWidth: 0,
      textStyle: { color: '#ffffff', fontSize: 12 },
      formatter: (params: { name: string; value: number; dataIndex: number }[]) => {
        const p = params[0]
        const seg = segments[p.dataIndex]
        if (!seg) return ''
        return `${seg.segment}<br/>库存 ${formatQty(seg.qty)} 件 · SKU ${seg.skuCount} 个<br/>占比 ${(seg.qtyRatio * 100).toFixed(1)}%`
      },
    },
    grid: { left: 4, right: 8, top: 24, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      data: segments.map((s) => s.segment),
      axisLine: { lineStyle: { color: '#e4e7eb' } },
      axisTick: { show: false },
      axisLabel: { color: '#5a6270', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#eef0f3' } },
      axisLabel: { color: '#949ca8', fontSize: 11 },
    },
    series: [
      {
        type: 'bar',
        barWidth: 24,
        data: segments.map((s) => s.qty),
        itemStyle: { color: '#3b82f6', borderRadius: [3, 3, 0, 0] },
        label: {
          show: true,
          position: 'top',
          color: '#949ca8',
          fontSize: 11,
          formatter: (p: { value: number }) => `${Math.round((p.value / Math.max(1, segments.reduce((a, b) => a + b.qty, 0))) * 100)}%`,
        },
      },
    ],
  }
})

/* ---------------- Top 榜单最大值（行内条占比） ---------------- */
const topMax = computed(() => {
  const items = top.value?.items ?? []
  return items.reduce((m, i) => Math.max(m, i.qty), 0)
})
</script>

<template>
  <div class="page analysis">
    <PageHeader title="统计分析" desc="出入库流量、库存结构与货位利用的多维分析工作台">
      <template #actions>
        <div class="scope">
          <span class="t-xs t-3">仓库范围</span>
          <WarehouseSelect v-model="whId" style="width: 180px" placeholder="全部仓库（数据权限内）" @change="onWarehouseChange" />
        </div>
        <div class="segment">
          <button
            v-for="d in [7, 30, 90]"
            :key="d"
            type="button"
            class="segment__btn"
            :class="{ 'is-active': days === d }"
            @click="changeDays(d)"
          >
            近 {{ d }} 天
          </button>
        </div>
        <el-button :icon="RefreshRight" @click="reloadAll">刷新</el-button>
      </template>
    </PageHeader>

    <!-- ============ 深色区间摘要：与看板浅色指标带形成层次差异 ============ -->
    <section class="hero">
      <div class="hero__range">
        <span class="hero__label">统计区间</span>
        <b class="hero__date num">{{ rangeText }}</b>
        <span class="hero__days">近 {{ days }} 天 · 已过账口径</span>
      </div>
      <div class="hero__metrics">
        <div class="hero-metric">
          <span class="hero-metric__label">入库合计</span>
          <span class="hero-metric__value num">{{ state.trendLoading ? '—' : formatQty(trend?.summary?.inboundQtyTotal) }}</span>
          <span class="hero-metric__unit">件</span>
        </div>
        <div class="hero-metric">
          <span class="hero-metric__label">出库合计</span>
          <span class="hero-metric__value num">{{ state.trendLoading ? '—' : formatQty(trend?.summary?.outboundQtyTotal) }}</span>
          <span class="hero-metric__unit">件</span>
        </div>
        <div class="hero-metric">
          <span class="hero-metric__label">净变化</span>
          <span
            class="hero-metric__value num"
            :class="{
              'is-up': (netQty ?? 0) > 0,
              'is-down': (netQty ?? 0) < 0,
            }"
          >
            {{ netQty === null ? '—' : `${netQty > 0 ? '+' : ''}${formatQty(netQty)}` }}
          </span>
          <span class="hero-metric__unit">件</span>
        </div>
        <div class="hero-metric">
          <span class="hero-metric__label">出 / 入比</span>
          <span class="hero-metric__value num">{{ outInRatio === null ? '—' : `${(outInRatio * 100).toFixed(1)}%` }}</span>
          <span class="hero-metric__unit">出库量占入库量</span>
        </div>
      </div>
    </section>

    <!-- ============ 趋势大图 + 竖排读数侧栏 ============ -->
    <section class="panel trend">
      <div class="panel__header">
        <span class="panel__title">出入库趋势<em class="panel__desc">{{ rangeText }}</em></span>
      </div>
      <div class="panel__body trend__body">
        <LoadState
          :loading="state.trendLoading"
          :error="state.trendError"
          :empty="!state.trendLoading && !state.trendError && (trend?.series?.length ?? 0) === 0"
          height="320px"
          @retry="loadTrend"
        >
          <div class="trend__layout">
            <AppChart :option="trendOption" height="320px" />
            <aside class="reads">
              <div class="read">
                <span class="read__label">日均入库</span>
                <span class="read__value num">{{ avgIn === null ? '—' : formatQty(avgIn) }}</span>
                <span class="read__unit">件 / 天</span>
              </div>
              <div class="read">
                <span class="read__label">日均出库</span>
                <span class="read__value num">{{ avgOut === null ? '—' : formatQty(avgOut) }}</span>
                <span class="read__unit">件 / 天</span>
              </div>
              <div class="read">
                <span class="read__label">峰值单日</span>
                <span class="read__value num">
                  {{ peakDay ? formatQty(peakDay.inboundQty + peakDay.outboundQty) : '—' }}
                </span>
                <span class="read__unit">{{ peakDay ? peakDay.date : '—' }}</span>
              </div>
              <div class="read">
                <span class="read__label">峰值日出/入</span>
                <span class="read__value num">
                  {{ peakDay ? `${formatQty(peakDay.inboundQty)} / ${formatQty(peakDay.outboundQty)}` : '—' }}
                </span>
                <span class="read__unit">入库 / 出库 件</span>
              </div>
            </aside>
          </div>
        </LoadState>
      </div>
    </section>

    <!-- ============ 三列分析：账龄柱状 | 利用率列表 | 分类占比 ============ -->
    <section class="tri">
      <article class="panel">
        <div class="panel__header">
          <span class="panel__title">库存账龄分布<em class="panel__desc">按入库后滞留天数分桶</em></span>
        </div>
        <div class="panel__body">
          <LoadState
            :loading="state.agingLoading"
            :error="state.agingError"
            :empty="!state.agingLoading && !state.agingError && (aging?.agingSegments?.length ?? 0) === 0"
            height="260px"
            @retry="loadAging"
          >
            <AppChart :option="agingOption" height="250px" />
          </LoadState>
        </div>
      </article>

      <article class="panel">
        <div class="panel__header">
          <span class="panel__title">仓库货位利用率<em class="panel__desc">占用 / 总货位</em></span>
        </div>
        <div class="panel__body">
          <LoadState
            :loading="state.occLoading"
            :error="state.occError"
            :empty="!state.occLoading && !state.occError && (occupancy?.items?.length ?? 0) === 0"
            height="260px"
            @retry="loadOccupancy"
          >
            <ul class="occ-list">
              <li v-for="w in occupancy?.items" :key="w.warehouseId" class="occ-row">
                <div class="occ-row__head">
                  <span class="occ-row__name text-ellipsis">{{ w.warehouseName }}</span>
                  <span class="occ-row__pct num">{{ Math.round(w.occupancyRate * 100) }}%</span>
                </div>
                <div class="occ-row__bar">
                  <span :style="{ width: `${Math.round(w.occupancyRate * 100)}%` }" />
                </div>
                <div class="occ-row__meta">
                  <span>占用 {{ formatNumber(w.locationUsed) }} / {{ formatNumber(w.locationTotal) }}</span>
                  <span>空闲 {{ formatNumber(w.locationIdle) }} · SKU {{ formatNumber(w.skuCount) }}</span>
                </div>
              </li>
            </ul>
          </LoadState>
        </div>
      </article>

      <article class="panel">
        <div class="panel__header">
          <span class="panel__title">分类库存占比<em class="panel__desc">SKU {{ formatNumber(category?.totalSku) }} 个</em></span>
        </div>
        <div class="panel__body">
          <LoadState
            :loading="state.catLoading"
            :error="state.catError"
            :empty="!state.catLoading && !state.catError && (category?.items?.length ?? 0) === 0"
            height="260px"
            @retry="loadCategory"
          >
            <ul class="cat-list">
              <li v-for="c in category?.items" :key="c.categoryName" class="cat-row">
                <div class="cat-row__head">
                  <span class="cat-row__name text-ellipsis">{{ c.categoryName }}</span>
                  <span class="cat-row__qty num">{{ formatQty(c.qty) }}</span>
                </div>
                <div class="cat-row__bar">
                  <span :style="{ width: `${Math.max(2, Math.round(c.qtyRatio * 100))}%` }" />
                </div>
                <span class="cat-row__pct num">{{ (c.qtyRatio * 100).toFixed(1) }}% · {{ c.skuCount }} SKU</span>
              </li>
            </ul>
          </LoadState>
        </div>
      </article>
    </section>

    <!-- ============ 底部：Top 榜单 + 滞销清单 ============ -->
    <section class="duo">
      <article class="panel">
        <div class="panel__header">
          <span class="panel__title">出入库 Top 商品<em class="panel__desc">近 {{ days }} 天 · 前 10 名</em></span>
          <div class="segment">
            <button
              v-for="t in [
                { v: 'total', l: '合计' },
                { v: 'inbound', l: '入库' },
                { v: 'outbound', l: '出库' },
              ]"
              :key="t.v"
              type="button"
              class="segment__btn"
              :class="{ 'is-active': topType === t.v }"
              @click="changeTopType(t.v as 'inbound' | 'outbound' | 'total')"
            >
              {{ t.l }}
            </button>
          </div>
        </div>
        <div class="panel__body panel__body--flush">
          <LoadState
            :loading="state.topLoading"
            :error="state.topError"
            :empty="!state.topLoading && !state.topError && (top?.items?.length ?? 0) === 0"
            height="320px"
            @retry="loadTop"
          >
            <table class="rank-table">
              <thead>
                <tr>
                  <th class="rank-table__rank">#</th>
                  <th>商品</th>
                  <th>分类</th>
                  <th class="rank-table__qty">数量</th>
                  <th class="rank-table__orders">单数</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="i in top?.items" :key="i.productId">
                  <td class="rank-table__rank">
                    <span class="rank-badge" :class="{ 'is-top': i.rank <= 3 }">{{ i.rank }}</span>
                  </td>
                  <td>
                    <div class="rank-table__product">
                      <span class="text-ellipsis">{{ i.productName }}</span>
                      <em class="mono">{{ i.productCode }}</em>
                    </div>
                  </td>
                  <td class="t-3">{{ i.categoryName }}</td>
                  <td class="rank-table__qty">
                    <div class="qty-cell">
                      <span class="qty-cell__bar">
                        <span :style="{ width: `${topMax ? Math.max(4, (i.qty / topMax) * 100) : 0}%` }" />
                      </span>
                      <b class="num">{{ formatQty(i.qty) }}</b>
                    </div>
                  </td>
                  <td class="rank-table__orders num t-3">{{ i.orderCount }}</td>
                </tr>
              </tbody>
            </table>
          </LoadState>
        </div>
      </article>

      <article class="panel">
        <div class="panel__header">
          <span class="panel__title">滞销商品<em class="panel__desc">按最后出库时间倒序 · 前 10 个</em></span>
        </div>
        <div class="panel__body panel__body--flush">
          <LoadState
            :loading="state.agingLoading"
            :error="state.agingError"
            :empty="!state.agingLoading && !state.agingError && (aging?.slowMoving?.length ?? 0) === 0"
            height="320px"
            @retry="loadAging"
          >
            <ul class="slow-list">
              <li v-for="s in aging?.slowMoving" :key="`${s.productId}-${s.warehouseId}`" class="slow-row">
                <div class="slow-row__main">
                  <span class="slow-row__name text-ellipsis">{{ s.productName }}</span>
                  <em class="mono">{{ s.productCode }}</em>
                </div>
                <div class="slow-row__meta">
                  <span>{{ s.warehouseName }}</span>
                  <span>库存 <b class="num">{{ formatQty(s.qty) }}</b></span>
                  <span>库龄 <b class="num">{{ s.agingDays }}</b> 天</span>
                </div>
                <div class="slow-row__foot">
                  <span>最后入库 {{ s.lastInboundTime }}</span>
                  <span>{{ s.lastOutboundTime ? `最后出库 ${s.lastOutboundTime}` : '从未出库' }}</span>
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
.analysis {
  gap: var(--sp-4);
}

.scope {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-right: var(--sp-2);
}

/* ================= 分段控件 ================= */
.segment {
  display: inline-flex;
  padding: 2px;
  background-color: var(--c-bg-muted);
  border-radius: var(--radius-sm);

  &__btn {
    height: 26px;
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

/* ================= 深色区间摘要 ================= */
.hero {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: var(--sp-8);
  flex-wrap: wrap;
  padding: var(--sp-5) var(--sp-6);
  background-color: var(--c-bg-inverse);
  border: 1px solid var(--c-border-inverse);
  border-radius: var(--radius-lg);
  color: var(--c-text-inverse);

  &__range {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 6px;
    min-width: 220px;
  }

  &__label {
    font-size: var(--fs-xs);
    color: #8b93a1;
    letter-spacing: 0.08em;
  }

  &__date {
    font-size: var(--fs-lg);
    font-weight: var(--fw-semibold);
    letter-spacing: 0.01em;
  }

  &__days {
    font-size: var(--fs-xs);
    color: #8b93a1;
  }

  &__metrics {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    flex: 1;
    min-width: 480px;
  }
}

.hero-metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 2px var(--sp-5);
  border-left: 1px solid var(--c-border-inverse);

  &__label {
    font-size: var(--fs-xs);
    color: #8b93a1;
  }

  &__value {
    font-size: var(--fs-3xl);
    line-height: 1.1;
    font-weight: var(--fw-semibold);
    letter-spacing: -0.02em;
    color: var(--c-text-inverse);

    &.is-up {
      color: #4ade80;
    }

    &.is-down {
      color: #f87171;
    }
  }

  &__unit {
    font-size: var(--fs-xs);
    color: #8b93a1;
  }
}

/* ================= 趋势 + 读数侧栏 ================= */
.trend__body {
  min-height: 320px;
}

.trend__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 200px;
  gap: var(--sp-5);
  align-items: stretch;
}

.reads {
  display: flex;
  flex-direction: column;
  justify-content: space-around;
  border-left: 1px solid var(--c-border);
  padding-left: var(--sp-5);
}

.read {
  display: flex;
  flex-direction: column;
  gap: 2px;

  &__label {
    font-size: var(--fs-xs);
    color: var(--c-text-3);
  }

  &__value {
    font-size: var(--fs-xl);
    font-weight: var(--fw-semibold);
    color: var(--c-text);
    letter-spacing: -0.01em;
  }

  &__unit {
    font-size: var(--fs-xs);
    color: var(--c-text-3);
  }
}

/* ================= 三列分析 ================= */
.tri {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--sp-4);
}

/* 利用率列表（纯 CSS 进度条，区别于看板柱状图） */
.occ-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.occ-row {
  &__head {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    gap: var(--sp-2);
    margin-bottom: 6px;
  }

  &__name {
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    color: var(--c-text);
  }

  &__pct {
    font-size: var(--fs-sm);
    font-weight: var(--fw-semibold);
    color: var(--c-primary);
  }

  &__bar {
    height: 6px;
    background-color: var(--c-bg-muted);
    border-radius: var(--radius-full);
    overflow: hidden;

    > span {
      display: block;
      height: 100%;
      background-color: var(--c-primary);
      border-radius: var(--radius-full);
      transition: width var(--dur-slow) var(--ease-out);
    }
  }

  &__meta {
    display: flex;
    justify-content: space-between;
    gap: var(--sp-2);
    margin-top: 5px;
    font-size: var(--fs-xs);
    color: var(--c-text-3);
  }
}

/* 分类占比列表 */
.cat-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.cat-row {
  &__head {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    gap: var(--sp-2);
    margin-bottom: 5px;
  }

  &__name {
    font-size: var(--fs-sm);
    color: var(--c-text);
  }

  &__qty {
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    color: var(--c-text-2);
  }

  &__bar {
    height: 4px;
    background-color: var(--c-bg-muted);
    border-radius: var(--radius-full);
    overflow: hidden;

    > span {
      display: block;
      height: 100%;
      background-color: var(--c-primary);
      border-radius: var(--radius-full);
      transition: width var(--dur-slow) var(--ease-out);
    }
  }

  &__pct {
    display: block;
    margin-top: 3px;
    font-size: var(--fs-xs);
    color: var(--c-text-3);
  }
}

/* ================= 底部两列 ================= */
.duo {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
  gap: var(--sp-4);
}

/* Top 榜单表格 */
.rank-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--fs-sm);

  th {
    padding: var(--sp-2) var(--sp-4);
    background-color: var(--c-bg-subtle);
    border-bottom: 1px solid var(--c-border);
    font-size: var(--fs-xs);
    font-weight: var(--fw-medium);
    color: var(--c-text-3);
    text-align: left;
  }

  td {
    padding: var(--sp-3) var(--sp-4);
    border-bottom: 1px solid var(--c-border);
    color: var(--c-text-2);
    vertical-align: middle;
  }

  tbody tr {
    transition: background-color var(--dur-fast) var(--ease-out);

    &:hover {
      background-color: var(--c-bg-subtle);
    }

    &:last-child td {
      border-bottom: none;
    }
  }

  &__rank {
    width: 44px;
    text-align: center;
  }

  &__qty {
    width: 34%;
  }

  &__orders {
    width: 56px;
    text-align: right;
  }

  &__product {
    display: flex;
    flex-direction: column;
    gap: 1px;
    min-width: 0;

    > span {
      color: var(--c-text);
      font-weight: var(--fw-medium);
    }

    em {
      font-style: normal;
      font-size: var(--fs-xs);
      color: var(--c-text-3);
    }
  }
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--radius-sm);
  background-color: var(--c-bg-muted);
  font-size: var(--fs-xs);
  font-weight: var(--fw-medium);
  color: var(--c-text-2);

  &.is-top {
    background-color: var(--c-primary-soft);
    color: var(--c-primary);
  }
}

.qty-cell {
  display: flex;
  align-items: center;
  gap: var(--sp-3);

  &__bar {
    flex: 1;
    height: 5px;
    background-color: var(--c-bg-muted);
    border-radius: var(--radius-full);
    overflow: hidden;

    > span {
      display: block;
      height: 100%;
      background-color: var(--c-primary);
      border-radius: var(--radius-full);
    }
  }

  b {
    min-width: 72px;
    text-align: right;
    color: var(--c-text);
    font-weight: var(--fw-medium);
  }
}

/* 滞销清单 */
.slow-list {
  max-height: 360px;
  overflow-y: auto;
}

.slow-row {
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--c-border);

  &:last-child {
    border-bottom: none;
  }

  &__main {
    display: flex;
    align-items: baseline;
    gap: var(--sp-2);
    min-width: 0;
  }

  &__name {
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    color: var(--c-text);
  }

  em {
    font-style: normal;
    font-size: var(--fs-xs);
    color: var(--c-text-3);
  }

  &__meta {
    display: flex;
    gap: var(--sp-4);
    margin-top: 4px;
    font-size: var(--fs-xs);
    color: var(--c-text-2);

    b {
      color: var(--c-text);
      font-weight: var(--fw-medium);
    }
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
  .hero__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    min-width: 320px;
    row-gap: var(--sp-4);
  }

  .hero-metric:nth-child(odd) {
    border-left: none;
  }

  .trend__layout {
    grid-template-columns: 1fr;
  }

  .reads {
    flex-direction: row;
    flex-wrap: wrap;
    gap: var(--sp-5);
    border-left: none;
    border-top: 1px solid var(--c-border);
    padding-left: 0;
    padding-top: var(--sp-4);
  }

  .read {
    min-width: 130px;
  }

  .tri,
  .duo {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hero {
    padding: var(--sp-4);
  }

  .hero__metrics {
    grid-template-columns: 1fr;
    min-width: 0;
  }

  .hero-metric {
    border-left: none;
    padding-left: 0;
  }
}
</style>
