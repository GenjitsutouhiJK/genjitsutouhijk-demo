# web（前端 · Vite + Vue 3）

`genjitsutouhijk-demo` 项目的前端部分。单页应用，目前包含**登录页**、**注册页**和**登录后的主页**，与 `../server`（Spring Boot 后端）配合跑通"注册 → 登录 → 会话 → 退出"的完整闭环。

整项目说明见根目录 [`../README.md`](../README.md)。

## 开发

```bash
npm install     # 首次运行，安装依赖
npm run dev     # 启动开发服务器（默认 http://localhost:5173）
npm run build   # 打包到 dist/，产物可直接静态部署（如 GitHub Pages）
npm run preview # 本地预览打包结果
```

接口默认请求 `http://localhost:8080`，基础地址可在 `src/utils/request.js` 的 `BASE_URL` 中统一修改。

> ⚠️ 页面上所有涉及账号的操作（登录、注册）都需要 `../server` 后端**同时在跑**，否则只会提示"网络错误，请稍后重试"。

## 页面与路由

| 路径 | 页面 | 谁能看 |
|---|---|---|
| `/login` | 登录页 | 只有未登录用户 |
| `/register` | 注册页 | 只有未登录用户 |
| `/home` | 主页 | 必须已登录 |
| 其他 / `/` | — | 重定向到 `/login` |

守卫在 `src/router/index.js` 的 `beforeEach` 里，按这个顺序判断：

0. **先把过期的会话就地清掉** —— 只要本地存着一张已经过期的票，
   就 `clearSession()` + 记一句"登录已过期，请重新登录"，然后照常走下面两步。
   没有这一步的话，过期的票也算"已登录"，守卫会放行 `/home`，
   主页画完面板才发现票不灵 —— 用户看到的就是"面板闪一下再被弹走"。
1. `meta.requiresAuth` → 没登录进 `/home`，送回 `/login`；
2. `meta.guestOnly` → 已登录还去 `/login` 或 `/register`，送回 `/home`。

> 所以调试注册页时，**要先退出登录**，否则会被自动弹回主页。
>
> 第 0 步依赖 `utils/session.js` 的 `isTokenExpired()`：JWT 的 payload 是明文，
> 本地就能读出 `exp` 来比对，**同步、零网络请求**。
> ⚠️ 但这只是体验优化，**不是安全边界** —— payload 谁都能改。
> 真正说了算的仍然是服务端那次核验（`GET /api/user/me`）。
> 因此 `isTokenExpired()` 在读不出 `exp` 时返回 `false`（不下结论），
> 宁可多跑一次请求，也不因为解析意外把登录着的人踢出去。

## 目录结构

```
src/
├─ main.js                  # 入口：挂载根组件 + 引入全局样式 + use(router)
├─ App.vue                  # 根组件：<RouterView> 路由出口 + 页面切换过渡（<Transition name="route">）
├─ router/
│  └─ index.js              # 路由表（/login、/register、/home）+ 双向守卫 beforeEach
├─ styles/
│  └─ main.css              # 全局样式：设计令牌、动效令牌与全部关键帧、共用版式块
│                           #   （.panel / .status / .form-switch 等）、按钮体系、路由过渡、减少动效开关
├─ utils/
│  ├─ session.js            # 会话状态：setSession / clearSession / isLoggedIn
│  │                        #   + readTokenPayload / isTokenExpired（本地读过期时间）
│  │                        #   + setNotice / takeNotice（跨页面的一次性提示，读一次即清）
│  └─ request.js            # 请求封装：BASE_URL、JSON 处理、自动带 Authorization 头、凭证失效处理
├─ api/
│  ├─ auth.js               # 接口定义层：只声明 login() / register() 调哪个接口、传什么
│  └─ user.js               # getMe()：取当前登录用户资料（受保护接口）
├─ components/
│  ├─ AppShell.vue          # 页面外壳：背景/水印/准星/顶栏/底栏，页面内容通过 <slot> 填入
│  └─ PasswordInput.vue     # 可复用组件：带"显示/隐藏"的密码输入框
└─ views/
   ├─ LoginView.vue         # 登录页：表单 + "上一次为什么被带回来"的提示 + 成功后 setSession 并跳转 /home
   ├─ RegisterView.vue      # 注册页：多一个"确认密码"字段，注册即登录，成功后同样跳 /home
   └─ HomeView.vue          # 主页：核验闸门（Verifying / Unverified）→ 通过后才渲染
                            #   身份卡（含凭证寿命条）+ 四格指标 + 双栏对照 + 顶栏退出
```

