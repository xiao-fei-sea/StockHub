<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

defineOptions({ name: 'AuthShell' })

/**
 * 认证页共享外壳（登录 / 注册）：
 * 曜石黑全屏构图 + 极光漂移背景 + 微尘粒子 + 鼠标聚光视差 + 编辑排版式品牌区 + 玻璃拟态卡片框架。
 * 表单内容通过默认插槽注入；样式统一收敛在本组件的非 scoped 样式块中，
 * 以 `.auth-page` 根类做命名空间隔离，避免与全局工具类冲突。
 */

const rootRef = ref<HTMLElement>()
const dustRef = ref<HTMLCanvasElement>()
/** 玻璃卡片容器（页面表单级错误抖动等交互目标），通过 defineExpose 提供。 */
const cardRef = ref<HTMLElement>()

defineExpose({ cardRef })

/* ============================ 品牌内容 ============================ */

/** 标题逐字揭示：offset 用于跨行连续编排入场延迟。 */
const titleLines = [
  { chars: '让每一件库存'.split(''), offset: 0 },
  { chars: '都有迹可循'.split(''), offset: 6 },
]

const features = [
  { idx: '01', title: '单据驱动库存', desc: '入库 / 出库 / 调拨 / 盘点全程留痕' },
  { idx: '02', title: '数据权限隔离', desc: '按角色与仓库范围精确控制' },
  { idx: '03', title: '实时库存预警', desc: '上下限与效期异常及时提醒' },
]

/* ============================ 指针：聚光灯 + 视差 ============================ */

const reduced =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
const finePointer =
  typeof window !== 'undefined' && window.matchMedia('(hover: hover) and (pointer: fine)').matches

let rafId = 0

function onPointerMove(e: PointerEvent) {
  if (!finePointer || reduced || rafId) return
  const nx = e.clientX / window.innerWidth
  const ny = e.clientY / window.innerHeight
  rafId = requestAnimationFrame(() => {
    rafId = 0
    const el = rootRef.value
    if (!el) return
    el.style.setProperty('--mx', `${(nx * 100).toFixed(2)}%`)
    el.style.setProperty('--my', `${(ny * 100).toFixed(2)}%`)
    el.style.setProperty('--px', ((nx - 0.5) * 2).toFixed(4))
    el.style.setProperty('--py', ((ny - 0.5) * 2).toFixed(4))
  })
}

/* ============================ 背景微尘粒子 ============================ */

interface Dust {
  x: number
  y: number
  r: number
  vy: number
  ph: number
  gold: boolean
  a: number
}

let dusts: Dust[] = []
let ctx: CanvasRenderingContext2D | null = null
let loopId = 0
let t = 0

function resizeDust() {
  const c = dustRef.value
  if (!c) return
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  const w = window.innerWidth
  const h = window.innerHeight
  c.width = Math.floor(w * dpr)
  c.height = Math.floor(h * dpr)
  c.style.width = `${w}px`
  c.style.height = `${h}px`
  ctx = c.getContext('2d')
  ctx?.setTransform(dpr, 0, 0, dpr, 0, 0)

  const count = Math.max(36, Math.min(72, Math.floor((w * h) / 24000)))
  dusts = Array.from({ length: count }, () => ({
    x: Math.random() * w,
    y: Math.random() * h,
    r: 0.5 + Math.random() * 1.3,
    vy: 0.05 + Math.random() * 0.16,
    ph: Math.random() * Math.PI * 2,
    gold: Math.random() < 0.55,
    a: 0.12 + Math.random() * 0.3,
  }))
}

function drawDust() {
  loopId = requestAnimationFrame(drawDust)
  if (!ctx || document.hidden) return
  const w = window.innerWidth
  const h = window.innerHeight
  t += 0.008
  ctx.clearRect(0, 0, w, h)
  for (const p of dusts) {
    p.y -= p.vy
    if (p.y < -6) {
      p.y = h + 6
      p.x = Math.random() * w
    }
    const x = p.x + Math.sin(t + p.ph) * 9
    const tw = 0.72 + Math.sin(t * 2.4 + p.ph) * 0.28
    ctx.beginPath()
    ctx.arc(x, p.y, p.r, 0, Math.PI * 2)
    ctx.fillStyle = p.gold
      ? `rgba(201, 168, 108, ${(p.a * tw).toFixed(3)})`
      : `rgba(242, 237, 227, ${(p.a * tw * 0.8).toFixed(3)})`
    ctx.fill()
  }
}

