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
import { clearSession, isLoggedIn, isTokenExpired, setNotice } from '../utils/session'

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
  /*
   * 第 0 步：先把"本地就已经能判定坏掉"的会话处理掉 —— 过期的凭证。
   *
   * 为什么要单独这一步？因为判断"有没有票"和判断"票还有没有效"是两个问题，
   * 而 isLoggedIn() 只回答了第一个：它看的是 sessionStorage 里躺没躺着那张票。
   *
   * 少了这一步，流程会是这样：
   *   票过期了 → isLoggedIn() 仍然是 true → 守卫放行 /home
   *   → 主页把面板画出来 → 自己去问后端 → 拿到 1006 → 才把人送回登录页
   * 结果就是"面板闪一下再被弹走"。而这个闪烁其实完全可以避免：
   * payload 里的 exp 是明文，本地就能读出来（见 utils/session.js）。
   *
   * ⚠️ 这里只处理"过期"这一种。签名被篡改、账号被删这类只有服务端知道的问题，
   *    仍然靠主页那次核验兜底（见 HomeView.vue 的 verify()）。
   */
  if (isLoggedIn() && isTokenExpired()) {
    clearSession()
    // 顺手记下原因，登录页会把它显示出来。不记的话用户只知道自己"被踢回来了"。
    setNotice('登录已过期，请重新登录')
  }

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
