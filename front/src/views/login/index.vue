<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Check, Lock, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/store/user'
import AuthShell from '@/components/AuthShell.vue'

defineOptions({ name: 'LoginPage' })

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/* ============================ 状态 ============================ */

type Status = 'idle' | 'loading' | 'success' | 'error'

const formRef = ref<FormInstance>()
/** el-button 是组件，模板 ref 拿到组件实例，需取其根节点 DOM */
const btnRef = ref<{ $el?: HTMLElement } | null>(null)
const shellRef = ref<InstanceType<typeof AuthShell> | null>(null)

const status = ref<Status>('idle')
const errMsg = ref('')
const remember = ref(false)

const form = reactive({ username: '', password: '' })

const REMEMBER_KEY = 'smartwms.login.remember'
const ACCOUNT_KEY = 'smartwms.login.account'

/* ============================ 校验 ============================ */

function validateAccount(_rule: unknown, value: unknown, cb: (e?: Error) => void) {
  const v = String(value ?? '').trim()
  if (!v) return cb(new Error('请输入账号'))
  if (v.includes('@') && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v)) return cb(new Error('邮箱格式不正确'))
  cb()
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { validator: validateAccount, trigger: 'blur' },
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

/* ============================ 提交 ============================ */

/** 从拦截器的各类 rejection（业务 response / AxiosError）中提取可读文案。 */
function pickError(err: unknown): string {
  const e = err as { data?: { message?: string }; response?: { data?: { message?: string } } } | null
  const payload = e?.response?.data ?? e?.data
  if (payload?.message) return payload.message
  if (payload) return '账号或密码错误，请重试'
  return '网络连接异常，请稍后重试'
}

const reduced =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

/** 错误抖动：走 WAAPI，避免与入场动画的 CSS animation 相互重放。 */
function triggerShake() {
  const card = shellRef.value?.cardRef
  if (reduced || !card) return
  card.animate(
    [
      { transform: 'translateX(0)' },
      { transform: 'translateX(-8px)', offset: 0.2 },
      { transform: 'translateX(7px)', offset: 0.4 },
      { transform: 'translateX(-5px)', offset: 0.6 },
      { transform: 'translateX(3px)', offset: 0.8 },
      { transform: 'translateX(0)' },
    ],
    { duration: 460, easing: 'cubic-bezier(0.16, 1, 0.3, 1)' },
  )
}

function clearError() {
  if (status.value === 'error') {
    status.value = 'idle'
    errMsg.value = ''
  }
}

watch(() => form.username, clearError)
watch(() => form.password, clearError)

async function handleSubmit() {
  if (status.value === 'loading' || status.value === 'success') return
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const account = form.username.trim()
  try {
    if (remember.value) {
      localStorage.setItem(REMEMBER_KEY, '1')
      localStorage.setItem(ACCOUNT_KEY, account)
    } else {
      localStorage.removeItem(REMEMBER_KEY)
      localStorage.removeItem(ACCOUNT_KEY)
    }
  } catch {
    // 隐私模式下 localStorage 不可用，不影响登录
  }

  status.value = 'loading'
  errMsg.value = ''
  try {
    await userStore.login({ username: account, password: form.password }, { silent: true })
    status.value = 'success'
    await new Promise((r) => window.setTimeout(r, 640))
    const redirect = String(route.query.redirect ?? '/')
    await router.replace(redirect.startsWith('/login') ? '/' : redirect)
  } catch (err) {
    status.value = 'error'
    errMsg.value = pickError(err)
    triggerShake()
  } finally {
    if (status.value === 'loading') status.value = 'idle'
  }
}

/* ============================ 次要入口 ============================ */

function onForgot() {
  ElMessage.info('请联系系统管理员为你重置登录密码')
}
function onCreateAccount() {
  router.push('/register')
}

/* ============================ 指针：按钮磁吸 ============================ */

const finePointer =
  typeof window !== 'undefined' && window.matchMedia('(hover: hover) and (pointer: fine)').matches

function btnEl(): HTMLElement | null {
  const v = btnRef.value
  if (!v) return null
  return v instanceof HTMLElement ? v : (v.$el ?? null)
}

/** 按钮磁吸：光标进入感应区后向光标轻微位移，离开时回弹。 */
function onBtnMove(e: MouseEvent) {
  if (!finePointer || reduced) return
  const el = btnEl()
  if (!el) return
  const r = el.getBoundingClientRect()
  const dx = (e.clientX - (r.left + r.width / 2)) / (r.width / 2)
  const dy = (e.clientY - (r.top + r.height / 2)) / (r.height / 2)
  el.style.transform = `translate3d(${(dx * 5).toFixed(2)}px, ${(dy * 4).toFixed(2)}px, 0)`
}

