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
├─ App.vue                  # 根组件：纯 <RouterView /> 路由出口
├─ router/
│  └─ index.js              # 路由表（/login、/register、/home）+ 双向守卫 beforeEach
├─ styles/
│  └─ main.css              # 全局样式：设计令牌、共用版式块（.panel / .status / .form-switch 等）、按钮体系
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
