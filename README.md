# genjitsutouhijk-demo

一个用于**个人练手的全栈项目**，前后端放在同一个仓库里。

当前目标很朴素：先跑通一个**登录页 → 主页**的基础流程，把"前端怎么调用后端接口、登录数据怎么传过页面"这条主线打通，再一点点往上加功能。所以现在的内容刻意保持精简，方便逐步理解，而不是一上来就堆满功能。

> 仓库已推送到 GitHub（见下方"已知事项"）。

## 技术栈

| 部分 | 技术 | 说明 |
|---|---|---|
| 后端 `server/` | Spring Boot 4.1.1 + Java 21 + Maven | Controller / Service / Repository 分层，统一异常处理 + 参数校验 |
| 数据库 | H2（文件模式）+ Spring Data JPA + Flyway | 零安装，不用装数据库服务；建表由 Flyway 的 SQL 脚本管理，换库只改配置 |
| 密码存储 | Spring Security 的 BCrypt | 库里只存哈希，不存明文 |
| 前端 `web/` | Vite + Vue 3 + Vue Router 4 | 单页应用，登录页 / 注册页 / 主页，带双向路由守卫 |
| 通信 | 基于 `fetch` 的封装 + Spring CORS | 请求后端 `http://localhost:8080`，接口路径以 `/api` 开头 |

## 仓库结构

```
genjitsutouhijk-demo/
├─ README.md                本文件（整项目说明）
├─ .gitignore               忽略 IDE 配置、AI 工作台数据、打包副本等
├─ server/                  后端（Spring Boot）
│  ├─ README.md             后端专属说明（分层约定、接口与错误码、Boot 4 踩坑）
│  ├─ pom.xml               Maven 配置（groupId io.github.genjitsutouhijk）
│  ├─ data/                 H2 数据库文件（首次启动自动生成，已 gitignore）
│  └─ src/
│     ├─ main/java/.../demo/
│     │  ├─ Application.java               启动类
│     │  ├─ config/
│     │  │  ├─ CorsConfig.java             允许前端跨域访问 /api/**
│     │  │  └─ PasswordConfig.java         密码加密器（BCrypt）
│     │  ├─ controller/
│     │  │  ├─ AuthController.java         POST /api/auth/login、POST /api/auth/register
│     │  │  └─ HelloController.java        GET /hello
│     │  ├─ service/AuthService.java       业务规则：校验账号密码、注册、签发凭证
│     │  ├─ repository/UserRepository.java 数据访问层（Spring Data 自动生成实现，没有 Impl）
│     │  ├─ entity/User.java               实体类，对应数据库的 users 表
│     │  ├─ exception/
│     │  │  ├─ ErrorCode.java              业务错误码字典
│     │  │  ├─ BusinessException.java      业务异常（表达"规则不允许"，不是 bug）
│     │  │  └─ GlobalExceptionHandler.java 全局异常处理，把异常翻译成 ApiResponse
│     │  └─ dto/                           ApiResponse / LoginRequest / LoginResponse / RegisterRequest
│     ├─ main/resources/
│     │  ├─ application.properties         数据源 / JPA / Flyway 配置
│     │  └─ db/migration/                  Flyway 迁移脚本：V1 建表、V2 种子用户
│     └─ test/
│        ├─ java/.../demo/
│        │  ├─ ApplicationTests.java       上下文能否正常启动
│        │  ├─ controller/AuthControllerTest.java  认证接口全部分支的 MockMvc 测试
│        │  └─ repository/UserRepositoryTest.java  数据访问层的真实数据库验证
│        └─ resources/application-test.properties  测试专用配置（换成内存数据库）
└─ web/                     前端（Vue 3）
   ├─ README.md             前端专属说明
   ├─ index.html            入口 HTML
   ├─ vite.config.js        Vite 配置
   └─ src/
      ├─ main.js            应用入口：createApp + .use(router)
      ├─ App.vue            根组件：纯 <RouterView /> 出口
      ├─ router/index.js    路由表 + 登录守卫
      ├─ styles/main.css    设计令牌 + 共用版式块（全局）
      ├─ utils/
      │  ├─ session.js      跨页面会话状态（ref + sessionStorage）
      │  └─ request.js      基于 fetch 的请求封装（BASE_URL）
      ├─ api/auth.js        登录接口 login()
      ├─ components/
      │  ├─ AppShell.vue    页面外壳（背景/水印/准星/顶栏/底栏 + 插槽）
      │  └─ PasswordInput.vue  带"显示/隐藏"的密码输入框
      └─ views/
         ├─ LoginView.vue    登录页
         ├─ RegisterView.vue 注册页（多一个"确认密码"字段）
         └─ HomeView.vue     登录后的主页
```

