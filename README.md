# genjitsutouhijk-demo

一个用于**个人练手的全栈项目**，前后端放在同一个仓库里。

当前目标很朴素：先跑通一个**登录页 → 主页**的基础流程，把"前端怎么调用后端接口、登录数据怎么传过页面"这条主线打通，再一点点往上加功能。所以现在的内容刻意保持精简，方便逐步理解，而不是一上来就堆满功能。

> 仓库已推送到 GitHub（见下方"已知事项"）。

## 技术栈

| 部分 | 技术 | 说明 |
|---|---|---|
| 后端 `server/` | Spring Boot 4.1.1 + Java 21 + Maven | Controller / Service / Repository 分层，统一异常处理 + 参数校验 |
| 认证 | Spring Security 7 + JWT（JJWT 0.13） | 无状态认证：登录发一张签名 token，之后每个请求自带；默认拒绝 + 自定义 401 响应 |
| 数据库 | H2（文件模式）+ Spring Data JPA + Flyway | 零安装，不用装数据库服务；建表由 Flyway 的 SQL 脚本管理，换库只改配置 |
| 密码存储 | Spring Security 的 BCrypt | 库里只存哈希，不存明文 |
| 前端 `web/` | Vite + Vue 3 + Vue Router 4 | 单页应用，登录页 / 注册页 / 主页，带双向路由守卫 |
| 通信 | 基于 `fetch` 的封装 + Spring CORS | 请求后端 `http://localhost:8080`，接口路径以 `/api` 开头，自动带 `Authorization` 头 |

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
│     │  │  ├─ PasswordConfig.java         密码加密器（BCrypt）
│     │  │  ├─ JwtProperties.java          读取 app.jwt.* 配置（密钥、有效期）
│     │  │  └─ SecurityConfig.java         安全规则总表（谁可以访问什么）
│     │  ├─ security/
│     │  │  ├─ JwtService.java             JWT 签发与解析（HS256）
│     │  │  ├─ JwtAuthenticationFilter.java 把 Authorization 头翻译成登录身份
│     │  │  └─ RestAuthenticationEntryPoint.java 未登录时输出统一格式的 401
│     │  ├─ controller/
│     │  │  ├─ AuthController.java         POST /api/auth/login、POST /api/auth/register
│     │  │  ├─ UserController.java         GET /api/user/me（需登录）
│     │  │  └─ HelloController.java        GET /hello
│     │  ├─ service/                       AuthService（账号密码）、UserService（用户资料）
│     │  ├─ repository/UserRepository.java 数据访问层（Spring Data 自动生成实现，没有 Impl）
│     │  ├─ entity/User.java               实体类，对应数据库的 users 表
│     │  ├─ exception/
│     │  │  ├─ ErrorCode.java              业务错误码字典
│     │  │  ├─ BusinessException.java      业务异常（表达"规则不允许"，不是 bug）
│     │  │  └─ GlobalExceptionHandler.java 全局异常处理，把异常翻译成 ApiResponse
│     │  └─ dto/                           ApiResponse / LoginRequest / LoginResponse / RegisterRequest / UserProfile
│     ├─ main/resources/
│     │  ├─ application.properties         数据源 / JPA / Flyway / JWT 配置
│     │  └─ db/migration/                  Flyway 迁移脚本：V1 建表、V2 种子用户
│     └─ test/
│        ├─ java/.../demo/
│        │  ├─ ApplicationTests.java       上下文能否正常启动
│        │  ├─ controller/AuthControllerTest.java        认证接口全部分支
│        │  ├─ controller/SecurityIntegrationTest.java   安全链与受保护接口
│        │  ├─ security/JwtServiceTest.java              JWT 签发/解析/过期/伪造（纯单元测试）
│        │  └─ repository/UserRepositoryTest.java        数据访问层的真实数据库验证
│        └─ resources/application-test.properties  测试专用配置（内存数据库 + 独立 JWT 密钥）
└─ web/                     前端（Vue 3）
   ├─ README.md             前端专属说明
   ├─ index.html            入口 HTML
   ├─ vite.config.js        Vite 配置
   └─ src/
      ├─ main.js            应用入口：createApp + .use(router) + 注册 401 处理
      ├─ App.vue            根组件：纯 <RouterView /> 出口
      ├─ router/index.js    路由表 + 登录守卫
      ├─ styles/main.css    设计令牌 + 共用版式块（全局）
      ├─ utils/
      │  ├─ session.js      跨页面会话状态（ref + sessionStorage）
      │  └─ request.js      基于 fetch 的请求封装（BASE_URL、自动带 token、处理 401）
      ├─ api/
      │  ├─ auth.js         登录 / 注册接口
      │  └─ user.js         当前用户资料接口
      ├─ components/
      │  ├─ AppShell.vue    页面外壳（背景/水印/准星/顶栏/底栏 + 插槽）
      │  └─ PasswordInput.vue  带"显示/隐藏"的密码输入框
      └─ views/
         ├─ LoginView.vue    登录页
         ├─ RegisterView.vue 注册页（多一个"确认密码"字段）
         └─ HomeView.vue     登录后的主页（含"服务端确认的身份"面板）
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

