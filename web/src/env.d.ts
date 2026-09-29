/// <reference types="vite/client" />

/**
 * 让 TypeScript 认识 .vue 文件。
 *
 * 没有这段声明时，任何 `import AppShell from './AppShell.vue'` 在 .ts 文件里
 * 都会报「找不到模块」—— 因为 .vue 不是 TS 原生认识的扩展名，
 * 编译器不知道它导出了什么。Vite 在打包时能处理它，但类型检查这一层需要一句声明。
 *
 * 现在 .vue 组件还是 JS 写法，所以这里给的是宽松的兜底类型。
 * 等以后把组件迁到 `<script setup lang="ts">`，vue-tsc 会直接读组件自身的类型，
 * 这段声明的作用就退化成"只覆盖非 TS 的 .vue 文件"。
 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'

  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}