/* ============================ 生命周期 ============================ */

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && (e as KeyboardEvent & { isComposing?: boolean }).isComposing) {
    e.preventDefault()
  }
}

onMounted(() => {
  window.addEventListener('pointermove', onPointerMove, { passive: true })
  window.addEventListener('resize', resizeDust)
  document.addEventListener('keydown', onKeydown)

  if (!reduced) {
    resizeDust()
    drawDust()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('resize', resizeDust)
  document.removeEventListener('keydown', onKeydown)
  cancelAnimationFrame(loopId)
  cancelAnimationFrame(rafId)
})
</script>

<template>
  <div ref="rootRef" class="auth-page">
    <!-- ============ 背景层：极光漂移 / 微尘 / 鼠标聚光 / 颗粒 ============ -->
    <div class="bg" aria-hidden="true">
      <span class="bg__blob bg__blob--gold"></span>
      <span class="bg__blob bg__blob--blue"></span>
      <span class="bg__blob bg__blob--ember"></span>
      <canvas ref="dustRef" class="bg__dust"></canvas>
      <span class="bg__spot"></span>
      <span class="bg__grain"></span>
    </div>

    <div class="stage">
      <!-- ============ 左侧：编辑排版式品牌区 ============ -->
      <aside class="brand">
        <header class="brand__logo">
          <span class="brand__mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M3 7.5 12 3l9 4.5v9L12 21l-9-4.5v-9Z" />
              <path d="M3 7.5 12 12l9-4.5M12 12v9" />
            </svg>
          </span>
          <span class="brand__wordmark">智仓云 <em>SmartWMS</em></span>
        </header>

        <!-- 抽象艺术视觉：轨道环 + 悬浮晶体 + 巨型轮廓字 -->
        <div class="art" aria-hidden="true">
          <span class="art__glyph">智</span>
          <span class="art__orbit art__orbit--a"><i></i></span>
          <span class="art__orbit art__orbit--b"><i></i></span>
          <span class="art__shard"></span>
        </div>

        <div class="brand__statement">
          <p class="brand__eyebrow">SMART WAREHOUSE MANAGEMENT</p>

          <h1 class="brand__title">
            <span v-for="(line, li) in titleLines" :key="li" class="brand__line">
              <span
                v-for="(ch, ci) in line.chars"
                :key="ci"
                class="char-mask"
                aria-hidden="true"
              >
                <span class="char" :style="{ '--i': line.offset + ci }">{{ ch }}</span>
              </span>
            </span>
            <span class="sr-only">让每一件库存都有迹可循</span>
          </h1>

          <p class="brand__tagline">Every unit, accounted for.</p>
          <span class="brand__rule"></span>

          <p class="brand__desc">
            仓库、商品、入出库、调拨与盘点的全链路管理。<br />
            单据驱动、账实同步、审核留痕，库存数据始终可追溯。
          </p>
        </div>

        <ul class="brand__features">
          <li v-for="(f, i) in features" :key="f.idx" :style="{ '--i': i }">
            <span class="feature-index">{{ f.idx }}</span>
            <div>
              <strong>{{ f.title }}</strong>
              <p>{{ f.desc }}</p>
            </div>
          </li>
        </ul>

        <footer class="brand__foot">
          <span>© 2026 SmartWMS · 仓储管理系统</span>
          <span class="brand__edition">EDITION 2026 / V1.0</span>
        </footer>
      </aside>

      <!-- ============ 右侧：玻璃拟态面板（signin-panel 避开全局 .panel 工具类），卡片内容由页面注入 ============ -->
      <main class="signin-panel">
        <section ref="cardRef" class="card">
          <slot />
        </section>

        <div class="signin-panel__meta">
          <span>TLS 加密传输</span>
          <span class="meta-dot" aria-hidden="true"></span>
          <span>双令牌鉴权</span>
          <span class="meta-dot" aria-hidden="true"></span>
          <span>仅限 HTTPS / 本地</span>
        </div>
      </main>
    </div>
  </div>
</template>

<style lang="scss">
/* ============================================================
 * 认证页共享视觉体系 · Obsidian / Champagne / Ivory
 * 非 scoped 但以 .auth-page 做完整命名空间隔离（仅登录/注册页挂载该根类）
 * ============================================================ */

.auth-page {
  /* 局部设计变量（仅认证页使用，不影响后台体系） */
  --ink: #0a0b0e;
  --ink-2: #101217;
  --gold: #c9a86c;
  --gold-hi: #e6cf9f;
  --ivory: #f2ede3;
  --graphite: #7d838e;
  --line: rgba(242, 237, 227, 0.1);
  --ease-expo: cubic-bezier(0.16, 1, 0.3, 1);

  /* 指针状态（JS 写入） */
  --mx: 50%;
  --my: 38%;
  --px: 0;
  --py: 0;

  position: relative;
  height: 100dvh;
  overflow: hidden;
  background-color: var(--ink);
  color: var(--ivory);
  font-family: var(--font-sans);

  /* Element Plus 主色改为香槟金（CSS 变量向下继承） */
  --el-color-primary: var(--gold);

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
    white-space: nowrap;
  }

  /* ---------------- 背景 ---------------- */
  .bg {
    position: absolute;
    inset: 0;
    pointer-events: none;
    animation: auth-fade-in 1.4s var(--ease-expo) both;

    &__blob {
      position: absolute;
      border-radius: 50%;
      filter: blur(10px);
      will-change: transform;
    }

    &__blob--gold {
      width: 56vw;
      height: 56vw;
      left: -14vw;
      top: -18vw;
      background: radial-gradient(closest-side, rgba(201, 168, 108, 0.14), transparent 72%);
      animation: auth-drift-a 34s ease-in-out infinite alternate;
    }

    &__blob--blue {
      width: 62vw;
      height: 62vw;
      right: -18vw;
      bottom: -24vw;
      background: radial-gradient(closest-side, rgba(64, 88, 138, 0.17), transparent 72%);
      animation: auth-drift-b 42s ease-in-out infinite alternate;
    }

    &__blob--ember {
      width: 34vw;
      height: 34vw;
      right: 6vw;
      top: 12vh;
      background: radial-gradient(closest-side, rgba(201, 168, 108, 0.07), transparent 70%);
      animation: auth-drift-a 27s ease-in-out infinite alternate-reverse;
    }

    &__dust {
      position: absolute;
      inset: 0;
    }

    /* 跟随鼠标的环境聚光（JS 每帧写入变量，不做背景过渡以免渐变重绘） */
    &__spot {
      position: absolute;
      inset: 0;
      background: radial-gradient(
        620px circle at var(--mx) var(--my),
        rgba(201, 168, 108, 0.1),
        rgba(201, 168, 108, 0.03) 42%,
        transparent 68%
      );
    }

    /* 细腻胶片颗粒 */
    &__grain {
      position: absolute;
      inset: -50%;
      opacity: 0.05;
      background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='240' height='240'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.82' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
    }
  }

  /* ---------------- 舞台栅格 ---------------- */
  .stage {
    position: relative;
    z-index: 1;
    display: grid;
    grid-template-columns: minmax(0, 56fr) minmax(0, 44fr);
    height: 100%;
  }

  /* ---------------- 品牌区 ---------------- */
  .brand {
    position: relative;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    gap: var(--sp-8);
    padding: clamp(32px, 5vh, 56px) clamp(32px, 5vw, 72px);
    overflow: hidden;

    &__logo {
      display: flex;
      align-items: center;
      gap: 10px;
      animation: auth-rise 0.9s 0.1s var(--ease-expo) both;
    }

    &__mark {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 30px;
      height: 30px;
      border: 1px solid rgba(201, 168, 108, 0.55);
      border-radius: 6px;
      color: var(--gold-hi);
      transition: transform 0.5s var(--ease-expo), box-shadow 0.5s var(--ease-expo);

      &:hover {
        transform: rotate(-90deg);
        box-shadow: 0 0 24px rgba(201, 168, 108, 0.25);
      }
    }

    &__wordmark {
      display: flex;
      align-items: baseline;
      gap: 7px;
      font-size: var(--fs-md);
      font-weight: var(--fw-semibold);
      letter-spacing: 0.02em;

      em {
        font-style: normal;
        font-family: var(--font-mono);
        font-size: 11px;
        font-weight: var(--fw-regular);
        color: var(--graphite);
        letter-spacing: 0.18em;
        text-transform: uppercase;
      }
    }

    &__statement {
      position: relative;
      z-index: 2;
      max-width: 560px;
      /* 与背景反向的微视差（入场由内部元素分层错峰完成，避免动画覆盖 transform） */
      transform: translate3d(calc(var(--px) * -8px), calc(var(--py) * -6px), 0);
    }

    &__eyebrow {
      font-family: var(--font-mono);
      font-size: 11px;
      letter-spacing: 0.34em;
      color: var(--gold);
      margin-bottom: var(--sp-5);
      animation: auth-rise 0.8s 0.32s var(--ease-expo) both;
    }

    &__title {
      font-family: Georgia, 'Times New Roman', 'Noto Serif SC', 'Songti SC', 'SimSun', serif;
      font-size: clamp(38px, 4.6vw, 64px);
      font-weight: 600;
      line-height: 1.24;
      letter-spacing: 0.03em;
      color: var(--ivory);
    }

    &__line {
      display: block;
    }

    &__tagline {
      margin-top: var(--sp-4);
      font-family: Georgia, 'Times New Roman', serif;
      font-style: italic;
      font-size: clamp(15px, 1.3vw, 19px);
      letter-spacing: 0.06em;
      color: rgba(242, 237, 227, 0.52);
      animation: auth-rise 0.9s 1.05s var(--ease-expo) both;
    }

    &__rule {
      display: block;
      width: min(260px, 55%);
      height: 1px;
      margin-top: var(--sp-5);
      background: linear-gradient(90deg, var(--gold), rgba(201, 168, 108, 0));
      transform-origin: left center;
      animation: auth-grow-x 1.1s 0.95s var(--ease-expo) both;
    }

    &__desc {
      margin-top: var(--sp-5);
      font-size: var(--fs-base);
      line-height: 1.9;
      color: rgba(242, 237, 227, 0.6);
      animation: auth-rise 0.9s 1.15s var(--ease-expo) both;
    }

    &__features {
      position: relative;
      z-index: 2;
      display: flex;
      flex-direction: column;
      gap: var(--sp-3);
      max-width: 430px;

      li {
        display: flex;
        gap: var(--sp-4);
        padding-top: var(--sp-3);
        border-top: 1px solid rgba(242, 237, 227, 0.08);
        animation: auth-rise 0.8s calc(1.3s + var(--i) * 0.12s) var(--ease-expo) both;

        strong {
          display: block;
          font-size: var(--fs-base);
          font-weight: var(--fw-medium);
          color: rgba(242, 237, 227, 0.88);
          margin-bottom: 1px;
        }

        p {
          font-size: var(--fs-sm);
          color: var(--graphite);
        }
      }
    }

    &__foot {
      position: relative;
      z-index: 2;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--sp-4);
      font-size: var(--fs-xs);
      color: #5b616b;
      animation: auth-fade-in 1s 1.7s both;

      .brand__edition {
        font-family: var(--font-mono);
        letter-spacing: 0.22em;
        color: rgba(201, 168, 108, 0.55);
      }
    }
  }

  .feature-index {
    font-family: var(--font-mono);
    font-size: var(--fs-xs);
    color: var(--gold);
    padding-top: 2px;
  }

  /* 逐字揭示 */
  .char-mask {
    display: inline-block;
    overflow: hidden;
    vertical-align: bottom;
  }

  .char {
    display: inline-block;
    transform: translateY(112%);
    animation: auth-char-reveal 0.95s calc(0.34s + var(--i) * 0.055s) var(--ease-expo) both;
  }

  /* ---------------- 抽象艺术视觉 ---------------- */
  .art {
    position: absolute;
    z-index: 1;
    right: -6%;
    top: 8%;
    bottom: 8%;
    width: min(46vw, 560px);
    perspective: 900px;
    opacity: 0;
    animation: auth-fade-in 1.6s 0.5s both;
    /* 随鼠标漂移的视差组 */
    transform: translate3d(calc(var(--px) * 22px), calc(var(--py) * 16px), 0);
    will-change: transform;

    &__glyph {
      position: absolute;
      right: -8%;
      top: 50%;
      transform: translateY(-50%);
      font-family: Georgia, 'Songti SC', 'SimSun', serif;
      font-size: clamp(300px, 30vw, 460px);
      line-height: 1;
      color: transparent;
      -webkit-text-stroke: 1px rgba(242, 237, 227, 0.06);
      user-select: none;
    }

    &__orbit {
      position: absolute;
      inset: 6%;
      border: 1px solid rgba(201, 168, 108, 0.22);
      border-radius: 50%;

      i {
        position: absolute;
        top: -4px;
        left: calc(50% - 4px);
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: var(--gold-hi);
        box-shadow: 0 0 14px rgba(230, 207, 159, 0.8);
      }

      &--a {
        inset: 4%;
        animation: auth-orbit-a 52s linear infinite;
      }

      &--b {
        inset: 16%;
        border-color: rgba(242, 237, 227, 0.12);
        animation: auth-orbit-b 74s linear infinite;

        i {
          top: auto;
          bottom: -3px;
          width: 6px;
          height: 6px;
          left: calc(50% - 3px);
          background: var(--ivory);
          box-shadow: 0 0 10px rgba(242, 237, 227, 0.7);
        }
      }
    }

    &__shard {
      position: absolute;
      left: 50%;
      top: 50%;
      width: 32%;
      aspect-ratio: 1;
      margin-left: -16%;
      margin-top: -16%;
      background: linear-gradient(
        135deg,
        rgba(242, 237, 227, 0.12),
        rgba(201, 168, 108, 0.07) 46%,
        rgba(10, 11, 14, 0.5)
      );
      border: 1px solid rgba(242, 237, 227, 0.2);
      box-shadow: 0 34px 90px rgba(0, 0, 0, 0.55), inset 0 1px 0 rgba(255, 255, 255, 0.14);
      animation: auth-shard-float 12s ease-in-out infinite alternate;
    }
  }

  /* ---------------- 玻璃拟态面板 ---------------- */
  .signin-panel {
    position: relative;
    z-index: 2;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: var(--sp-5);
    padding: var(--sp-8);
    /* 面板随鼠标轻微同向位移 */
    transform: translate3d(calc(var(--px) * 7px), calc(var(--py) * 5px), 0);
  }

  .card {
    position: relative;
    width: 100%;
    max-width: 420px;
    padding: clamp(28px, 4vh, 40px);
    border: 1px solid rgba(242, 237, 227, 0.1);
    border-radius: 16px;
    background: linear-gradient(160deg, rgba(22, 24, 30, 0.72), rgba(12, 13, 17, 0.6));
    backdrop-filter: blur(22px);
    -webkit-backdrop-filter: blur(22px);
    box-shadow: 0 40px 90px rgba(0, 0, 0, 0.5), 0 4px 16px rgba(0, 0, 0, 0.3),
      inset 0 1px 0 rgba(242, 237, 227, 0.08);
    animation: auth-card-in 1s 0.5s var(--ease-expo) both;

    /* 顶部高光发丝线 */
    &::before {
      content: '';
      position: absolute;
      top: 0;
      left: 24px;
      right: 24px;
      height: 1px;
      background: linear-gradient(
        90deg,
        transparent,
        rgba(230, 207, 159, 0.55),
        transparent
      );
    }

    &__head {
      margin-bottom: var(--sp-6);

      h2 {
        font-family: Georgia, 'Times New Roman', 'Noto Serif SC', 'Songti SC', serif;
        font-size: 27px;
        font-weight: 600;
        letter-spacing: 0.02em;
        color: var(--ivory);
      }
    }

    &__index {
      display: flex;
      justify-content: space-between;
      font-family: var(--font-mono);
      font-size: 10px;
      letter-spacing: 0.3em;
      color: var(--gold);
      margin-bottom: var(--sp-4);

      em {
        font-style: normal;
        color: var(--graphite);
      }
    }

    &__sub {
      margin-top: var(--sp-2);
      font-size: var(--fs-sm);
      color: rgba(242, 237, 227, 0.52);
    }

    &__foot {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      margin-top: var(--sp-6);
      padding-top: var(--sp-5);
      border-top: 1px solid rgba(242, 237, 227, 0.08);
      font-size: var(--fs-sm);
      color: var(--graphite);
    }
  }

  /* ---------------- 卡片内表单控件（深色输入 / 标签 / 错误条 / CTA） ---------------- */
  .fld__label {
    display: block;
    font-family: var(--font-mono);
    font-size: 10px;
    letter-spacing: 0.26em;
    text-transform: uppercase;
    color: rgba(242, 237, 227, 0.45);
    margin-bottom: var(--sp-2);
    animation: auth-rise 0.75s var(--d, 0.72s) var(--ease-expo) backwards;
  }

  /* 表单项依次进入：backwards 填充 —— 入场前保持起始态，结束后交还基础样式，
     以免 fill 锁死 transform 影响按钮磁吸与错误条的显隐过渡。
     各页面通过内联 --d 逐项错峰编排延迟。 */
  .card .el-form-item {
    animation: auth-rise 0.75s var(--d, 0.78s) var(--ease-expo) backwards;
  }

  .link {
    color: var(--ivory);
    text-decoration: underline;
    text-decoration-color: rgba(201, 168, 108, 0.55);
    text-underline-offset: 4px;
    text-decoration-thickness: 1px;
    transition: color var(--dur) var(--ease-out), text-decoration-color var(--dur) var(--ease-out);

    &:hover {
      color: var(--gold-hi);
      text-decoration-color: var(--gold-hi);
    }

    &:focus-visible {
      outline: 2px solid var(--gold);
      outline-offset: 3px;
      border-radius: 3px;
    }
  }

  /* 深色输入框 */
  .card .el-input {
    --el-input-bg-color: rgba(242, 237, 227, 0.035);
    --el-input-border-color: transparent;
    --el-input-hover-border-color: transparent;
    --el-input-focus-border-color: transparent;
    --el-input-text-color: var(--ivory);
    --el-input-placeholder-color: #6d737e;
    --el-input-hover-bg-color: rgba(242, 237, 227, 0.055);
  }

  .card .el-input__wrapper {
    height: 48px;
    padding: 0 15px;
    border-radius: 10px;
    box-shadow: 0 0 0 1px rgba(242, 237, 227, 0.13) inset;
    background-color: rgba(242, 237, 227, 0.035);
    transition: box-shadow var(--dur) var(--ease-out), background-color var(--dur) var(--ease-out);

    &:hover {
      box-shadow: 0 0 0 1px rgba(242, 237, 227, 0.28) inset;
    }

    &.is-focus {
      background-color: rgba(242, 237, 227, 0.06);
      box-shadow: 0 0 0 1px rgba(201, 168, 108, 0.75) inset,
        0 0 0 4px rgba(201, 168, 108, 0.12);
    }
  }

  .card .el-input__inner {
    color: var(--ivory);
    font-size: var(--fs-base);

    &::placeholder {
      color: #6d737e;
    }

    /* 焦点光晕由外框表达，避免浏览器默认 ring 叠加 */
    &:focus-visible {
      outline: none;
    }
  }

  .card .el-input__prefix,
  .card .el-input__suffix {
    color: var(--graphite);
  }

  /* 校验错误态：柔和红光，不刺眼 */
  .card .el-form-item.is-error .el-input__wrapper {
    box-shadow: 0 0 0 1px rgba(224, 106, 95, 0.7) inset,
      0 0 0 4px rgba(224, 106, 95, 0.1);
  }

  .card .el-form-item__error {
    color: #e0897f;
    font-size: var(--fs-xs);
    padding-top: 6px;
    letter-spacing: 0.04em;
  }

  /* ---------------- 错误提示（占位防抖动） ---------------- */
  .alert {
    min-height: 18px;
    margin-top: var(--sp-3);
    font-size: var(--fs-xs);
    letter-spacing: 0.03em;
    color: #e0897f;
    opacity: 0;
    transform: translateY(-4px);
    transition: opacity var(--dur) var(--ease-out), transform var(--dur) var(--ease-out);
    /* 入场仅做透明度，结束后交还基础态，保证 .show 显隐过渡可用 */
    animation: auth-fade-in 0.6s var(--d, 1.1s) var(--ease-expo) backwards;

    &.show {
      opacity: 1;
      transform: translateY(0);
    }
  }

  /* ---------------- CTA 按钮：磁吸 + 流光 + 状态机 ---------------- */
  .auth-btn {
    --el-button-bg-color: var(--ivory);
    --el-button-border-color: transparent;
    --el-button-text-color: #0c0d10;
    --el-button-hover-bg-color: #ffffff;
    --el-button-hover-border-color: transparent;
    --el-button-hover-text-color: #0c0d10;
    --el-button-active-bg-color: #ded8cc;
    --el-button-active-border-color: transparent;
    --el-button-active-text-color: #0c0d10;
    --el-button-disabled-bg-color: rgba(242, 237, 227, 0.55);
    --el-button-disabled-text-color: rgba(12, 13, 16, 0.5);
    --el-button-disabled-border-color: transparent;

    position: relative;
    width: 100%;
    height: 48px;
    margin-top: var(--sp-4);
    border-radius: 10px;
    overflow: hidden;
    font-family: var(--font-mono);
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.24em;
    transition: transform 0.3s var(--ease-out), box-shadow 0.3s var(--ease-out),
      background-color var(--dur) var(--ease-out);
    box-shadow: 0 14px 34px rgba(0, 0, 0, 0.35);
    animation: auth-rise 0.75s var(--d, 1.16s) var(--ease-expo) backwards;

    &:hover {
      box-shadow: 0 18px 44px rgba(0, 0, 0, 0.45), 0 0 0 1px rgba(201, 168, 108, 0.35);
    }

    &:active {
      transform: scale(0.985) !important;
    }

    &:focus-visible {
      outline: 2px solid var(--gold);
      outline-offset: 3px;
    }

    /* 覆盖全局 element-override 的主色 hover/active，保持象牙白 CTA */
    &.el-button--primary:not(.is-plain):not(.is-link):not(.is-text):not(.is-disabled) {
      background-color: var(--ivory);
      border-color: transparent;
      color: #0c0d10;

      &:hover {
        background-color: #ffffff;
        border-color: transparent;
        color: #0c0d10;
      }

      &:active {
        background-color: #ded8cc;
        border-color: transparent;
        color: #0c0d10;
      }
    }

    /* 加载禁用态：保持象牙白，仅降饱和提示进行中 */
    &.is-disabled {
      background-color: rgba(242, 237, 227, 0.85);
      border-color: transparent;
      color: rgba(12, 13, 16, 0.55);
      cursor: progress;
    }

    /* 悬停时横掠而过的香槟流光 */
    &::after {
      content: '';
      position: absolute;
      inset: 0;
      background: linear-gradient(
        105deg,
        transparent 32%,
        rgba(201, 168, 108, 0.5) 50%,
        transparent 68%
      );
      transform: translateX(-130%);
      transition: transform 0.75s var(--ease-expo);
      pointer-events: none;
    }

    &:hover::after {
      transform: translateX(130%);
    }

    .el-button__content,
    &__state {
      position: relative;
      z-index: 1;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
    }

    &__arrow {
      transition: transform 0.35s var(--ease-expo);
    }

    &:hover &__arrow {
      transform: translateX(4px);
    }

    &__state {
      width: 100%;
    }
  }

  /* 加载态：细金环旋转 */
  .spin {
    width: 15px;
    height: 15px;
    border: 1.5px solid rgba(12, 13, 16, 0.25);
    border-top-color: #0c0d10;
    border-radius: 50%;
    animation: auth-spin 0.7s linear infinite;
  }

  /* ---------------- 面板底部元信息 ---------------- */
  .signin-panel__meta {
    display: flex;
    align-items: center;
    gap: 10px;
    font-family: var(--font-mono);
    font-size: 10px;
    letter-spacing: 0.18em;
    color: #5b616b;
    animation: auth-fade-in 1s 1.6s both;

    .meta-dot {
      width: 3px;
      height: 3px;
      border-radius: 50%;
      background: rgba(201, 168, 108, 0.6);
    }
  }

  /* ============================ 关键帧 ============================ */
  @keyframes auth-fade-in {
    from {
      opacity: 0;
    }
    to {
      opacity: 1;
    }
  }

  @keyframes auth-rise {
    from {
      opacity: 0;
      transform: translateY(22px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  @keyframes auth-char-reveal {
    from {
      transform: translateY(112%);
    }
    to {
      transform: translateY(0);
    }
  }

  @keyframes auth-grow-x {
    from {
      transform: scaleX(0);
    }
    to {
      transform: scaleX(1);
    }
  }

  @keyframes auth-card-in {
    from {
      opacity: 0;
      transform: translateY(34px) scale(0.985);
    }
    to {
      opacity: 1;
      transform: translateY(0) scale(1);
    }
  }

  @keyframes auth-spin {
    to {
      transform: rotate(360deg);
    }
  }

  @keyframes auth-drift-a {
    from {
      transform: translate3d(0, 0, 0) scale(1);
    }
    to {
      transform: translate3d(6vw, 4vh, 0) scale(1.12);
    }
  }

  @keyframes auth-drift-b {
    from {
      transform: translate3d(0, 0, 0) scale(1.08);
    }
    to {
      transform: translate3d(-5vw, -5vh, 0) scale(1);
    }
  }

  @keyframes auth-orbit-a {
    from {
      transform: rotateX(66deg) rotateZ(0deg);
    }
    to {
      transform: rotateX(66deg) rotateZ(360deg);
    }
  }

  @keyframes auth-orbit-b {
    from {
      transform: rotateX(58deg) rotateZ(30deg) rotateY(12deg) rotateZ(0deg);
    }
    to {
      transform: rotateX(58deg) rotateZ(30deg) rotateY(12deg) rotateZ(-360deg);
    }
  }

  @keyframes auth-shard-float {
    from {
      transform: translateY(10px) rotate(45deg);
    }
    to {
      transform: translateY(-14px) rotate(45deg);
    }
  }

  /* ============================ 矮屏桌面 ============================ */
  @media (max-height: 760px) and (min-width: 1025px) {
    .brand {
      &__features {
        display: none;
      }

      &__title {
        font-size: clamp(34px, 4vw, 46px);
      }

      &__desc {
        line-height: 1.7;
      }
    }
  }

  /* ============================ 平板 / 手机：重新构图 ============================ */
  @media (max-width: 1024px) {
    height: auto;
    min-height: 100dvh;
    overflow-y: auto;
    overflow-x: hidden;

    .stage {
      grid-template-columns: 1fr;
      min-height: 100dvh;
    }

    /* 纯装饰元素在小屏移除，构图改为：品牌头 → 标题 → 表单 */
    .art,
    .brand__features,
    .brand__edition {
      display: none;
    }

    .brand {
      justify-content: flex-start;
      gap: var(--sp-6);
      padding: var(--sp-8) var(--sp-6) 0;
      overflow: visible;

      &__statement {
        transform: none;
        text-align: left;
      }

      &__title {
        font-size: clamp(32px, 8.5vw, 46px);
      }

      &__rule {
        width: 120px;
      }

      &__desc {
        font-size: var(--fs-sm);
        line-height: 1.8;

        br {
          display: none;
        }
      }

      &__foot {
        display: none;
      }
    }

    .signin-panel {
      transform: none;
      justify-content: flex-start;
      padding: var(--sp-6) var(--sp-5) var(--sp-10);
    }

    .card {
      max-width: 480px;
      animation-delay: 0.45s;
    }
  }

  @media (max-width: 560px) {
    .brand {
      padding: var(--sp-6) var(--sp-5) 0;

      &__tagline {
        font-size: 14px;
      }

      &__desc {
        display: none;
      }
    }

    .signin-panel {
      padding: var(--sp-5) var(--sp-4) var(--sp-8);
    }

    .card {
      padding: 26px 20px;
      border-radius: 14px;

      &__head h2 {
        font-size: 23px;
      }
    }

    .signin-panel__meta {
      flex-wrap: wrap;
      justify-content: center;
      row-gap: 6px;
    }

    .card .el-input__wrapper {
      height: 46px;
    }

    .auth-btn {
      height: 46px;
    }
  }

  /* ============================ 降低动态效果 ============================ */
  @media (prefers-reduced-motion: reduce) {
    *,
    *::before,
    *::after {
      animation-duration: 0.01ms !important;
      animation-delay: 0s !important;
      transition-duration: 0.01ms !important;
    }

    .bg__spot {
      transition: none;
    }
  }
}
</style>

<style lang="scss">
/* 认证页期间画布/回弹区域同步为曜石黑，避免露白（非 scoped，作用于 html/body） */
html:has(.auth-page),
body:has(.auth-page) {
  background-color: #0a0b0e;
}
</style>