## 认证是怎么接进来的

后端从里程碑 ③ 起是"默认拒绝"的：除了登录 / 注册，其他接口都要带 token。
前端这边只需要认准**一个地方**，就是 `utils/request.js`：

```js
const token = session.value?.accessToken
headers: {
  ...(token ? { Authorization: `Bearer ${token}` } : {}),
}
```

- **发请求时自动带上** `Authorization: Bearer <token>`，业务代码里一行都不用写；
- **收到 401 时**（后端返回 `code` 1005 / 1006 / 1007）自动 `clearSession()`，并把
  "为什么失效"传给 `main.js` 注册进来的处理函数，由它记下提示再跳回登录页。

关键设计：`request.js` **不 import router**，因为它和 router 会形成循环依赖
（`request.js → router → views → api → request.js`）。它只导出一个 `setUnauthorizedHandler()`，
由 `src/main.js` 在启动时把"跳回登录页"这个动作注册进去 —— 依赖方向始终从上往下，不绕圈。

另外注意：**HTTP 状态码 401 并不影响前端的判断逻辑**。
后端把 401 的响应体也做成了统一的 `{ code, message, data }`，
所以 `request.js` 依然是"不看状态码、只解析 body、只判断 `json.code`"。

### 主页的闸门：核验通过前不渲染

`HomeView.vue` 有三种状态，用**一个** `status` 变量表达（`checking` / `verified` / `error`），
而不是 `isLoading` + `hasError` 两个布尔量 —— 后者能拼出四种组合，其中
"既在加载又出错了"是不可能的，那种"不可能的状态"一旦被拼出来就是 bug 的温床。

```
挂载 → checking（只显示一块 Verifying 闸门，不显示任何会话内容）
         ├─ code 0        → verified → 渲染身份卡 / 指标格 / 双栏
         ├─ 1005/1006/1007 → request.js 已清会话 → 跳登录页
         └─ fetch 抛错     → error → 显示原因 + 重试按钮，**保留会话**
```

★ 最后那条是这条链路里最容易做错的地方：**连不上后端不等于没登录**。
网络抖一下就把用户登出，比"多显示一次错误提示"糟糕得多，所以失败态里
"重试"是第一选项，"退出登录"只是备用出口。

> 想亲眼看这三条路：
> - **过期**：把后端 `app.jwt.expires-in-seconds` 改成 10，登录后在主页等 10 秒再刷新
>   —— 会被路由守卫**在进主页之前**拦下，直接落在登录页并显示"登录已过期"；
> - **连不上**：登录后把后端停掉再刷新 —— 显示 `Unverified` 闸门，会话还在，重开后端点重试即恢复；
> - **有效**：正常刷新 —— 闸门一闪而过，然后是面板。

## 动效

整套动效只有两种性格，令牌写在 `styles/main.css` 的 `:root` 里：

| 性格 | 曲线 / 时长 | 用在哪 |
|---|---|---|
| **机械** —— 快起步、慢收尾，像装置"咔"地到位 | `--ease-out` = `cubic-bezier(.22,.61,.36,1)`，`--dur-fast/--dur/--dur-slow` | 所有进场、hover、按下 |
| **信号** —— 匀速、缓慢、不引人注意，像设备通着电 | `linear` + 秒级时长 | 顶栏光带、水印漂移、光标闪烁 |

