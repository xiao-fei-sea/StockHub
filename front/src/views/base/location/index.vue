<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  Box,
  Delete,
  Edit,
  Grid,
  Plus,
  Refresh,
  Search,
  VideoPause,
  VideoPlay,
  House as WarehouseIcon,
} from '@element-plus/icons-vue'
import { AppTable, PageHeader, StatusTag, WarehouseSelect, confirmDialog } from '@/components'
import type { TableColumn } from '@/components'
import {
  batchGenerateLocations,
  createArea,
  createLocation,
  createRack,
  deleteArea,
  deleteLocation,
  deleteRack,
  getAreaOptions,
  getAreas,
  getLocations,
  getRacks,
  updateArea,
  updateLocation,
  updateRack,
} from '@/api/location'
import { getWarehouseOptions } from '@/api/warehouse'
import type {
  Area,
  AreaForm,
  AreaOption,
  Location,
  LocationBatchForm,
  LocationBatchResult,
  LocationForm,
  Rack,
  RackForm,
  RackOption,
} from '@/types/entity'
import { LOCATION_STATUS, LOCATION_TYPE, dictLabel, enableStatusInfo } from '@/utils/dict'
import { enableTagStatus, locationTagStatus } from '../components/status'
import { usePermission } from '@/composables/usePermission'

defineOptions({ name: 'BaseLocation' })

const { hasPerm } = usePermission()

/** 库区类型（接口文档：存储区/拣货区/暂存区）。 */
const AREA_TYPES = ['存储区', '拣货区', '暂存区']

/* ---------------- 左侧：仓库 → 库区 → 货架 树 ---------------- */
type NodeKind = 'warehouse' | 'area' | 'rack'

interface TreeNode {
  key: string
  type: NodeKind
  id: number
  warehouseId: number
  areaId?: number
  label: string
  code: string
  /** 货位状态统计等上下文名称 */
  whName?: string
  areaName?: string
  rackName?: string
  /** 层级类型/启停等原始数据（area/rack 节点） */
  raw?: Area | Rack
  children?: TreeNode[]
}

const treeLoading = ref(false)
const treeData = ref<TreeNode[]>([])
const treeRef = ref()

function areaNodeOf(areaId: number): TreeNode | undefined {
  for (const wh of treeData.value) {
    const found = wh.children?.find((c) => c.id === areaId && c.type === 'area')
    if (found) return found
  }
  return undefined
}

function rackChildrenOf(areaId: number): TreeNode[] {
  return areaNodeOf(areaId)?.children ?? []
}

async function loadTree() {
  treeLoading.value = true
  try {
    const warehouses = await getWarehouseOptions()
    const areaPages = await Promise.all(
      warehouses.map((w) => getAreas({ warehouseId: w.id, pageSize: 100 })),
    )
    const areasByWh = areaPages.map((p) => p.list ?? [])
    const allAreas = areasByWh.flat()
    const rackPages = await Promise.all(allAreas.map((a) => getRacks({ areaId: a.id, pageSize: 100 })))
    const rackMap = new Map<number, Rack[]>()
    allAreas.forEach((a, i) => rackMap.set(a.id, rackPages[i].list ?? []))

    treeData.value = warehouses.map((w, wi) => ({
      key: `wh-${w.id}`,
      type: 'warehouse',
      id: w.id,
      warehouseId: w.id,
      label: w.warehouseName,
      code: w.warehouseCode,
      whName: w.warehouseName,
      children: areasByWh[wi].map((a) => ({
        key: `area-${a.id}`,
        type: 'area',
        id: a.id,
        warehouseId: w.id,
        label: a.areaName,
        code: a.areaCode,
        whName: w.warehouseName,
        areaName: a.areaName,
        raw: a,
        children: (rackMap.get(a.id) ?? []).map((r) => ({
          key: `rack-${r.id}`,
          type: 'rack',
          id: r.id,
          warehouseId: w.id,
          areaId: a.id,
          label: r.rackName,
          code: r.rackCode,
          whName: w.warehouseName,
          areaName: a.areaName,
          rackName: r.rackName,
          raw: r,
        })),
      })),
    }))
  } finally {
    treeLoading.value = false
  }
}

/* ---------------- 当前选中层级与货位查询 ---------------- */
const sel = reactive({
  kind: null as NodeKind | null,
  warehouseId: 0,
  areaId: 0,
  rackId: 0,
  whName: '',
  areaName: '',
  rackName: '',
})

function clearSelection() {
  sel.kind = null
  sel.warehouseId = 0
  sel.areaId = 0
  sel.rackId = 0
  sel.whName = ''
  sel.areaName = ''
  sel.rackName = ''
}

function onNodeClick(data: TreeNode) {
  sel.kind = data.type
  sel.whName = data.whName ?? ''
  sel.areaName = data.areaName ?? ''
  sel.rackName = data.rackName ?? ''
  if (data.type === 'warehouse') {
    sel.warehouseId = data.id
    sel.areaId = 0
    sel.rackId = 0
  } else if (data.type === 'area') {
    sel.warehouseId = data.warehouseId
    sel.areaId = data.id
    sel.rackId = 0
  } else {
    sel.warehouseId = data.warehouseId
    sel.areaId = data.areaId ?? 0
    sel.rackId = data.id
  }
  searchLocations()
}

function viewAllLocations() {
  clearSelection()
  searchLocations()
}

