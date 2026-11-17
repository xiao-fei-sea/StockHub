<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, GaugeChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'

/**
 * ECharts 轻量封装（按需注册：折线/柱状/饼图/仪表盘）。
 * 用法：<AppChart :option="chartOption" height="280px" />
 * option 变化自动 setOption，容器尺寸变化自动 resize，组件卸载自动 dispose。
 */
defineOptions({ name: 'AppChart' })

echarts.use([
  LineChart,
  BarChart,
  PieChart,
  GaugeChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  CanvasRenderer,
])

const props = withDefaults(
  defineProps<{
    option: EChartsCoreOption
    height?: string
    loading?: boolean
  }>(),
  { height: '280px', loading: false },
)

const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null

function render() {
  if (!chart) return
  chart.setOption(props.option, { notMerge: true })
}

onMounted(() => {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value, undefined, { renderer: 'canvas' })
  render()
  if (props.loading) chart.showLoading({ text: '加载中…', color: '#3b82f6', textColor: '#5a6270', maskColor: 'rgba(255,255,255,0.7)' })
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(chartRef.value)
})

watch(
  () => props.option,
  () => render(),
  { deep: true },
)

watch(
  () => props.loading,
  (loading) => {
    if (!chart) return
    if (loading) {
      chart.showLoading({ text: '加载中…', color: '#3b82f6', textColor: '#5a6270', maskColor: 'rgba(255,255,255,0.7)' })
    } else {
      chart.hideLoading()
    }
  },
)

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="chartRef" class="app-chart" :style="{ height }" />
</template>

<style scoped>
.app-chart {
  width: 100%;
}
</style>