## 后端怎么跑

需要本地装好 **JDK 21** 和 **Maven**。

```bash
cd server
mvn spring-boot:run        # 默认监听 8080
mvn test                   # 跑测试（不启动服务、不占端口）
```

> 想换端口：`mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080`

**数据库不用装。** H2 就是一个 jar：第一次启动时会自动在 `server/data/` 下建出数据库文件，
并执行 `src/main/resources/db/migration/` 里的 SQL 脚本建表、插入一个演示账号。

想直接看表里的数据：启动后打开 <http://localhost:8080/h2-console>，
JDBC URL 填 `jdbc:h2:file:./data/genjitsutouhijk-demo`，用户名 `sa`，密码留空。

启动后可用接口：

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/auth/login` | 登录。请求体 `{ "username": "admin", "password": "123456" }`。校验通过后返回一个**假 token**（`fake-token-for-admin`），字段：`accessToken` / `tokenType`(Bearer) / `expiresIn`(3600) / `username` |
| `POST` | `/api/auth/register` | 注册。请求体 `{ "username": "alice", "password": "alice-pass" }`（用户名 3–20 位、密码 6–32 位）。成功后**直接返回登录凭证**（注册即登录），字段与登录完全一致 |
| `GET` | `/hello` | 返回纯文本 `Hello, Spring Boot!`，用来快速验证后端活着 |

所有接口共用同一个响应壳 `{ "code": 0, "message": "成功", "data": {...} }`，前端只判断 `code` 是不是 0。

| `code` | 含义 | 什么情况会出现 |
|---|---|---|
| `0` | 成功 | — |
| `1001` | 用户名或密码错误 | 账号密码对不上 |
| `1002` | 请求参数不合法 | 用户名/密码为空或只有空格，`message` 会指出是哪个字段 |
| `1003` | 请求体格式错误 | 没传 body，或 body 不是合法 JSON |
| `1004` | 用户名已被占用 | 注册时用了别人已经用过的用户名 |
| `404` | 接口不存在 | 访问了没有对应接口的路径（HTTP 状态码同样是 404） |
| `9999` | 服务器开小差了 | 没预料到的异常（HTTP 状态码 500），细节只写进日志不给前端 |

> 账号 `admin / 123456` 是 Flyway 的 V2 迁移脚本插进数据库的**种子用户**（库里存的是 BCrypt 哈希，不是明文）。
> 想加自己的账号，调注册接口，或者用上面的 H2 控制台直接往表里插都行。

## 前端怎么跑

需要本地装好 **Node.js 18+**（用了 Vite）。

```bash
cd web
npm install      # 首次运行，安装依赖
npm run dev      # 启动开发服务器（默认 http://localhost:5173）
npm run build    # 打包到 web/dist/，产物可直接静态部署
npm run preview  # 本地预览打包结果
```

打开页面后：
1. 访问 `/` 会被自动重定向到登录页 `/login`；
2. 用 `admin / 123456` 登录，成功后跳到 `/home`；
3. 主页展示登录时缓存下来的会话数据（账号 / 登录时间 / 令牌信息 —— 来自登录接口的返回体，存在 `sessionStorage` 里），并有"退出登录"按钮。

> 直接访问 `/home` 且未登录，会被路由守卫弹回登录页。

## 前后端怎么连起来

- 前端所有请求走 `src/utils/request.js` 这一个出口，`BASE_URL = 'http://localhost:8080'`。
  ⚠️ 注意它**不含 `/api`** —— 接口的完整路径在 `src/api/auth.js` 里写（`/api/auth/login`）。换后端地址只需改 `BASE_URL` 这一处。
- 后端 `CorsConfig` 放行了 `http://localhost:5173` 对 `/api/**` 的跨域访问。
  - ⚠️ 注意：若 Vite 启动时 **5173 端口被占用**，会自动改用 `5174`，此时前端端口与 CORS 白名单不一致，登录请求会被浏览器拦截。解决办法：要么让出 5173 端口，要么在 `CorsConfig` 里把 `5174` 也加进白名单。