**刻意不用会回弹的曲线**（`back` / `elastic`）：零圆角的直角版面配弹跳会显得轻浮，
和这套"终端"气质打架。所有进场都压在 0.4s 内、延迟不超过 0.4s ——
动效是用来提示结构的，不是让用户等的。

### 清单：动效都在哪

| 位置 | 效果 | 触发 |
|---|---|---|
| `App.vue` | 页面切换淡入淡出（`mode="out-in"`），只改透明度**不加位移** | 路由跳转 |
| `AppShell` 四角准星 | 横竖两笔从中心展开，四角顺时针依次"校准" | 挂载 |
| `AppShell` 顶栏标题 | 从左往右"打印"出来（`clip-path` 裁自己，不改布局宽度） | 挂载 |
| `AppShell` 顶栏下沿 | 一道浅光 7 秒一次缓缓划过 | 无限循环 |
| `AppShell` 水印 | 54 秒挪 16px 的极慢漂移 | 无限循环 |
| `AppShell` 底栏 | 状态文字后跟一个闪烁光标 | 无限循环 |
| 所有面板 `.panel` | 从下方 8px 浮起（`rise-in`） | 挂载 |
| 深色标题条 `.panel-bar` | 一道浅光从左扫过，**只扫一次**（通电自检） | 挂载 |
| 标题行 `.stage-head` 的线 | 从左往右"画"出来（`scaleX`，不是改宽度） | 挂载 |
| 输入框 `.field` | 获得焦点时，**本行**的标签线被深色线从左往右覆盖 | 聚焦 |
| 主按钮按下 | 整体下沉 1px | `:active` |
| 主按钮禁用（= 登录中） | 一道浅光持续扫过 | `disabled` |
| 表单提示 `.status` | 从左推 5px 出现 | 每次出现 |
| 主页指标格 | 四格依次落位，每格晚 55ms | 挂载 |
| 主页凭证寿命条 | 剩余 < 20% 转红并缓慢呼吸，数字同步转红 | 剩余不足 20% |
| 主页「服务端已确认」徽标 | 边框荡出一圈涟漪，**只荡一次** | 挂载 |
| 主页顶栏小绿点 | 2.8 秒一轮的明暗呼吸 | 无限循环 |

### 五条约定

1. **关键帧只定义在 `styles/main.css`，组件的 scoped 样式直接引用名字。**
   Vue 的 scoped 编译只会给**同一个 `<style scoped>` 块里定义**的关键帧加作用域后缀；
   引用全局名字不会被改写（构建产物里 `rise-in` 仍是 `rise-in`，已验证）。
   好处是"整个项目的动效语法"只有一个文件能定义，审查时看一处就够。
2. **错峰延迟用 CSS 变量从模板传**：`v-for` 里写 `:style="{ '--i': index }"`，
   样式里 `animation-delay: calc(var(--i, 0) * 55ms)`。
   加一格、调顺序都不用动样式；`var()` 的第二个参数是兜底，绑丢了也不会整块不显示。
3. **不要用 `html.includes('life--low')` 这类方式验证类名**（写校验脚本时踩过）：
   模板里的 HTML 注释在开发模式下会保留，注释里写了类名就会假阳性。
   另外 Vue 的 SSR 把**动态 class 排在静态 class 之前**，
   实际输出是 `class="life--low life"`，拼顺序的断言一定会挂。
4. **`prefers-reduced-motion` 由 main.css 末尾那条 `*` 规则一次性关掉全站动效。**
   不要在组件里逐个写例外 —— 那样新加的动画迟早会漏，而漏掉的后果正是违背用户设置。
5. **被 `<Transition>` 包住的页面组件必须是"单根"，模板顶层不能有注释。**
   见下面那条红字警告 —— 这条踩过一次，后果是整页永久空白。