| 方法 | 路径 | 需要登录 | 说明 |
|---|---|---|---|
| `POST` | `/api/auth/login` | 否 | 登录。请求体 `{ "username": "admin", "password": "123456" }`。校验通过后返回一张**真 JWT**，字段：`accessToken` / `tokenType`(Bearer) / `expiresIn`(3600) / `username` |
| `POST` | `/api/auth/register` | 否 | 注册。请求体 `{ "username": "alice", "password": "alice-pass" }`（用户名 3–20 位、密码 6–32 位）。成功后**直接返回登录凭证**（注册即登录），字段与登录完全一致 |
| `GET` | `/api/user/me` | **是** | 返回当前登录用户的资料（`id` / `username` / `createdAt`）。身份取自 token，不接受前端传参 |
| `GET` | `/hello` | 否 | 返回纯文本 `Hello, Spring Boot!`，用来快速验证后端活着 |

受保护接口的调用方式：请求头带上 `Authorization: Bearer <accessToken>`。
规则是"默认拒绝"—— 没在 `SecurityConfig` 里明确放开的路径，一律要求登录。

所有接口共用同一个响应壳 `{ "code": 0, "message": "成功", "data": {...} }`，前端只判断 `code` 是不是 0。

| `code` | 含义 | 什么情况会出现 |
|---|---|---|
| `0` | 成功 | — |
| `1001` | 用户名或密码错误 | 账号密码对不上 |
| `1002` | 请求参数不合法 | 用户名/密码为空或只有空格，`message` 会指出是哪个字段 |
| `1003` | 请求体格式错误 | 没传 body，或 body 不是合法 JSON |
| `1004` | 用户名已被占用 | 注册时用了别人已经用过的用户名 |
| `1005` | 请先登录 | 没带 `Authorization` 头就访问受保护接口（HTTP 401） |
| `1006` | 登录已过期 | 带了 token，但已超过有效期（HTTP 401） |
| `1007` | 登录凭证无效 | token 被改过 / 不是本服务签发的（HTTP 401） |
| `404` | 接口不存在 | 访问了没有对应接口的路径（HTTP 状态码同样是 404；**未登录时会是 401**） |
| `9999` | 服务器开小差了 | 没预料到的异常（HTTP 状态码 500），细节只写进日志不给前端 |

> `401` 的响应体**也是**上面那个统一格式，所以前端照旧只判断 `json.code`。
> 之所以"密码错误"是 200 而"未登录"是 401：前者是业务层得出的结论，后者请求压根没进 Controller，
> 属于框架层面没走通（和 404 / 500 同类）。详见 `server/README.md`。

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
3. 主页（通栏 920px，不再是登录页那种窄列）分三层，从"概览"到"细节"：
   - **身份卡**：首字母方块 + 欢迎语 + "服务端已确认"徽标，右侧是**每秒在跳的凭证寿命条**；
   - **指标格**：`User ID / Token Type / Token TTL / Registered` 四格，
     每格右上角标着这条数据的出处 —— `server` 是刚从后端问来的，`local` 是浏览器里存的；
   - **双栏对照**：左栏 `Session / Local` 是登录那一刻存进 `sessionStorage` 的快照；
     右栏 `Server / Verified` 是**每次刷新都重新向后端确认**的 ——
     本页会带着 token 调一次 `GET /api/user/me`，后端验签、查库，再返回资料。
   - "退出登录"收在顶栏右上角，不再拖在页面尾部。

> **★ 主页在服务端确认身份之前，不会渲染上面这些东西。**
>
> 本地 `sessionStorage` 里那份数据只是"登录那一刻"的快照 —— 可能已经过期、被改过，
> 或者这个账号早就不存在了。拿它直接画面板，等于"先假装你已登录，再慢慢去问后端"，
> 万一张票不能用，用户会先看到一个像模像样的面板、然后被弹走。
>
> 所以刷新 `/home` 时先看到的是一块 `Verifying` 的小闸门，三种结果各有各的去处：
>
> | 情况 | 怎么办 | 用户看到 |
> |---|---|---|
> | token 有效 | 带着它请求 `/api/user/me` | 面板 |
> | token **过期** | **本地就能判定**（payload 里的 `exp` 是明文），路由守卫直接拦下，一步都不进主页 | 登录页 + 一句"登录已过期" |
> | token 被改过 / 不是本服务签发 | 本地判不了，由那次 `GET /api/user/me` 拿到 401，清会话回登录页 | 登录页 + 一句"登录状态异常" |
> | 后端没启动 / 连不上 | 显示 `Unverified` 错误态 + 重试按钮，**保留会话、不登出** | "无法连接后端服务" + 重试 |
>
> 最后一条是刻意的：**连不上后端 ≠ 没登录**。网络抖一下就把人踢出去，是最招人烦的自伤 bug。
> 想亲眼看过期流程：把 `app.jwt.expires-in-seconds` 改成 10，登录后在主页等 10 秒再刷新。

