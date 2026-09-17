# web（前端 · Vite + Vue 3）

`genjitsutouhijk-demo` 项目的前端部分。单页应用，目前包含**登录页**和**登录后的主页**，与 `../server`（Spring Boot 后端）配合跑通登录闭环。

整项目说明见根目录 [`../README.md`](../README.md)。

## 开发

```bash
npm install     # 首次运行，安装依赖
npm run dev     # 启动开发服务器（默认 http://localhost:5173）
npm run build   # 打包到 dist/，产物可直接静态部署（如 GitHub Pages）
npm run preview # 本地预览打包结果
```

登录接口默认请求 `http://localhost:8080/api/auth/login`，基础地址可在 `src/utils/request.js` 的 `BASE_URL` 中统一修改。

## 目录结构

```
src/
├─ main.js                  # 入口：挂载根组件 + 引入全局样式 + use(router)
├─ App.vue                  # 根组件：纯 <RouterView /> 路由出口
├─ router/
│  └─ index.js              # 路由表（/login、/home）+ 登录守卫 beforeEach
├─ styles/
│  └─ main.css              # 全局样式：设计令牌、共用版式块（.panel 等）、按钮体系
├─ utils/
│  ├─ session.js            # 跨页面会话状态（ref + sessionStorage）：setSession / clearSession / isLoggedIn
│  └─ request.js            # 请求封装：BASE_URL、JSON 处理、异常统一处理
├─ api/
│  └─ auth.js               # 接口定义层：只声明 login() 调哪个接口、传什么
├─ components/
│  ├─ AppShell.vue          # 页面外壳：背景/水印/准星/顶栏/底栏，页面内容通过 <slot> 填入
│  └─ PasswordInput.vue     # 可复用组件：带"显示/隐藏"的密码输入框
└─ views/
   ├─ LoginView.vue         # 登录页：表单 + 登录逻辑 + 成功后 setSession 并跳转 /home
   └─ HomeView.vue          # 主页：欢迎语 + 会话信息面板 + 退出登录
```

## 分层约定

- **views/** 只关心页面展示与交互，不直接写 `fetch`；
- **api/** 只描述接口，不关心 UI；
- **utils/request.js** 是唯一的请求出口，换后端地址、加 token、统一错误提示都改这里；
- **utils/session.js** 是唯一的跨页面会话状态出口，登录页写进去、主页读出来，刷新不丢；
- **components/** 放与业务无关、可被多个页面复用的 UI 组件；
- 只属于某个页面的样式写在对应 `.vue` 的 `<style scoped>` 里，跨页面共用的才进 `styles/`。