> ⚠️ **页面组件的模板顶层必须是唯一一个根节点（不能有注释、不能有第二个兄弟节点）。**
>
> `App.vue` 用 `<Transition name="route" mode="out-in">` 包住路由页面，而 `<Transition>`
> 只认**单个根节点**。开发模式下 Vue **会保留模板里的 HTML 注释**，于是
> `<template>` 里写一句 `<!-- ... -->` 就会让根变成 `[注释, AppShell]` 这种多根，
> 组件根退化为 Fragment，过渡钩子**挂不到真正的 DOM 上**。
>
> 崩的方式很隐蔽（2026-09-28 实际踩到，排查花了很久）：
> 1. `out-in` 先把旧页面标记为 `isLeaving`，渲染一个空注释占位；
> 2. 它靠"旧页面过渡结束"的回调（`afterLeave`）复位 `isLeaving`，然后才挂新页面；
> 3. 钩子没传到 DOM 上 → 旧页面被**直接摘掉**，那个回调永远不来；
> 4. `isLeaving` 永久停在 `true` → **之后每次渲染都只输出空占位**。
>
> 症状：**点了登录/注册，地址栏跳到 `/home`，但页面一片空白；按 F5 刷新又完全正常，
> 而且控制台一个字都不报。** 刷新能好，是因为整页加载走的是另一条路（没有过渡）。
>
> 另外一个坑中坑：**生产构建会把模板注释删掉**，所以 `npm run build` 出来的版本
> 完全正常 —— 这个 bug 只在 `npm run dev` 下出现，"构建通过"根本发现不了它。
> 所以：**说明性注释一律写在 `<script>` 里，不要写在 `<template>` 顶层。**

> ⚠️ 有几种情况的关键帧里**必须把元素原有的 `transform` 一起写上**：
> 准星的 `translateX(-50%)` / `translateY(-50%)`、水印的 `translate(-50%, -50%)`、
> 密码框 SHOW/HIDE 按钮的 `translateY(-50%)`（那句同时承担垂直居中）。
> keyframes 不叠加元素自己的 `transform`，少写一句就会在动画期间把元素挪位。
>
> ⚠️ 动画里的 `animation-fill-mode: both` 意味着**延迟期间元素停在起始态**（多半是 `opacity: 0`）。
> 所以进场动画的延迟不能太长，否则页面上会出现一段"什么都没有"的空白。

## 分层约定

- **views/** 只关心页面展示与交互，不直接写 `fetch`；
- **api/** 只描述接口，不关心 UI；
- **utils/request.js** 是唯一的请求出口，换后端地址、加 token、统一错误提示都改这里；
- **utils/session.js** 是唯一的跨页面会话状态出口，登录/注册页写进去、主页读出来，刷新不丢；
- **components/** 放与业务无关、可被多个页面复用的 UI 组件；
- 只属于某个页面的样式写在对应 `.vue` 的 `<style scoped>` 里，跨页面共用的才进 `styles/`。
- **前端的本地校验规则必须和后端的 DTO 注解保持一致**（用户名 3~20、密码 6~32）。
  本地校验只是为了省一次网络往返，**它挡不住绕过前端直接发请求的情况**，后端那份才是真正的防线；改规则时两边必须一起改。
- **错误码是前后端之间的契约**：`request.js` 里的 `SESSION_LOST_CODES`（1005/1006/1007）必须和后端
  `ErrorCode` 枚举对得上。这类改动没有类型系统兜底，最容易漏，改的时候两边一起搜。
- **动画只做"提示结构"这一件事**：进场用来说明"这里分三层、从哪读起"，
  循环动画只用来表达"还活着 / 正在处理"。加新动效前先想清楚它在提示什么 ——
  想不出来就别加。**无限循环的光带、会一直扩散的圈**这类要特别克制，
  看几秒就从"精致"变成"干扰"（所以标题条的光带只扫一次、徽标的涟漪只荡一次）。