/* ---------------- 右侧：货位分页表格 ---------------- */
const locLoading = ref(false)
const locList = ref<Location[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const locQuery = reactive<{ keyword?: string; currentStatus?: string; locationType?: string }>({})

function locationParams(): Record<string, unknown> {
  return {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    ...(sel.warehouseId ? { warehouseId: sel.warehouseId } : {}),
    ...(sel.areaId ? { areaId: sel.areaId } : {}),
    ...(sel.rackId ? { rackId: sel.rackId } : {}),
    ...(locQuery.keyword ? { keyword: locQuery.keyword } : {}),
    ...(locQuery.currentStatus ? { currentStatus: locQuery.currentStatus } : {}),
    ...(locQuery.locationType ? { locationType: locQuery.locationType } : {}),
  }
}

async function loadLocations() {
  locLoading.value = true
  try {
    const res = await getLocations(locationParams())
    locList.value = res.list ?? []
    total.value = res.total ?? 0
    if (!locList.value.length && pageNum.value > 1) {
      pageNum.value -= 1
      await loadLocations()
    }
  } finally {
    locLoading.value = false
  }
}

function searchLocations() {
  pageNum.value = 1
  loadLocations()
}

function onLocPageChange({ pageNum: p, pageSize: s }: { pageNum: number; pageSize: number }) {
  pageNum.value = p
  pageSize.value = s
  loadLocations()
}

const columns: TableColumn[] = [
  { prop: 'locationCode', label: '货位编码', width: 150 },
  { prop: 'locationName', label: '货位名称', width: 110 },
  { prop: 'areaName', label: '所属库区', minWidth: 130, showOverflowTooltip: true },
  { prop: 'rackName', label: '所属货架', minWidth: 110, showOverflowTooltip: true },
  { prop: 'locationType', label: '货位类型', width: 96 },
  { prop: 'capacityVolume', label: '容积（m³）', width: 110, align: 'right' },
  { prop: 'currentStatus', label: '货位状态', width: 96, align: 'center' },
  { prop: 'status', label: '启用状态', width: 96, align: 'center' },
  { prop: 'op', label: '操作', width: 120, align: 'right' },
]

/* ---------------- 级联选项（货位/批量生成共用） ---------------- */
const areaList = ref<AreaOption[]>([])
const rackList = ref<RackOption[]>([])

async function loadAreaOptions(warehouseId?: number) {
  areaList.value = warehouseId ? (await getAreaOptions(warehouseId)) ?? [] : []
}

async function loadRackOptions(areaId?: number) {
  rackList.value = areaId ? ((await getRacks({ areaId, pageSize: 100 })).list ?? []) : []
}

interface CascadeForm {
  warehouseId: number
  areaId: number
  rackId: number
}

async function onWarehousePick(form: CascadeForm, id: number | null) {
  form.warehouseId = id ?? 0
  form.areaId = 0
  form.rackId = 0
  await loadAreaOptions(form.warehouseId || undefined)
  rackList.value = []
}

async function onAreaPick(form: CascadeForm, id: number | null) {
  form.areaId = id ?? 0
  form.rackId = 0
  await loadRackOptions(form.areaId || undefined)
}

async function prepareCascade(form: CascadeForm) {
  await Promise.all([
    loadAreaOptions(form.warehouseId || undefined),
    loadRackOptions(form.areaId || undefined),
  ])
}

/* ---------------- 库区表单 ---------------- */
const areaVisible = ref(false)
const areaEditId = ref<number | null>(null)
const areaSubmitting = ref(false)
const areaFormRef = ref<FormInstance>()
const areaForm = reactive<AreaForm>({ warehouseId: 0, areaCode: '', areaName: '', areaType: '存储区', status: 1, sort: 0, remark: '' })

const areaRules: FormRules<AreaForm> = {
  warehouseId: [{ required: true, type: 'number', min: 1, message: '请选择所属仓库', trigger: 'change' }],
  areaCode: [
    { required: true, message: '请输入库区编码', trigger: 'blur' },
    { pattern: /^QY-[A-Za-z0-9-]+$/, message: '编码需以 QY- 开头', trigger: 'blur' },
  ],
  areaName: [
    { required: true, message: '请输入库区名称', trigger: 'blur' },
    { min: 2, max: 30, message: '库区名称长度 2-30 字', trigger: 'blur' },
  ],
  areaType: [{ required: true, message: '请选择库区类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  remark: [{ max: 200, message: '备注不能超过 200 字', trigger: 'blur' }],
}

function openAreaCreate(warehouseId?: number) {
  areaEditId.value = null
  Object.assign(areaForm, {
    warehouseId: warehouseId ?? sel.warehouseId ?? 0,
    areaCode: '',
    areaName: '',
    areaType: '存储区',
    status: 1,
    sort: 0,
    remark: '',
  })
  areaFormRef.value?.clearValidate()
  areaVisible.value = true
}

function openAreaEdit(node: TreeNode) {
  const a = node.raw as Area
  areaEditId.value = a.id
  Object.assign(areaForm, {
    warehouseId: a.warehouseId,
    areaCode: a.areaCode,
    areaName: a.areaName,
    areaType: a.areaType,
    status: a.status,
    sort: a.sort ?? 0,
    remark: a.remark ?? '',
  })
  areaFormRef.value?.clearValidate()
  areaVisible.value = true
}

function syncAreaNode(id: number, data: AreaForm) {
  const node = areaNodeOf(id)
  if (node) {
    node.label = data.areaName
    node.code = data.areaCode
    node.areaName = data.areaName
    node.raw = { ...(node.raw as Area), ...data }
  }
}

async function submitArea() {
  const valid = await areaFormRef.value?.validate().catch(() => false)
  if (!valid) return
  areaSubmitting.value = true
  try {
    if (areaEditId.value !== null) {
      await updateArea(areaEditId.value, { ...areaForm })
      syncAreaNode(areaEditId.value, areaForm)
      if (sel.areaId === areaEditId.value) sel.areaName = areaForm.areaName
      ElMessage.success('保存成功')
    } else {
      const id = await createArea({ ...areaForm })
      const wh = treeData.value.find((w) => w.id === areaForm.warehouseId)
      if (wh) {
        wh.children = [
          ...(wh.children ?? []),
          {
            key: `area-${id}`,
            type: 'area',
            id,
            warehouseId: areaForm.warehouseId,
            label: areaForm.areaName,
            code: areaForm.areaCode,
            whName: wh.label,
            areaName: areaForm.areaName,
            raw: { ...areaForm, id } as Area,
            children: [],
          },
        ]
      }
      ElMessage.success('保存成功')
    }
    areaVisible.value = false
  } catch {
    /* 业务错误由 request 统一提示 */
  } finally {
    areaSubmitting.value = false
  }
}

async function onDeleteArea(node: TreeNode) {
  const ok = await confirmDialog({
    title: '删除库区',
    message: `确定删除库区「${node.label}（${node.code}）」吗？\n若其下存在货架或被库存/单据引用，将无法删除。`,
    confirmText: '删除',
    type: 'danger',
  })
  if (!ok) return
  try {
    await deleteArea(node.id)
    ElMessage.success('删除成功')
    const wh = treeData.value.find((w) => w.id === node.warehouseId)
    if (wh) wh.children = (wh.children ?? []).filter((c) => c.id !== node.id)
    if (sel.areaId === node.id || sel.rackId) {
      sel.warehouseId = node.warehouseId
      sel.areaId = 0
      sel.rackId = 0
      sel.kind = 'warehouse'
      sel.areaName = ''
      sel.rackName = ''
      searchLocations()
    }
  } catch {
    /* 引用拦截由 request 统一提示 */
  }
}

/** 库区启停（状态随库区编辑体一并提交，接口无独立启停端点）。 */
async function toggleAreaStatus(node: TreeNode) {
  const a = node.raw as Area
  const next = a.status === 1 ? 0 : 1
  try {
    await updateArea(a.id, {
      warehouseId: a.warehouseId,
      areaCode: a.areaCode,
      areaName: a.areaName,
      areaType: a.areaType,
      status: next,
      sort: a.sort ?? 0,
      remark: a.remark ?? '',
    })
    a.status = next
    ElMessage.success(next === 1 ? '库区已启用' : '库区已停用')
  } catch {
    /* 业务错误由 request 统一提示 */
  }
}

/* ---------------- 货架表单 ---------------- */
const rackVisible = ref(false)
const rackEditId = ref<number | null>(null)
const rackSubmitting = ref(false)
const rackFormRef = ref<FormInstance>()
const rackForm = reactive<RackForm>({
  warehouseId: 0,
  areaId: 0,
  rackCode: '',
  rackName: '',
  floorCount: 1,
  columnCount: 1,
  status: 1,
  sort: 0,
  remark: '',
})

const rackRules: FormRules<RackForm> = {
  rackCode: [
    { required: true, message: '请输入货架编码', trigger: 'blur' },
    { pattern: /^JH-[A-Za-z0-9-]+$/, message: '编码需以 JH- 开头', trigger: 'blur' },
  ],
  rackName: [{ required: true, message: '请输入货架名称', trigger: 'blur' }, { max: 30, message: '货架名称不能超过 30 字', trigger: 'blur' }],
  floorCount: [{ required: true, type: 'number', min: 1, max: 99, message: '层数需为 1-99 的整数', trigger: 'blur' }],
  columnCount: [{ required: true, type: 'number', min: 1, max: 99, message: '列数需为 1-99 的整数', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  remark: [{ max: 200, message: '备注不能超过 200 字', trigger: 'blur' }],
}

function openRackCreate(areaNode: TreeNode) {
  rackEditId.value = null
  Object.assign(rackForm, {
    warehouseId: areaNode.warehouseId,
    areaId: areaNode.id,
    rackCode: '',
    rackName: '',
    floorCount: 1,
    columnCount: 1,
    status: 1,
    sort: 0,
    remark: '',
  })
  rackFormRef.value?.clearValidate()
  rackVisible.value = true
}

function openRackEdit(node: TreeNode) {
  const r = node.raw as Rack
  rackEditId.value = r.id
  Object.assign(rackForm, {
    warehouseId: r.warehouseId,
    areaId: r.areaId,
    rackCode: r.rackCode,
    rackName: r.rackName,
    floorCount: r.floorCount,
    columnCount: r.columnCount,
    status: r.status,
    sort: r.sort ?? 0,
    remark: r.remark ?? '',
  })
  rackFormRef.value?.clearValidate()
  rackVisible.value = true
}

function syncRackNode(id: number, data: RackForm) {
  const list = rackChildrenOf(data.areaId)
  const node = list.find((n) => n.id === id)
  if (node) {
    node.label = data.rackName
    node.code = data.rackCode
    node.rackName = data.rackName
    node.raw = { ...(node.raw as Rack), ...data }
  }
}

async function submitRack() {
  const valid = await rackFormRef.value?.validate().catch(() => false)
  if (!valid) return
  rackSubmitting.value = true
  try {
    if (rackEditId.value !== null) {
      await updateRack(rackEditId.value, { ...rackForm })
      syncRackNode(rackEditId.value, rackForm)
      if (sel.rackId === rackEditId.value) sel.rackName = rackForm.rackName
      ElMessage.success('保存成功')
    } else {
      const id = await createRack({ ...rackForm })
      const area = areaNodeOf(rackForm.areaId)
      if (area) {
        area.children = [
          ...(area.children ?? []),
          {
            key: `rack-${id}`,
            type: 'rack',
            id,
            warehouseId: rackForm.warehouseId,
            areaId: rackForm.areaId,
            label: rackForm.rackName,
            code: rackForm.rackCode,
            whName: area.whName,
            areaName: area.label,
            rackName: rackForm.rackName,
            raw: { ...rackForm, id } as Rack,
          },
        ]
      }
      ElMessage.success('保存成功')
    }
    rackVisible.value = false
  } catch {
    /* 业务错误由 request 统一提示 */
  } finally {
    rackSubmitting.value = false
  }
}

async function onDeleteRack(node: TreeNode) {
  const ok = await confirmDialog({
    title: '删除货架',
    message: `确定删除货架「${node.label}（${node.code}）」吗？\n若其下已生成货位，将无法删除。`,
    confirmText: '删除',
    type: 'danger',
  })
  if (!ok) return
  try {
    await deleteRack(node.id)
    ElMessage.success('删除成功')
    const area = areaNodeOf(node.areaId ?? 0)
    if (area) area.children = (area.children ?? []).filter((c) => c.id !== node.id)
    if (sel.rackId === node.id) {
      sel.rackId = 0
      sel.rackName = ''
      sel.kind = 'area'
      searchLocations()
    }
  } catch {
    /* 引用拦截由 request 统一提示 */
  }
}

/* ---------------- 货位表单 ---------------- */
const locVisible = ref(false)
const locEditId = ref<number | null>(null)
const locSubmitting = ref(false)
const locFormRef = ref<FormInstance>()
const locForm = reactive<LocationForm>({
  warehouseId: 0,
  areaId: 0,
  rackId: 0,
  locationCode: '',
  locationName: '',
  locationType: '存储位',
  capacityVolume: null,
  currentStatus: '空闲',
  status: 1,
  sort: 0,
  remark: '',
})

const manualStatusOptions = computed(() =>
  LOCATION_STATUS.map((s) => ({
    ...s,
    /** 「占用」由库存联动，不可手工选择（表单校验规则 §4） */
    disabled: s.value === '占用' && s.value !== locForm.currentStatus,
  })),
)

const locRules: FormRules<LocationForm> = {
  warehouseId: [{ required: true, type: 'number', min: 1, message: '请选择所属仓库', trigger: 'change' }],
  areaId: [{ required: true, type: 'number', min: 1, message: '请选择所属库区', trigger: 'change' }],
  rackId: [{ required: true, type: 'number', min: 1, message: '请选择所属货架', trigger: 'change' }],
  locationCode: [
    { required: true, message: '请输入货位编码', trigger: 'blur' },
    { pattern: /^HW-[A-Za-z0-9-]+$/, message: '编码需以 HW- 开头', trigger: 'blur' },
  ],
  locationName: [{ required: true, message: '请输入货位名称', trigger: 'blur' }, { max: 30, message: '货位名称不能超过 30 字', trigger: 'blur' }],
  locationType: [{ required: true, message: '请选择货位类型', trigger: 'change' }],
  capacityVolume: [{ type: 'number', min: 0, message: '容积需为不小于 0 的数值，最多 3 位小数', trigger: 'blur' }],
  currentStatus: [{ required: true, message: '请选择货位状态', trigger: 'change' }],
  sort: [{ type: 'integer', min: 0, message: '排序号需为不小于 0 的整数', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  remark: [{ max: 200, message: '备注不能超过 200 字', trigger: 'blur' }],
}

async function openLocCreate() {
  locEditId.value = null
  Object.assign(locForm, {
    warehouseId: sel.warehouseId || 0,
    areaId: sel.areaId || 0,
    rackId: sel.rackId || 0,
    locationCode: '',
    locationName: '',
    locationType: '存储位',
    capacityVolume: null,
    currentStatus: '空闲',
    status: 1,
    sort: 0,
    remark: '',
  })
  await prepareCascade(locForm)
  locFormRef.value?.clearValidate()
  locVisible.value = true
}

async function openLocEdit(row: Location) {
  locEditId.value = row.id
  Object.assign(locForm, {
    warehouseId: row.warehouseId,
    areaId: row.areaId,
    rackId: row.rackId,
    locationCode: row.locationCode,
    locationName: row.locationName,
    locationType: row.locationType,
    capacityVolume: row.capacityVolume ?? null,
    currentStatus: row.currentStatus,
    status: row.status,
    sort: row.sort ?? 0,
    remark: row.remark ?? '',
  })
  await prepareCascade(locForm)
  locFormRef.value?.clearValidate()
  locVisible.value = true
}

async function submitLocation() {
  const valid = await locFormRef.value?.validate().catch(() => false)
  if (!valid) return
  locSubmitting.value = true
  try {
    if (locEditId.value !== null) {
      await updateLocation(locEditId.value, { ...locForm })
      ElMessage.success('保存成功')
    } else {
      await createLocation({ ...locForm })
      ElMessage.success('保存成功')
    }
    locVisible.value = false
    loadLocations()
  } catch {
    /* 业务错误由 request 统一提示 */
  } finally {
    locSubmitting.value = false
  }
}

async function onDeleteLocation(row: Location) {
  const ok = await confirmDialog({
    title: '删除货位',
    message: `确定删除货位「${row.locationName}（${row.locationCode}）」吗？\n若该货位存在库存或被单据引用，将无法删除。`,
    confirmText: '删除',
    type: 'danger',
  })
  if (!ok) return
  try {
    await deleteLocation(row.id)
    ElMessage.success('删除成功')
    loadLocations()
  } catch {
    /* 引用拦截由 request 统一提示 */
  }
}

/* ---------------- 批量生成货位 ---------------- */
const batchVisible = ref(false)
const batchSubmitting = ref(false)
const batchFormRef = ref<FormInstance>()
const batchForm = reactive<LocationBatchForm & { startFloor: number; startColumn: number }>({
  warehouseId: 0,
  areaId: 0,
  rackId: 0,
  prefix: '',
  startFloor: 1,
  startColumn: 1,
  floorCount: 1,
  columnCount: 1,
  locationType: '存储位',
  capacityVolume: null,
  skipExists: true,
})

const batchRules: FormRules = {
  warehouseId: [{ required: true, type: 'number', min: 1, message: '请选择所属仓库', trigger: 'change' }],
  areaId: [{ required: true, type: 'number', min: 1, message: '请选择所属库区', trigger: 'change' }],
  rackId: [{ required: true, type: 'number', min: 1, message: '请选择所属货架', trigger: 'change' }],
  locationType: [{ required: true, message: '请选择货位类型', trigger: 'change' }],
  floorCount: [{ required: true, type: 'integer', min: 1, max: 50, message: '请填写有效的层数（1-50）', trigger: 'blur' }],
  columnCount: [{ required: true, type: 'integer', min: 1, max: 50, message: '请填写有效的列数（1-50）', trigger: 'blur' }],
  startFloor: [{ type: 'integer', min: 1, message: '起始层号需为不小于 1 的整数', trigger: 'blur' }],
  startColumn: [{ type: 'integer', min: 1, message: '起始列号需为不小于 1 的整数', trigger: 'blur' }],
}

async function openBatch() {
  Object.assign(batchForm, {
    warehouseId: sel.warehouseId || 0,
    areaId: sel.areaId || 0,
    rackId: sel.rackId || 0,
    prefix: '',
    startFloor: 1,
    startColumn: 1,
    floorCount: sel.rackId ? ((rackChildrenOf(sel.areaId).find((n) => n.id === sel.rackId)?.raw as Rack | undefined)?.floorCount ?? 1) : 1,
    columnCount: sel.rackId ? ((rackChildrenOf(sel.areaId).find((n) => n.id === sel.rackId)?.raw as Rack | undefined)?.columnCount ?? 1) : 1,
    locationType: '存储位',
    capacityVolume: null,
    skipExists: true,
  })
  await prepareCascade(batchForm)
  batchFormRef.value?.clearValidate()
  batchVisible.value = true
}

async function submitBatch() {
  const valid = await batchFormRef.value?.validate().catch(() => false)
  if (!valid) return
  batchSubmitting.value = true
  try {
    const payload: LocationBatchForm = {
      warehouseId: batchForm.warehouseId,
      areaId: batchForm.areaId,
      rackId: batchForm.rackId,
      locationType: batchForm.locationType,
      startFloor: batchForm.startFloor,
      startColumn: batchForm.startColumn,
      floorCount: batchForm.floorCount,
      columnCount: batchForm.columnCount,
      skipExists: batchForm.skipExists,
      ...(batchForm.prefix ? { prefix: batchForm.prefix } : {}),
      ...(batchForm.capacityVolume != null ? { capacityVolume: batchForm.capacityVolume } : {}),
    }
    const res: LocationBatchResult = await batchGenerateLocations(payload)
    ElMessage.success(
      `已生成 ${res.createdCount} 个货位` +
        (res.skippedCount ? `，跳过 ${res.skippedCount} 个已存在编码` : '') +
        `（${res.firstLocationCode} ~ ${res.lastLocationCode}）`,
    )
    batchVisible.value = false
    searchLocations()
  } catch {
    /* 业务错误由 request 统一提示 */
  } finally {
    batchSubmitting.value = false
  }
}

/* ---------------- 顶部动作可用性 ---------------- */
const canCreateArea = ref(false)

async function init() {
  canCreateArea.value = hasPerm('sys:area:add')
  await Promise.all([loadTree()])
  loadLocations()
}

onMounted(init)
</script>

<template>
  <div class="page page--fill">
    <PageHeader title="库区与库位" desc="仓库 → 库区 → 货架 → 货位 四级结构维护，右侧按选中层级查询货位" />

    <div class="split split--tree loc-layout">
      <!-- 左侧：库位结构树 -->
      <aside class="panel loc-tree">
        <div class="panel__header">
          <span class="panel__title">
            库位结构
            <span class="panel__desc">点击节点筛选货位</span>
          </span>
          <div class="loc-tree__ops">
            <el-button size="small" :icon="Refresh" :loading="treeLoading" @click="loadTree">刷新</el-button>
            <el-button
              v-if="canCreateArea"
              size="small"
              type="primary"
              plain
              :icon="Plus"
              :disabled="!sel.warehouseId"
              :title="sel.warehouseId ? '在当前仓库下新增库区' : '请先选择一个仓库'"
              @click="openAreaCreate()"
            >
              新增库区
            </el-button>
          </div>
        </div>

        <div v-loading="treeLoading" class="loc-tree__body">
          <el-tree
            ref="treeRef"
            :data="treeData"
            node-key="key"
            highlight-current-row
            :expand-on-click-node="false"
            :default-expanded-keys="treeData.map((n) => n.key)"
            empty-text="暂无仓库数据"
            @node-click="onNodeClick"
          >
            <template #default="{ data }">
              <span class="tree-node">
                <el-icon class="tree-node__icon">
                  <WarehouseIcon v-if="data.type === 'warehouse'" />
                  <Grid v-else-if="data.type === 'area'" />
                  <Box v-else />
                </el-icon>
                <span class="tree-node__label" :title="data.label">{{ data.label }}</span>
                <span class="tree-node__code mono">{{ data.code }}</span>
                <span class="tree-node__actions" @click.stop>
                  <!-- 仓库节点：新增库区 -->
                  <el-button
                    v-if="data.type === 'warehouse' && hasPerm('sys:area:add')"
                    link
                    size="small"
                    :icon="Plus"
                    title="新增库区"
                    @click="openAreaCreate(data.id)"
                  />
                  <!-- 库区节点：货架维护 + 启停 + 删除 -->
                  <template v-if="data.type === 'area'">
                    <el-button
                      v-if="hasPerm('sys:rack:add')"
                      link
                      size="small"
                      :icon="Plus"
                      title="新增货架"
                      @click="openRackCreate(data)"
                    />
                    <el-button
                      v-if="hasPerm('sys:area:edit')"
                      link
                      size="small"
                      :icon="Edit"
                      title="编辑库区"
                      @click="openAreaEdit(data)"
                    />
                    <el-button
                      v-if="hasPerm('sys:area:edit')"
                      link
                      size="small"
                      :icon="data.raw.status === 1 ? VideoPause : VideoPlay"
                      :title="data.raw.status === 1 ? '停用库区' : '启用库区'"
                      @click="toggleAreaStatus(data)"
                    />
                    <el-button
                      v-if="hasPerm('sys:area:delete')"
                      link
                      size="small"
                      :icon="Delete"
                      class="is-danger"
                      title="删除库区"
                      @click="onDeleteArea(data)"
                    />
                  </template>
                  <!-- 货架节点：货位维护 -->
                  <template v-if="data.type === 'rack'">
                    <el-button
                      v-if="hasPerm('sys:location:batch')"
                      link
                      size="small"
                      :icon="Grid"
                      title="批量生成货位"
                      @click="openBatch"
                    />
                    <el-button
                      v-if="hasPerm('sys:rack:edit')"
                      link
                      size="small"
                      :icon="Edit"
                      title="编辑货架"
                      @click="openRackEdit(data)"
                    />
                    <el-button
                      v-if="hasPerm('sys:rack:delete')"
                      link
                      size="small"
                      :icon="Delete"
                      class="is-danger"
                      title="删除货架"
                      @click="onDeleteRack(data)"
                    />
                  </template>
                </span>
              </span>
            </template>
          </el-tree>
        </div>
      </aside>

      <!-- 右侧：货位表格 -->
      <section class="loc-main">
        <div class="panel loc-crumb">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>
              <button type="button" class="crumb-link" :class="{ 'is-current': !sel.kind }" @click="viewAllLocations">
                全部货位
              </button>
            </el-breadcrumb-item>
            <el-breadcrumb-item v-if="sel.whName">{{ sel.whName }}</el-breadcrumb-item>
            <el-breadcrumb-item v-if="sel.areaName">{{ sel.areaName }}</el-breadcrumb-item>
            <el-breadcrumb-item v-if="sel.rackName">{{ sel.rackName }}</el-breadcrumb-item>
          </el-breadcrumb>
          <span class="t-xs t-3">
            {{ sel.kind ? '已按选中层级过滤' : '未限定层级，查询全部数据权限内货位' }}
          </span>
        </div>

        <AppTable
          :columns="columns"
          :data="locList"
          :loading="locLoading"
          :pagination="{ total, pageNum, pageSize }"
          row-key="id"
          @page-change="onLocPageChange"
        >
          <template #toolbar>
            <div class="toolbar__group">
              <el-input
                v-model="locQuery.keyword"
                placeholder="货位编码 / 名称"
                clearable
                :prefix-icon="Search"
                style="width: 180px"
                @keyup.enter="searchLocations"
                @clear="searchLocations"
              />
              <el-select
                v-model="locQuery.currentStatus"
                placeholder="货位状态"
                clearable
                style="width: 120px"
                @change="searchLocations"
              >
                <el-option v-for="s in LOCATION_STATUS" :key="String(s.value)" :label="s.label" :value="s.value" />
              </el-select>
              <el-select
                v-model="locQuery.locationType"
                placeholder="货位类型"
                clearable
                style="width: 120px"
                @change="searchLocations"
              >
                <el-option v-for="t in LOCATION_TYPE" :key="String(t.value)" :label="t.label" :value="t.value" />
              </el-select>
            </div>
            <div class="toolbar__group">
              <el-button v-permission="'sys:location:batch'" :icon="Grid" @click="openBatch">批量生成</el-button>
              <el-button v-permission="'sys:location:add'" type="primary" :icon="Plus" @click="openLocCreate">
                新增货位
              </el-button>
            </div>
          </template>

          <template #col-currentStatus="{ row }">
            <StatusTag
              :status="locationTagStatus(row.currentStatus)"
              :label="dictLabel(LOCATION_STATUS, row.currentStatus)"
            />
          </template>
          <template #col-status="{ row }">
            <StatusTag :status="enableTagStatus(row.status)" :label="enableStatusInfo(row.status).label" />
          </template>
          <template #col-op="{ row }">
            <el-button v-permission="'sys:location:edit'" link type="primary" size="small" @click="openLocEdit(row)">
              编辑
            </el-button>
            <el-button v-permission="'sys:location:delete'" link type="danger" size="small" @click="onDeleteLocation(row)">
              删除
            </el-button>
          </template>
        </AppTable>
      </section>
    </div>

    <!-- 库区表单 -->
    <el-dialog v-model="areaVisible" :title="areaEditId ? '编辑库区' : '新增库区'" width="560px" destroy-on-close :close-on-click-modal="false">
      <el-form ref="areaFormRef" :model="areaForm" :rules="areaRules" label-width="96px" @submit.prevent>
        <el-form-item label="所属仓库" prop="warehouseId">
          <WarehouseSelect
            :model-value="areaForm.warehouseId || null"
            :disabled="areaEditId !== null"
            @change="areaForm.warehouseId = $event ?? 0"
          />
        </el-form-item>
        <el-form-item label="库区编码" prop="areaCode">
          <el-input v-model="areaForm.areaCode" placeholder="如 QY-001" maxlength="20" />
        </el-form-item>
        <el-form-item label="库区名称" prop="areaName">
          <el-input v-model="areaForm.areaName" placeholder="2-30 字符" maxlength="30" />
        </el-form-item>
        <el-form-item label="库区类型" prop="areaType">
          <el-select v-model="areaForm.areaType" placeholder="请选择" style="width: 100%">
            <el-option v-for="t in AREA_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序号" prop="sort">
          <el-input-number v-model="areaForm.sort" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="areaForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="areaForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="areaVisible = false">取消</el-button>
        <el-button type="primary" :loading="areaSubmitting" @click="submitArea">保存</el-button>
      </template>
    </el-dialog>

    <!-- 货架表单 -->
    <el-dialog v-model="rackVisible" :title="rackEditId ? '编辑货架' : '新增货架'" width="560px" destroy-on-close :close-on-click-modal="false">
      <el-form ref="rackFormRef" :model="rackForm" :rules="rackRules" label-width="96px" @submit.prevent>
        <el-form-item label="所属库区">
          <el-input :model-value="areaNodeOf(rackForm.areaId)?.label ?? ''" disabled />
        </el-form-item>
        <el-form-item label="货架编码" prop="rackCode">
          <el-input v-model="rackForm.rackCode" placeholder="如 JH-001" maxlength="20" />
        </el-form-item>
        <el-form-item label="货架名称" prop="rackName">
          <el-input v-model="rackForm.rackName" placeholder="最多 30 字" maxlength="30" />
        </el-form-item>
        <el-form-item label="层数" prop="floorCount">
          <el-input-number v-model="rackForm.floorCount" :min="1" :max="99" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="列数" prop="columnCount">
          <el-input-number v-model="rackForm.columnCount" :min="1" :max="99" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="排序号" prop="sort">
          <el-input-number v-model="rackForm.sort" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="rackForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="rackForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rackVisible = false">取消</el-button>
        <el-button type="primary" :loading="rackSubmitting" @click="submitRack">保存</el-button>
      </template>
    </el-dialog>

    <!-- 货位表单 -->
    <el-dialog v-model="locVisible" :title="locEditId ? '编辑货位' : '新增货位'" width="620px" destroy-on-close :close-on-click-modal="false">
      <el-form ref="locFormRef" :model="locForm" :rules="locRules" label-width="96px" @submit.prevent>
        <div class="form-grid">
          <el-form-item label="所属仓库" prop="warehouseId">
            <WarehouseSelect :model-value="locForm.warehouseId || null" @change="onWarehousePick(locForm, $event)" />
          </el-form-item>
          <el-form-item label="所属库区" prop="areaId">
            <el-select
              :model-value="locForm.areaId || undefined"
              placeholder="请先选择仓库"
              :disabled="!locForm.warehouseId"
              style="width: 100%"
              @update:model-value="onAreaPick(locForm, $event as number | null)"
            >
              <el-option v-for="a in areaList" :key="a.id" :label="`${a.areaName}（${a.areaCode}）`" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属货架" prop="rackId">
            <el-select
              :model-value="locForm.rackId || undefined"
              placeholder="请先选择库区"
              :disabled="!locForm.areaId"
              style="width: 100%"
              @update:model-value="locForm.rackId = ($event as number | null) ?? 0"
            >
              <el-option v-for="r in rackList" :key="r.id" :label="`${r.rackName}（${r.rackCode}）`" :value="r.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="货位编码" prop="locationCode">
            <el-input v-model="locForm.locationCode" placeholder="如 HW-01010101" maxlength="30" />
          </el-form-item>
          <el-form-item label="货位名称" prop="locationName">
            <el-input v-model="locForm.locationName" placeholder="如 1-1-1" maxlength="30" />
          </el-form-item>
          <el-form-item label="货位类型" prop="locationType">
            <el-select v-model="locForm.locationType" style="width: 100%">
              <el-option v-for="t in LOCATION_TYPE" :key="String(t.value)" :label="t.label" :value="String(t.value)" />
            </el-select>
          </el-form-item>
          <el-form-item label="容积（m³）" prop="capacityVolume">
            <el-input-number v-model="locForm.capacityVolume" :min="0" :precision="3" :controls="false" placeholder="选填" style="width: 100%" />
          </el-form-item>
          <el-form-item label="货位状态" prop="currentStatus">
            <el-select v-model="locForm.currentStatus" style="width: 100%">
              <el-option
                v-for="s in manualStatusOptions"
                :key="String(s.value)"
                :label="s.label"
                :value="String(s.value)"
                :disabled="s.disabled"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="排序号" prop="sort">
            <el-input-number v-model="locForm.sort" :min="0" :precision="0" style="width: 100%" />
          </el-form-item>
          <el-form-item label="启用状态" prop="status">
            <el-radio-group v-model="locForm.status">
              <el-radio :value="1">启用</el-radio>
              <el-radio :value="0">停用</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="备注" prop="remark" class="form-grid__full">
            <el-input v-model="locForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="选填" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="locVisible = false">取消</el-button>
        <el-button type="primary" :loading="locSubmitting" @click="submitLocation">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量生成货位 -->
    <el-dialog v-model="batchVisible" title="批量生成货位" width="620px" destroy-on-close :close-on-click-modal="false">
      <p class="t-sm t-2 batch-tip">
        按「货架层数 × 列数」批量生成货位，生成编码 = 前缀 + 层号 2 位 + 列号 2 位；已存在的编码默认跳过。
      </p>
      <el-form ref="batchFormRef" :model="batchForm" :rules="batchRules" label-width="96px" @submit.prevent>
        <div class="form-grid">
          <el-form-item label="所属仓库" prop="warehouseId">
            <WarehouseSelect :model-value="batchForm.warehouseId || null" @change="onWarehousePick(batchForm, $event)" />
          </el-form-item>
          <el-form-item label="所属库区" prop="areaId">
            <el-select
              :model-value="batchForm.areaId || undefined"
              placeholder="请先选择仓库"
              :disabled="!batchForm.warehouseId"
              style="width: 100%"
              @update:model-value="onAreaPick(batchForm, $event as number | null)"
            >
              <el-option v-for="a in areaList" :key="a.id" :label="`${a.areaName}（${a.areaCode}）`" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属货架" prop="rackId">
            <el-select
              :model-value="batchForm.rackId || undefined"
              placeholder="请先选择库区"
              :disabled="!batchForm.areaId"
              style="width: 100%"
              @update:model-value="batchForm.rackId = ($event as number | null) ?? 0"
            >
              <el-option v-for="r in rackList" :key="r.id" :label="`${r.rackName}（${r.rackCode}）`" :value="r.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="货位类型" prop="locationType">
            <el-select v-model="batchForm.locationType" style="width: 100%">
              <el-option v-for="t in LOCATION_TYPE" :key="String(t.value)" :label="t.label" :value="String(t.value)" />
            </el-select>
          </el-form-item>
          <el-form-item label="编码前缀" prop="prefix">
            <el-input v-model="batchForm.prefix" placeholder="选填，默认取货架编码" maxlength="20" />
          </el-form-item>
          <el-form-item label="生成层数" prop="floorCount">
            <el-input-number v-model="batchForm.floorCount" :min="1" :max="50" :precision="0" style="width: 100%" />
          </el-form-item>
          <el-form-item label="生成列数" prop="columnCount">
            <el-input-number v-model="batchForm.columnCount" :min="1" :max="50" :precision="0" style="width: 100%" />
          </el-form-item>
          <el-form-item label="起始层号" prop="startFloor">
            <el-input-number v-model="batchForm.startFloor" :min="1" :precision="0" style="width: 100%" />
          </el-form-item>
          <el-form-item label="起始列号" prop="startColumn">
            <el-input-number v-model="batchForm.startColumn" :min="1" :precision="0" style="width: 100%" />
          </el-form-item>
          <el-form-item label="统一容积" prop="capacityVolume">
            <el-input-number v-model="batchForm.capacityVolume" :min="0" :precision="3" :controls="false" placeholder="选填" style="width: 100%" />
          </el-form-item>
          <el-form-item label="已存在时" prop="skipExists">
            <el-switch v-model="batchForm.skipExists" active-text="跳过" inactive-text="整体失败" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="submitBatch">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.loc-layout {
  min-height: 480px;
}

.loc-tree {
  display: flex;
  flex-direction: column;
  min-height: 0;

  &__ops {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__body {
    flex: 1;
    min-height: 0;
    overflow: auto;
    padding: var(--sp-2);
  }
}

.loc-main {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  min-width: 0;
}

.loc-crumb {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  flex-wrap: wrap;
}

.crumb-link {
  border: none;
  background: transparent;
  padding: 0;
  font-family: inherit;
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  cursor: pointer;
  transition: color var(--dur-fast) var(--ease-out);

  &:hover {
    color: var(--c-primary);
  }

  &.is-current {
    color: var(--c-primary);
    font-weight: var(--fw-medium);
  }
}

/* 树节点 */
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
  padding-right: var(--sp-1);
  font-size: var(--fs-sm);

  &__icon {
    color: var(--c-text-3);
    flex-shrink: 0;
  }

  &__label {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__code {
    font-size: var(--fs-xs);
    color: var(--c-text-3);
    flex-shrink: 0;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 0;
    opacity: 0;
    flex-shrink: 0;
    transition: opacity var(--dur-fast) var(--ease-out);

    :deep(.el-button + .el-button) {
      margin-left: 0;
    }

    .is-danger:hover {
      color: var(--c-danger);
    }
  }

  &:hover &__actions,
  &__actions:focus-within {
    opacity: 1;
  }
}

.batch-tip {
  margin: 0 0 var(--sp-4);
  padding: var(--sp-2) var(--sp-3);
  background-color: var(--c-bg-subtle);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-md);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: var(--sp-5);

  &__full {
    grid-column: 1 / -1;
  }
}

:deep(.el-tree-node__content) {
  height: 30px;
  border-radius: var(--radius-sm);

  &:hover {
    background-color: var(--c-bg-subtle);
  }
}

:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: var(--c-primary-soft);
  color: var(--c-primary);

  .tree-node__icon,
  .tree-node__code {
    color: var(--c-primary);
  }
}

:deep(.app-table__toolbar) {
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
