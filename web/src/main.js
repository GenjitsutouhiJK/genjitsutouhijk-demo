import { createApp } from 'vue'
import './styles/main.css'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './utils/request'
import { setNotice } from './utils/session'

/**
 * 注册"会话失效"的处理动作
 *
 * 为什么放在这里，而不是写死在 utils/request.js 里？
 *   request.js 是最底层的一层（谁都用它），router 是上层（router 会间接用到请求）。
 *   让底层去 import 上层，就叫**循环依赖**。这里由"组装方"（入口文件）
 *   负责把两者接起来 —— 依赖关系始终是从上往下的，看代码时不会绕圈。
 *
 * 具体动作：token 过期/无效时（后端返回 1005 / 1006 / 1007），
 * 把用户送回登录页。session 已经在 request.js 里清掉了。
 *
 * 一个细节：如果此时**已经在登录页**，就不再跳了。
 * 否则会触发一次多余的路由跳转（虽然 vue-router 会去重，但会产生一条
 * "Avoided redundant navigation" 的警告，看着烦）。
 */
setUnauthorizedHandler((reason) => {
  // 先把"为什么失效"记下来，登录页会读出来显示。
  // 顺序不能反：一定要在跳转**之前**写，否则登录页已经渲染完了，提示就漏了。
  setNotice(reason)

  if (router.currentRoute.value.name !== 'login') {
    router.push({ name: 'login' })
  }
})

// .use(router) 把路由装到整个应用上，之后 <RouterView /> 才能工作
createApp(App).use(router).mount('#app')
