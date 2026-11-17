<script setup lang="ts">
import { ref, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { StatusTag } from '@/components'
import type { OperLog } from '@/types/entity'
import { enableStatusInfo } from '@/utils/dict'
import { enableTagStatus } from '@/views/base/components/status'
import { getLogDetail } from '@/api/log'

/** 日志详情抽屉：脱敏参数 / 响应结果 / 耗时 / IP 等审计信息（打开需 sys:log:query）。 */
defineOptions({ name: 'LogDetailDrawer' })

const props = withDefaults(
  defineProps<{
    visible: boolean
    logId: number | null
  }>(),
  { logId: null },
)

const emit = defineEmits<{ 'update:visible': [value: boolean] }>()

const loading = ref(false)
const detail = ref<OperLog | null>(null)

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) return
    detail.value = null
    if (props.logId == null) return
    loading.value = true
    try {
      detail.value = await getLogDetail(props.logId)
    } finally {
      loading.value = false
    }
  },
)

/** JSON 美化展示（非 JSON 原样输出，空值占位 —）。 */
function pretty(raw?: string | null): string {
  if (!raw) return '—'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="日志详情"
    size="640px"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading" class="log-drawer">
      <template v-if="detail">
        <div class="log-drawer__head">
          <div class="log-drawer__head-main">
            <div class="log-drawer__title">
              <h3>{{ detail.title }}</h3>
              <StatusTag
                :status="enableTagStatus(detail.status)"
                :label="enableStatusInfo(detail.status).value === 1 ? '成功' : '失败'"
                size="small"
              />
            </div>
            <p class="t-xs t-3 log-drawer__sub">
              {{ detail.module }} · {{ detail.operTime || '—' }}
            </p>
          </div>
          <button
            class="log-drawer__close"
            type="button"
            aria-label="关闭"
            @click="emit('update:visible', false)"
          >
            <el-icon><Close /></el-icon>
          </button>
        </div>

        <el-descriptions class="log-drawer__desc" :column="2" border size="small">
          <el-descriptions-item label="操作模块" :span="2">{{ detail.module }}</el-descriptions-item>
          <el-descriptions-item label="操作人">
            {{ detail.username || '系统' }}
          </el-descriptions-item>
          <el-descriptions-item label="用户 ID">
            {{ detail.userId ?? '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="操作时间">{{ detail.operTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="耗时">
            <span class="num">{{ detail.costTime ?? 0 }} ms</span>
          </el-descriptions-item>
          <el-descriptions-item label="来源 IP">{{ detail.ip || '—' }}</el-descriptions-item>
          <el-descriptions-item label="请求方法">
            <span class="mono">{{ detail.requestMethod || '—' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="请求路径" :span="2">
            <span class="mono log-drawer__wrap">{{ detail.requestUrl || '—' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="后端方法" :span="2">
            <span class="mono log-drawer__wrap">{{ detail.method || '—' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="detail.errorMsg"
          type="error"
          :closable="false"
          show-icon
          :title="`执行失败：${detail.errorMsg}`"
          class="log-drawer__alert"
        />

        <section class="log-drawer__sec">
          <h4 class="log-drawer__sec-title">
            请求参数
            <span class="t-xs t-3">敏感字段（密码 / 令牌等）已由服务端脱敏为 ******</span>
          </h4>
          <pre class="log-drawer__code">{{ pretty(detail.requestParams) }}</pre>
        </section>

        <section class="log-drawer__sec">
          <h4 class="log-drawer__sec-title">响应结果</h4>
          <pre class="log-drawer__code">{{ pretty(detail.responseResult) }}</pre>
        </section>

        <p v-if="detail.status === 0 && !detail.errorMsg" class="t-xs t-3 log-drawer__note">
          本次执行失败，未记录错误信息，请结合服务端日志排查。
        </p>
      </template>
      <el-empty v-else-if="!loading" description="日志详情不存在或已被清理" />
    </div>

    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.log-drawer {
  &__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--sp-3);
    margin-bottom: var(--sp-4);
  }

  &__title {
    display: flex;
    align-items: center;
    gap: var(--sp-3);

    h3 {
      margin: 0;
      font-size: var(--fs-md);
      font-weight: var(--fw-semibold);
      color: var(--c-text);
    }
  }

  &__sub {
    margin: 6px 0 0;
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

  &__desc {
    margin-bottom: var(--sp-4);
  }

  &__wrap {
    word-break: break-all;
  }

  &__alert {
    margin-bottom: var(--sp-4);
  }

  &__sec {
    margin-bottom: var(--sp-4);
  }

  &__sec-title {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: var(--sp-3);
    margin: 0 0 var(--sp-2);
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    color: var(--c-text-2);
  }

  &__code {
    margin: 0;
    padding: var(--sp-3);
    max-height: 240px;
    overflow: auto;
    font-family: var(--font-mono);
    font-size: var(--fs-xs);
    line-height: 1.7;
    color: var(--c-text-2);
    background-color: var(--c-bg-subtle);
    border: 1px solid var(--c-border);
    border-radius: var(--radius-md);
    white-space: pre-wrap;
    word-break: break-all;
  }

  &__note {
    margin: calc(var(--sp-2) * -1) 0 0;
  }
}
</style>
