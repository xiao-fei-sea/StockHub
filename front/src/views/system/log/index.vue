<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { AppTable, PageHeader, SearchForm, StatusTag } from '@/components'
import type { SearchField, TableColumn } from '@/components'
import { getLogs } from '@/api/log'
import type { OperLog } from '@/types/entity'
import { enableStatusInfo } from '@/utils/dict'
import { enableTagStatus } from '@/views/base/components/status'
import { usePermission } from '@/composables/usePermission'
import LogDetailDrawer from './components/LogDetailDrawer.vue'

defineOptions({ name: 'SystemLog' })

const { hasPerm } = usePermission()

/* ---------------- 模块快捷 Tab ---------------- */
const MODULE_TABS = ['全部', '认证', '用户管理', '角色权限', '入库管理', '出库管理', '库存管理', '基础资料']
const activeModule = ref('全部')

function onModuleTab(name: string | number) {
  activeModule.value = String(name)
  searchLogs()
}

/* ---------------- 查询与列表 ---------------- */
const searchFields: SearchField[] = [
  { key: 'keyword', type: 'input', placeholder: '标题 / 请求路径 / 操作人' },
  {
    key: 'status',
    type: 'select',
    placeholder: '执行结果',
    width: 120,
    options: [
      { label: '成功', value: 1 },
      { label: '失败', value: 0 },
    ],
  },
  {
    key: 'requestMethod',
    type: 'select',
    placeholder: '请求方法',
    width: 120,
    options: [
      { label: 'GET', value: 'GET' },
      { label: 'POST', value: 'POST' },
      { label: 'PUT', value: 'PUT' },
      { label: 'DELETE', value: 'DELETE' },
    ],
  },
  { key: 'dates', type: 'daterange', label: '操作时间' },
]

const query = reactive<{
  keyword?: string
  status?: number | undefined
  requestMethod?: string | undefined
  dates?: string[]
}>({})

const loading = ref(false)
const list = ref<OperLog[]>([])
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

const columns: TableColumn[] = [
  { prop: 'operTime', label: '操作时间', width: 165 },
  { prop: 'module', label: '模块', width: 110, showOverflowTooltip: true },
  { prop: 'title', label: '操作标题', minWidth: 170, showOverflowTooltip: true },
  { prop: 'requestMethod', label: '方法', width: 84, align: 'center' },
  { prop: 'requestUrl', label: '请求路径', minWidth: 200, showOverflowTooltip: true },
  { prop: 'username', label: '操作人', width: 100, showOverflowTooltip: true },
  { prop: 'status', label: '结果', width: 84, align: 'center' },
  { prop: 'costTime', label: '耗时', width: 84, align: 'right' },
  { prop: 'op', label: '操作', width: 76, align: 'right' },
]

function onQueryChange(value: Record<string, unknown>) {
  Object.assign(query, value)
}

async function loadLogs() {
  const [start, end] = query.dates ?? []
  if (start && end && start > end) {
    ElMessage.warning('开始日期不能晚于结束日期')
    return
  }
  loading.value = true
  try {
    const module = activeModule.value === '全部' ? undefined : activeModule.value
    const res = await getLogs({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(module ? { module } : {}),
      ...(query.keyword ? { keyword: query.keyword } : {}),
      ...(typeof query.status === 'number' ? { status: query.status } : {}),
      ...(query.requestMethod ? { requestMethod: query.requestMethod } : {}),
      ...(start ? { startDate: start } : {}),
      ...(end ? { endDate: end } : {}),
    })
    list.value = res.list ?? []
    total.value = res.total ?? 0
    if (!list.value.length && pageNum.value > 1) {
      pageNum.value -= 1
      await loadLogs()
    }
  } finally {
    loading.value = false
  }
}

function searchLogs() {
  pageNum.value = 1
  loadLogs()
}

function onPageChange({ pageNum: p, pageSize: s }: { pageNum: number; pageSize: number }) {
  pageNum.value = p
  pageSize.value = s
  loadLogs()
}

/* ---------------- 详情（sys:log:query） ---------------- */
const detailVisible = ref(false)
const detailId = ref<number | null>(null)

function openDetail(row: OperLog) {
  if (!hasPerm('sys:log:query')) return
  detailId.value = row.id
  detailVisible.value = true
}

onMounted(() => {
  loadLogs()
})
</script>

<template>
  <div class="page">
    <PageHeader title="操作日志" desc="全量操作行为只读审计：按模块快捷筛选，逐条核查参数、结果与来源" />

    <!-- 模块快捷 Tab -->
    <div class="lg-mods">
      <el-tabs v-model="activeModule" @tab-change="onModuleTab">
        <el-tab-pane v-for="m in MODULE_TABS" :key="m" :label="m" :name="m" />
      </el-tabs>
    </div>

    <div class="lg-page">
      <SearchForm
        :model-value="query"
        :fields="searchFields"
        :loading="loading"
        @update:model-value="onQueryChange"
        @search="searchLogs"
      />

      <AppTable
        :columns="columns"
        :data="list"
        :loading="loading"
        :pagination="{ total, pageNum, pageSize }"
        row-key="id"
        size="small"
        stripe
        @row-click="(row) => openDetail(row as never)"
        @page-change="onPageChange"
      >
        <template #col-module="{ row }">
          <span class="t-sm">{{ row.module || '—' }}</span>
        </template>
        <template #col-requestMethod="{ row }">
          <span class="mono lg-method">{{ row.requestMethod || '—' }}</span>
        </template>
        <template #col-requestUrl="{ row }">
          <span class="mono t-xs lg-url">{{ row.requestUrl || '—' }}</span>
        </template>
        <template #col-status="{ row }">
          <StatusTag
            :status="enableTagStatus(row.status)"
            :label="enableStatusInfo(row.status).value === 1 ? '成功' : '失败'"
            size="small"
          />
        </template>
        <template #col-costTime="{ row }">
          <span class="num t-sm">{{ row.costTime ?? 0 }} ms</span>
        </template>
        <template #col-op="{ row }">
          <el-button
            v-if="hasPerm('sys:log:query')"
            link
            type="primary"
            size="small"
            @click.stop="openDetail(row)"
          >
            详情
          </el-button>
          <span v-else class="t-xs t-3">—</span>
        </template>
      </AppTable>
    </div>

    <!-- 日志详情 -->
    <LogDetailDrawer v-model:visible="detailVisible" :log-id="detailId" />
  </div>
</template>

<style scoped lang="scss">
.lg-mods {
  margin-bottom: var(--sp-4);
  padding: 0 var(--sp-5);
  background-color: var(--c-bg-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);

  :deep(.el-tabs__header) {
    margin-bottom: 0;
  }

  :deep(.el-tabs__nav-wrap::after) {
    height: 1px;
    background-color: var(--c-border);
  }

  :deep(.el-tabs__item) {
    height: 44px;
    line-height: 44px;
    font-size: var(--fs-sm);
  }
}

.lg-page {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.lg-method {
  font-size: var(--fs-xs);
}

.lg-url {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--c-text-3);
}
</style>
