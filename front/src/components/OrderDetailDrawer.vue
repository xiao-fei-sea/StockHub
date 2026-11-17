<script setup lang="ts">
import { Close } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import AppTable, { type TableColumn } from '@/components/AppTable.vue'

/** 单据基本信息字段。 */
export interface DetailField {
  label: string
  value?: string | number | null
}

/**
 * 单据详情抽屉（入库/出库/调拨/盘点等业务单据通用）：
 * 头部单据号 + 状态 → 基本信息区 → 明细表格 → 自定义区（审核/过账记录）→ 底部操作。
 * 底部按钮由父页面通过 #footer 插槽按状态自行控制显隐（交互设计 §2 按钮矩阵）。
 */
defineOptions({ name: 'OrderDetailDrawer' })

withDefaults(
  defineProps<{
    visible: boolean
    title: string
    /** 单据状态 0-5，显示 StatusTag */
    status?: number | null
    /** 基本信息字段（自动按两列栅格排布） */
    fields?: DetailField[]
    /** 单据明细行 */
    items?: Record<string, unknown>[]
    itemColumns?: TableColumn[]
    loading?: boolean
  }>(),
  {
    status: null,
    fields: () => [],
    items: () => [],
    itemColumns: () => [],
    loading: false,
  },
)

const emit = defineEmits<{ 'update:visible': [value: boolean] }>()
</script>

<template>
  <el-drawer
    :model-value="visible"
    size="720px"
    :with-header="false"
    class="order-drawer"
    @update:model-value="emit('update:visible', $event)"
  >
    <!-- 头部 -->
    <header class="drawer-head">
      <div class="drawer-head__main">
        <div class="drawer-head__title">
          <h3>{{ title }}</h3>
          <StatusTag v-if="status !== null" :status="status" />
        </div>
        <p class="drawer-head__slot"><slot name="head-extra" /></p>
      </div>
      <button class="drawer-head__close" type="button" aria-label="关闭" @click="emit('update:visible', false)">
        <el-icon><Close /></el-icon>
      </button>
    </header>

    <div v-loading="loading" class="drawer-body">
      <!-- 基本信息 -->
      <section v-if="fields.length" class="drawer-section">
        <h4 class="block-title">基本信息</h4>
        <dl class="detail-grid">
          <div v-for="f in fields" :key="f.label" class="detail-grid__item">
            <dt>{{ f.label }}</dt>
            <dd>
              <slot name="field" :field="f">
                <span :class="{ 'text-ellipsis': true }">{{ f.value ?? '—' }}</span>
              </slot>
            </dd>
          </div>
        </dl>
      </section>

      <!-- 单据明细 -->
      <section v-if="itemColumns.length" class="drawer-section">
        <h4 class="block-title">单据明细</h4>
        <AppTable
          :columns="itemColumns"
          :data="items"
          :pagination="false"
          size="small"
          row-key="lineNo"
        />
      </section>

      <!-- 扩展区（审核/过账/确认记录等） -->
      <section class="drawer-section">
        <slot name="extra" />
      </section>
    </div>

    <!-- 底部操作（按单据状态由父页面控制） -->
    <footer v-if="$slots.footer" class="drawer-foot">
      <slot name="footer" />
    </footer>
  </el-drawer>
</template>

<style scoped lang="scss">
.drawer-head {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-4) var(--sp-5);
  background-color: var(--c-bg-surface);
  border-bottom: 1px solid var(--c-border);

  &__title {
    display: flex;
    align-items: center;
    gap: var(--sp-3);

    h3 {
      font-size: var(--fs-md);
      font-weight: var(--fw-semibold);
      color: var(--c-text);
      letter-spacing: -0.01em;
    }
  }

  &__slot {
    margin-top: 4px;
    font-size: var(--fs-xs);
    color: var(--c-text-3);
  }

  &__close {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border: none;
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--c-text-3);
    cursor: pointer;
    flex-shrink: 0;

    &:hover {
      background-color: var(--c-bg-muted);
      color: var(--c-text);
    }
  }
}

.drawer-body {
  padding: var(--sp-5);
}

.drawer-section {
  margin-bottom: var(--sp-6);

  &:last-child {
    margin-bottom: 0;
  }
}

/* 基本信息栅格：两列起步，随抽屉宽度自适应 */
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sp-3) var(--sp-6);
  margin: 0;

  &__item {
    min-width: 0;

    dt {
      font-size: var(--fs-xs);
      color: var(--c-text-3);
      margin-bottom: 2px;
    }

    dd {
      margin: 0;
      font-size: var(--fs-sm);
      color: var(--c-text);
      word-break: break-all;
    }
  }
}

.drawer-foot {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--sp-2);
  padding: var(--sp-3) var(--sp-5);
  background-color: var(--c-bg-surface);
  border-top: 1px solid var(--c-border);
}

@media (min-width: 721px) {
  .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