## 当前进度与下一步

**已完成（主线打通）**
- 登录页 + 注册页 + 主页，三个页面共用同一套视觉外壳（`AppShell`），风格一致；
- 登录 → 拿 token → 跳转主页 → 主页展示会话数据 → 退出登录，完整闭环；
- 注册页填用户名 / 密码 / 确认密码，成功后同样拿到凭证进主页；
- 路由守卫双向生效：未登录不能进主页，已登录也不会停在登录 / 注册页。
- **后端里程碑 ①**：Controller / Service 分层，全局异常处理 + 参数校验，错误码收进 `ErrorCode` 字典。
  对外接口行为完全不变。
- **后端里程碑 ②**：接上数据库（H2 文件模式）。
  `AuthService` 里写死的假用户换成真实的 `users` 表查询；密码用 BCrypt 加密后存储，库里没有明文；
  新增注册接口 `POST /api/auth/register`；建表改由 Flyway 的 SQL 脚本管理（`ddl-auto=validate`，不让 Hibernate 碰表结构）。

**刻意留成"假"的地方（练手过程中逐步替换）**
- `accessToken` 还是拼出来的假字符串，不是真 JWT；而且服务端不记录、也不校验它 ——
  前端拿着它回来，后端依然不知道你是谁；
- 没有引入 Spring Security，所有接口都是匿名可访问的；
- 注册接口只有后端，前端还没有对应的注册页面；
- 主页目前只有"会话信息面板"，没有真实业务功能。

**可能的下一步**
- 里程碑 ③：真 JWT + Spring Security，前端带上 token 并做鉴权拦截；
- 前端加注册页，把 `/api/auth/register` 用起来；
- 主页加顶栏用户下拉 / 左侧导航 / 真实功能模块。

## 已知事项

- 远程仓库：`git@github.com:GenjitsutouhiJK/genjitsutouhijk-demo.git`（推送走 SSH）。
  ⚠️ GitHub Pages **只托管静态文件**，`server/`（Spring Boot）无法部署上去；将来若要上线在线版本，
  只能把 `web/` 单独拆成仓库，后端另找地方部署。
- `web/dist/` 是 `npm run build` 的产物，可随时删除、不纳入版本管理。
- `server/data/` 是 H2 的数据库文件，首次启动自动生成，已加进 `server/.gitignore`。
  想"重置数据库"直接删掉这个目录、重启即可 —— Flyway 会重新建表并插回种子用户。
- `spring-boot-starter-flyway` 启动时会警告 `H2 2.4.240 which is newer than the version Flyway has been verified with`。
  **这是正常现象**：Boot 4.1.1 受管的 H2 版本比当前 Flyway 官方验证过的版本新一点，实测迁移功能正常，可以忽略。
- `web/src/assets/` 下的 `hero.png` / `vite.svg` / `vue.svg` 以及 `web/public/icons.svg` 是脚手架残留，没有任何代码引用，可删。
- `server/pom.xml` 里的 **Lombok** 依赖目前仍然完全没用到（DTO 都用 `record`，实体类手写 getter），属脚手架残留，可删。
