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

守卫在 `src/router/index.js` 的 `beforeEach` 里，两个方向都拦：

- `meta.requiresAuth` → 没登录进 `/home`，送回 `/login`；
- `meta.guestOnly` → 已登录还去 `/login` 或 `/register`，送回 `/home`。

> 所以调试注册页时，**要先退出登录**，否则会被自动弹回主页。

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
│  ├─ session.js            # 跨页面会话状态（ref + sessionStorage）：setSession / clearSession / isLoggedIn
│  └─ request.js            # 请求封装：BASE_URL、JSON 处理、异常统一处理
├─ api/
│  └─ auth.js               # 接口定义层：只声明 login() / register() 调哪个接口、传什么
├─ components/
│  ├─ AppShell.vue          # 页面外壳：背景/水印/准星/顶栏/底栏，页面内容通过 <slot> 填入
│  └─ PasswordInput.vue     # 可复用组件：带"显示/隐藏"的密码输入框
└─ views/
   ├─ LoginView.vue         # 登录页：表单 + 登录逻辑 + 成功后 setSession 并跳转 /home
   ├─ RegisterView.vue      # 注册页：多一个"确认密码"字段，注册即登录，成功后同样跳 /home
   └─ HomeView.vue          # 主页：欢迎语 + 会话信息面板 + 退出登录
```

## 分层约定

- **views/** 只关心页面展示与交互，不直接写 `fetch`；
- **api/** 只描述接口，不关心 UI；
- **utils/request.js** 是唯一的请求出口，换后端地址、加 token、统一错误提示都改这里；
- **utils/session.js** 是唯一的跨页面会话状态出口，登录/注册页写进去、主页读出来，刷新不丢；
- **components/** 放与业务无关、可被多个页面复用的 UI 组件；
- 只属于某个页面的样式写在对应 `.vue` 的 `<style scoped>` 里，跨页面共用的才进 `styles/`。
- **前端的本地校验规则必须和后端的 DTO 注解保持一致**（用户名 3~20、密码 6~32）。
  本地校验只是为了省一次网络往返，**它挡不住绕过前端直接发请求的情况**，后端那份才是真正的防线；改规则时两边必须一起改。
