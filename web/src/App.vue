<script setup>
// 根组件：只负责"页面出口"这一层职责。
// 具体显示哪个页面由路由决定（见 src/router/index.js）：
//   /login -> views/LoginView.vue
//   /home  -> views/HomeView.vue
// RouterView 是 vue-router 注册的全局组件，会自动渲染当前路由对应的页面。
</script>

<template>
  <RouterView />
</template>
