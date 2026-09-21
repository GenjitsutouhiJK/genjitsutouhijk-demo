/**
 * 路由表
 *
 * 每一行 = 一条「网址 -> 页面组件」的对应关系。
 *
 * 以前 App.vue 里写死了 <LoginView />，不管地址是什么都只显示登录页。
 * 有了路由之后，地址栏变成 /login、/home，页面跟着换，浏览器前进/后退也能用。
 *
 * meta 里的两个标记：
 *   requiresAuth —— 必须登录才能看。没登录会被守卫送去登录页。
 *   guestOnly    —— 只给"还没登录"的人看。已经登录了还去 /login 没意义，直接回主页。
 */
import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import HomeView from '../views/HomeView.vue'
import { isLoggedIn } from '../utils/session'

const routes = [
  // 访问根路径时直接重定向到登录页
  { path: '/', redirect: '/login' },

  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { guestOnly: true },
  },

  {
    path: '/register',
    name: 'register',
    component: RegisterView,
    meta: { guestOnly: true },
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
 * 全局前置守卫：每次跳转之前都会先跑一遍这个函数，用来拦截不该进的页面。
 * 返回 undefined / true = 放行；返回一个路由对象 = 改去那个路由。
 */
router.beforeEach((to) => {
  // 方向一：需要登录的页面，没登录就送去登录页
  if (to.meta.requiresAuth && !isLoggedIn()) {
    return { name: 'login' }
  }

  // 方向二：只给未登录用户的页面（登录页 / 注册页），已登录就送回主页。
  // 注意顺序：两条都要写。少了方向二的话，登录之后手点浏览器"后退"
  // 会又回到登录页，看起来像是"被登出了"，其实只是没拦。
  if (to.meta.guestOnly && isLoggedIn()) {
    return { name: 'home' }
  }
})

export default router
