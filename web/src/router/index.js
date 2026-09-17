/**
 * 路由表
 *
 * 每一行 = 一条「网址 -> 页面组件」的对应关系。
 *
 * 以前 App.vue 里写死了 <LoginView />，不管地址是什么都只显示登录页。
 * 有了路由之后，地址栏变成 /login、/home，页面跟着换，浏览器前进/后退也能用。
 */
import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import { isLoggedIn } from '../utils/session'

const routes = [
  // 访问根路径时直接重定向到登录页
  { path: '/', redirect: '/login' },

  {
    path: '/login',
    name: 'login',
    component: LoginView,
  },

  {
    path: '/home',
    name: 'home',
    component: HomeView,
    // meta 是给路由附加的自定义信息。
    // 这里用 requiresAuth 标记"这个页面必须登录后才能看"，下面守卫会读它。
    meta: { requiresAuth: true },
  },

  // 兜底：地址写错了就回登录页（放在最后，因为路由是按顺序匹配的）
  { path: '/:pathMatch(.*)*', redirect: '/login' },
]

const router = createRouter({
  /**
   * createWebHistory() = 地址栏是干净的 /home，不带 #。
   * 代价是服务器必须把所有路径都回退到 index.html —— 本地开发时 Vite 已经帮你做好了。
   * （将来如果部署到 GitHub Pages 这类纯静态托管，要么改成 createWebHashHistory()，
   *   要么配置 404.html 回退，到时候我们再处理。）
   */
  history: createWebHistory(),
  routes,
})

/**
 * 全局前置守卫：每次跳转之前都会先跑一遍这个函数，用来拦截"没登录就想进 /home"的情况。
 * 返回 undefined / true = 放行；返回一个路由对象 = 改去那个路由。
 */
router.beforeEach((to) => {
  if (to.meta.requiresAuth && !isLoggedIn()) {
    return { name: 'login' }
  }
})

export default router
