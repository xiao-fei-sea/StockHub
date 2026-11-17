/// <reference types="vite/client" />

/**
 * Vite 处理的资源模块声明：
 * vue-tsc / Volar 可原生解析 .vue，但 IDE 内置 TS 语言服务需要该通配声明，
 * 否则 import Xxx from './Xxx.vue' 会报 "Cannot find module"。
 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, never>, Record<string, never>, any>
  export default component
}