function onBtnLeave() {
  const el = btnEl()
  if (el) el.style.transform = ''
}

/* ============================ 生命周期 ============================ */

onMounted(() => {
  // 注册成功后携带的账号预填优先，其次回填“记住账号”
  const preset = String(route.query.username ?? '')
  if (preset) {
    form.username = preset
    return
  }
  try {
    remember.value = localStorage.getItem(REMEMBER_KEY) === '1'
    if (remember.value) form.username = localStorage.getItem(ACCOUNT_KEY) ?? ''
  } catch {
    // ignore
  }
})
</script>

<template>
  <AuthShell ref="shellRef">
    <!-- ============ 玻璃卡片：登录表单（卡片框架 / 背景 / 品牌区由 AuthShell 提供） ============ -->
    <header class="card__head">
        <p class="card__index"><span>SIGN IN</span><em>ACCOUNT ACCESS</em></p>
        <h2>欢迎回来</h2>
        <p class="card__sub">登录以继续管理你的仓库与库存</p>
      </header>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        class="login-form"
        status-icon
        @keyup.enter="handleSubmit"
      >
        <p class="fld__label" :style="{ '--d': '0.72s' }">账号 / 邮箱</p>
        <el-form-item prop="username" :style="{ '--d': '0.78s' }">
          <el-input
            v-model="form.username"
            placeholder="name@company.com 或用户名"
            autocomplete="username"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <p class="fld__label" :style="{ '--d': '0.86s' }">密码</p>
        <el-form-item prop="password" :style="{ '--d': '0.92s' }">
          <el-input
            v-model="form.password"
            placeholder="请输入密码"
            type="password"
            autocomplete="current-password"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <div class="opts">
          <button
            type="button"
            class="remember"
            role="switch"
            :aria-checked="remember"
            @click="remember = !remember"
          >
            <span class="remember__box">
              <el-icon><Check /></el-icon>
            </span>
            <span class="remember__text">记住账号</span>
          </button>

          <a href="#" class="link" @click.prevent="onForgot">忘记密码？</a>
        </div>

        <p class="alert" :class="{ show: status === 'error' }" role="alert">
          {{ errMsg }}
        </p>

        <el-button
          ref="btnRef"
          class="auth-btn"
          type="primary"
          size="large"
          :disabled="status === 'loading'"
          :aria-busy="status === 'loading'"
          @click="handleSubmit"
          @mousemove="onBtnMove"
          @mouseleave="onBtnLeave"
        >
          <span v-if="status === 'loading'" class="auth-btn__state">
            <span class="spin" aria-hidden="true"></span>VERIFYING…
          </span>
          <span v-else-if="status === 'success'" class="auth-btn__state">
            <el-icon><Check /></el-icon>WELCOME BACK
          </span>
          <span v-else class="auth-btn__state">
            SIGN IN
            <el-icon class="auth-btn__arrow"><ArrowRight /></el-icon>
          </span>
        </el-button>
      </el-form>

      <footer class="card__foot">
        <span>还没有账号？</span>
        <a href="#" class="link" @click.prevent="onCreateAccount">创建账号</a>
      </footer>
  </AuthShell>
</template>

<style scoped lang="scss">
/* 登录页专属样式：共享视觉体系见 AuthShell（.auth-page 命名空间） */

.login-form {
  .opts {
    animation: auth-rise 0.75s 1.04s var(--ease-expo) backwards;
  }

  :deep(.el-form-item) {
    margin-bottom: var(--sp-5);
  }
}

/* ---------------- 记住我 / 忘记密码 ---------------- */
.opts {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--sp-1);
}

.remember {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  color: rgba(242, 237, 227, 0.62);
  font-size: var(--fs-sm);

  &__box {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    border: 1px solid rgba(242, 237, 227, 0.3);
    border-radius: 4px;
    color: transparent;
    transition: all var(--dur) var(--ease-out);

    .el-icon {
      font-size: 11px;
    }
  }

  &:hover &__box {
    border-color: var(--gold);
  }

  &[aria-checked='true'] &__box {
    background: var(--gold);
    border-color: var(--gold);
    color: var(--ink);
  }

  &[aria-checked='true'] .remember__text {
    color: var(--ivory);
  }
}
</style>
