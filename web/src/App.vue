<script setup lang="ts">
/**
 * 根组件：只负责"页面出口"这一层职责。
 * 具体显示哪个页面由路由决定（见 src/router/index.ts）：
 *   /login    -> views/LoginView.vue
 *   /register -> views/RegisterView.vue
 *   /home     -> views/HomeView.vue
 * RouterView 是 vue-router 注册的全局组件，会自动渲染当前路由对应的页面。
 *
 * ================== 页面切换过渡 ==================
 *
 * v-slot 解构出"当前路由对应的组件"，再把它交给 <Transition>：
 * Transition 会在旧组件消失 / 新组件出现的那几个瞬间，往元素上挂一组 CSS 类
 * （.route-enter-from / .route-enter-active / .route-leave-to …），
 * 样式写在 styles/main.css 的「页面切换过渡」一节。
 *
 * mode="out-in" 不能省：不加的话新旧页面会同时留在 DOM 里，
 * 而两个都是 min-height:100% 的整页外壳，叠在一起会出现高度抖动和闪白。
 * out-in 的语义是"旧的先退完，新的再进"，顺序确定。
 *
 * 为什么不在这里写 <KeepAlive>：
 * 每个页面进去时都要重新向服务端核验一次身份（HomeView 的闸门就是干这个的），
 * 缓存住反而会让"重新核验"这件事不成立。
 *
 * ============================================================
 * ★★★ 铁律：被 <Transition> 包住的页面组件必须是"单根" ★★★
 *
 * 也就是说 —— **<template> 顶层不能有注释，也不能有第二个兄弟节点。**
 *
 * 为什么：dev 模式下 Vue 会保留模板里的 HTML 注释。顶层一旦有注释，
 * 编译结果就成了 [注释, AppShell] 这样的多根，组件根退化成 Fragment；
 * 而 <Transition> 只能作用在单个根节点上 —— 挂在 Fragment 上的过渡钩子
 * **不会向下传给真正的 DOM 元素**。
 *
 * 于是 out-in 模式会这样崩掉（2026-09-28 真实踩过）：
 *   1. mode="out-in" 先把旧页面标记为 isLeaving，渲染一个空注释占位；
 *   2. 它靠"旧页面过渡结束"的回调（afterLeave）把 isLeaving 复位，再挂新页面；
 *   3. 钩子没传到 DOM 上 → 旧页面被**直接**摘掉，那个回调永远不会来；
 *   4. isLeaving 永远停在 true → 之后每一次渲染都只输出空占位
 *      → **整页永久空白，刷新一下又正常，控制台一个字都不报。**
 *
 * 所以：解释性的注释一律写在 <script> 里（就现在这样），不要写在模板顶层。
 * ============================================================
 */
</script>

<template>
  <RouterView v-slot="{ Component }">
    <Transition name="route" mode="out-in">
      <component :is="Component" />
    </Transition>
  </RouterView>
</template>