## 前后端怎么连起来

- 前端所有请求走 `src/utils/request.js` 这一个出口，`BASE_URL = 'http://localhost:8080'`。
  ⚠️ 注意它**不含 `/api`** —— 接口的完整路径在 `src/api/*.js` 里写（如 `/api/auth/login`）。换后端地址只需改 `BASE_URL` 这一处。
- 同一个文件负责**自动带上 `Authorization: Bearer <token>`**，以及**凭证失效（code 1005/1006/1007）时清会话并跳回登录页**。
  业务代码不需要关心这两件事。失效原因会一并写到 `sessionStorage`，由登录页读出来显示
  （"登录已过期，请重新登录"），这样用户不会莫名其妙地发现自己回到了登录页。
- 路由守卫在放行 `/home` 之前会**本地解一次 JWT 的 `exp`**，过期的票直接被拦在门外。
  ⚠️ 这不是安全边界（payload 是明文可改的），只是体验优化：能本地判的就地判掉、省一次往返；
  只能由服务端判的（签名被篡改、账号被删）仍然靠 `/api/user/me` 那次核验兜底。
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
- **后端里程碑 ③**：真 JWT + Spring Security。
  - `fake-token-for-admin` 换成 HS256 签名的**真 JWT**（含 `sub` / `iat` / `exp`，服务端验签即认身份）；
  - 引入 Spring Security 7，规则是**默认拒绝**：除了登录 / 注册 / 存活检查 / H2 控制台，其余路径一律要求登录；
  - 自定义 `RestAuthenticationEntryPoint`，让 401 也返回统一响应体（`code` 1005 / 1006 / 1007）；
  - 新增受保护接口 `GET /api/user/me`，作为"整条链路真的通了"的证明；
  - 前端自动带 token、凭证失效自动回登录页；主页新增"服务端确认的身份"面板。
  - 后端测试从 21 个用例涨到 **46 个**，全部通过。
  - 补上了"核验通过前不渲染受保护内容"这道闸门、路由守卫里的本地过期判定，
    以及跳回登录页时的那句原因提示（见上面「打开页面后」一节）。

**还没做的（有意留白）**
- 没有角色 / 权限概念：所有登录用户都是同一个 `ROLE_USER`，还没有 `hasRole(...)` 规则和 403 处理器；
- 没有 refresh token（过期只能重新登录）、没有登出接口（JWT 无状态，服务端没东西可注销）；
- 被作废前 token 一直有效，所以有效期不能设长（当前 1 小时）；
- `/h2-console/**` 目前是放开的，**上线前必须关掉**；
- 主页目前只有两个信息面板，没有真实业务功能。

**可能的下一步**
- 给用户加 `role` 字段（走 `V3__add_role_to_users.sql`）+ `hasRole("ADMIN")` 规则 + `AccessDeniedHandler`，
  把 401 / 403 的区分真正用起来；
- 主页加顶栏用户下拉 / 左侧导航 / 真实功能模块；
- 部署：`web/` 可上 GitHub Pages（注意 `createWebHistory` 需要 404 回退配置），后端另找地方。

## 已知事项

- 远程仓库：`git@github.com:GenjitsutouhiJK/genjitsutouhijk-demo.git`（推送走 SSH）。
  ⚠️ GitHub Pages **只托管静态文件**，`server/`（Spring Boot）无法部署上去；将来若要上线在线版本，
  只能把 `web/` 单独拆成仓库，后端另找地方部署。
- ⚠️ `application.properties` 里的 `app.jwt.secret` 是**开发占位值**，已经进版本库了，所以它不是秘密。
  真实部署必须用环境变量 `APP_JWT_SECRET` 覆盖（Spring Boot 会自动映射，且优先级高于配置文件）。
- `web/dist/` 是 `npm run build` 的产物，可随时删除、不纳入版本管理。
- `server/data/` 是 H2 的数据库文件，首次启动自动生成，已加进 `server/.gitignore`。
  想"重置数据库"直接删掉这个目录、重启即可 —— Flyway 会重新建表并插回种子用户。
  同目录下的 `*.trace.db` 只是诊断日志，可以单独删掉；`.mv.db` 才是数据库本体，**不能删**。
- `spring-boot-starter-flyway` 启动时会警告 `H2 2.4.240 which is newer than the version Flyway has been verified with`。
  **这是正常现象**：Boot 4.1.1 受管的 H2 版本比当前 Flyway 官方验证过的版本新一点，实测迁移功能正常，可以忽略。
- `web/src/assets/` 下的 `hero.png` / `vite.svg` / `vue.svg` 以及 `web/public/icons.svg` 是脚手架残留，没有任何代码引用，可删。
- `server/pom.xml` 里的 **Lombok** 依赖目前仍然完全没用到（DTO 都用 `record`，实体类手写 getter），属脚手架残留，可删。
