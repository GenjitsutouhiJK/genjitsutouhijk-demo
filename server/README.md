# server（后端 · Spring Boot 4 + Java 21）

`genjitsutouhijk-demo` 项目的后端部分。提供**登录**、**注册**和**存活检查**三个接口，数据存在 H2 数据库里，与 [`../web`](../web)（Vite + Vue 3 前端）配合跑通登录闭环。

整项目说明见根目录 [`../README.md`](../README.md)。

## 开发

需要本地装好 **JDK 21** 和 **Maven**。**不需要安装任何数据库**。

```bash
mvn spring-boot:run     # 启动服务，默认 http://localhost:8080
mvn test                # 跑测试（MOCK 模式 + 内存数据库，不占端口）
mvn -DskipTests package # 打包成 target/*.jar
```

> 换端口：`mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080`
>
> `src/main/resources/application.properties` 里没写 `server.port`，所以用的就是 Spring Boot 默认的 8080。

## 数据库

用的是 **H2 的文件模式**：整个数据库就是一个文件，像一个压缩包一样跟着项目走，
不用装 MySQL、不用起服务、不用管账号权限。代价是它只适合单人开发练手，不适合多人同时写。

### 三件套各管什么（这个分工很重要）

| 组件 | 管什么 | 具体在哪 |
|---|---|---|
| **Flyway** | **建表、改表**。用带版本号的 SQL 脚本描述表结构，启动时按顺序执行并记录哪些跑过了 | `src/main/resources/db/migration/V*.sql` |
| **Hibernate (JPA)** | **把 Java 对象和表互相转换**。查出来是 `User` 对象，存进去是 `INSERT` | `entity/User.java` + `repository/UserRepository.java` |
| **H2** | **真的存数据** | `server/data/genjitsutouhijk-demo.mv.db`（首次启动自动生成） |

为什么不让 Hibernate 自动建表（`ddl-auto=update`）？
因为它会"跟着 Java 类悄悄改表"，你既看不到它执行了什么，也没法把某次改动单独拿出来部署。
所以这里设成 **`validate`**：表由 Flyway 建，Hibernate 只负责**检查实体类和真实表结构对不对得上**，
对不上就启动失败。这条规则会强迫你保持 SQL 脚本和 Java 类一致。

### 怎么看表里的数据

启动服务后浏览器打开 **<http://localhost:8080/h2-console>**：

| 字段 | 填什么 |
|---|---|
| JDBC URL | `jdbc:h2:file:./data/genjitsutouhijk-demo` |
| User Name | `sa` |
| Password | 留空 |

进去之后左侧能看到 `USERS` 和 `FLYWAY_SCHEMA_HISTORY` 两张表，直接双击就能看数据、写 SQL。

### 想重置数据库

删掉 `server/data/` 整个目录，重启服务即可 —— Flyway 会重新建表、并把种子用户插回去。

### 以后想换成 MySQL / PostgreSQL

改 `application.properties` 里的三行就够，**Java 代码一行都不用动**：

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=你的用户名
spring.datasource.password=你的密码
```

外加在 `pom.xml` 里把 H2 驱动换成对应驱动（版本照样不用写，由 Spring Boot 的 BOM 管）。
⚠️ 换库时还要补一个 Flyway 的数据库模块，比如 PostgreSQL 要加 `org.flywaydb:flyway-database-postgresql`。
H2 不需要这个模块，这就是 BOM 里能查到 `flyway-database-postgresql` 却查不到 `flyway-database-h2` 的原因。

## 接口

| 方法 | 路径 | 需要 token | 说明 |
|---|---|---|---|
| `POST` | `/api/auth/login` | 否 | 登录。请求体 `{ "username": "admin", "password": "123456" }` |
| `POST` | `/api/auth/register` | 否 | 注册。请求体 `{ "username": "alice", "password": "alice-pass" }`（用户名 3–20 位、密码 6–32 位）。成功后**直接返回登录凭证**（注册即登录） |
| `GET` | `/api/user/me` | **是** | 返回当前登录用户的资料（`id` / `username` / `createdAt`）。身份取自 token，不接受前端传参 |
| `GET` | `/hello` | 否 | 返回纯文本 `Hello, Spring Boot!`，用来快速验证服务活着 |

> 受保护接口的调用方式：请求头带上 `Authorization: Bearer <accessToken>`。
> 规则是"默认拒绝"—— 没在 `SecurityConfig` 里明确放开的路径，一律要求登录。

登录和注册都返回同一个结构（前端可以走同一套处理逻辑）：

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc1ODU5MTIzNCwiZXhwIjoxNzU4NTk0ODM0fQ.3vQ9xK...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "username": "admin"
  }
}
```

`accessToken` 是真正的 JWT：三段用 `.` 连接，`header.payload.signature`。
中间那段只是 Base64 编码（**不是加密**），贴到任何 JWT 解析网站都能看到 `sub`（用户名）、`iat`（签发时间）、`exp`（过期时间）——
所以**不要往里面放密码之类的敏感信息**。安全性来自第三段的签名，它保证内容没被改过。

失败时 `data` 恒为 `null`：

```json
{ "code": 1001, "message": "用户名或密码错误", "data": null }
```

### 错误码

| `code` | 含义 | HTTP 状态码 | 触发场景 |
|---|---|---|---|
| `0` | 成功 | 200 | — |
| `1001` | 用户名或密码错误 | 200 | 账号不存在**或**密码不对（两者故意用同一个码，防用户名枚举，见下） |
| `1002` | 请求参数不合法 | 200 | 字段为空 / 只有空格 / 长度不合要求，`message` 会指出是哪个字段 |
| `1003` | 请求体格式错误 | 200 | 没传 body，或 body 不是合法 JSON |
| `1004` | 用户名已被占用 | 200 | 注册时用了别人已注册的用户名 |
| `1005` | 请先登录 | **401** | 没带 `Authorization` 头去访问受保护接口 |
| `1006` | 登录已过期 | **401** | 带了 token，但已经超过 `exp` |
| `1007` | 登录凭证无效 | **401** | token 被改过 / 不是本服务签发的 / 格式不对 |
| `404` | 接口不存在 | **404** | 访问了没有对应接口的路径（**未登录时会是 401**，见下） |
| `9999` | 服务器开小差了 | **500** | 没预料到的异常，细节只写进日志、不给前端 |

⚠️ **两条 HTTP 状态码约定**（前端 `request.js` 依赖它）：

1. **业务结论一律返回 HTTP 200**，成败看 body 里的 `code`。所以"密码错误"是 `200 + code 1001`，不是 HTTP 401。
2. **只有框架层面没走通才用非 200** —— `401`（认证失败）、`404`（路径不存在）、`500`（未预期异常）。
   注意这三种的响应体**依然是** `{code, message, data}` 统一格式，所以前端照旧只判断 `json.code` 就行。

⚠️ **上表的 1005 / 1006 / 1007 为什么会是 401，而不是像 1001 那样用 200？**
"密码错误"是业务层得出的结论（请求被正常处理了，只是结论是失败），所以走 200；
而"没通过认证"发生在**安全过滤器链**里，请求压根没进 Controller —— 属于框架层面没走通，
和 404 / 500 是同一类，因此用真实的 401。详见 `security/RestAuthenticationEntryPoint.java` 的注释。

⚠️ **为什么"账号不存在"和"密码错误"是同一个错误码？**
如果分开提示，攻击者就能拿一堆用户名去批量试探，根据返回值区分出"这个账号存在"和"不存在"，
免费把系统里有哪些用户名列出来。这叫**用户名枚举**。统一提示"用户名或密码错误"就什么都推断不出来。
`AuthControllerTest` 里有一条断言专门钉住这一点——哪天有人"好心"改成更友好的提示，测试会立刻变红。

⚠️ **未登录访问一个不存在的路径，拿到的是 401 而不是 404。**
执行顺序是「安全过滤器链 → DispatcherServlet → 路由匹配」，安全链在路由**之前**。
所以没登录时请求根本走不到"发现路径不存在"那一步。想看到真正的 404，先登录再试。

## 目录结构

```
src/
├─ main/
│  ├─ java/io/github/genjitsutouhijk/demo/
│  │  ├─ Application.java                     启动类（@SpringBootApplication + 排除默认用户自动配置）
│  │  ├─ config/                              装配层：把组件接起来
│  │  │  ├─ CorsConfig.java                   放行 http://localhost:5173 访问 /api/**
│  │  │  ├─ PasswordConfig.java               密码加密器（BCrypt）
│  │  │  ├─ JwtProperties.java                读取 app.jwt.* 配置（密钥、有效期）
│  │  │  └─ SecurityConfig.java               ★ 安全规则总表（Lambda DSL）
│  │  ├─ security/                            安全机制实现
│  │  │  ├─ JwtService.java                   JWT 的签发与解析（HS256）
│  │  │  ├─ JwtAuthenticationFilter.java      把 Authorization 头里的 token 翻译成登录身份
│  │  │  └─ RestAuthenticationEntryPoint.java 未登录时的统一出口，输出 ApiResponse 格式的 401
│  │  ├─ controller/                          HTTP 契约层
│  │  │  ├─ AuthController.java               POST /api/auth/login、/register
│  │  │  ├─ UserController.java               GET /api/user/me（受保护）
│  │  │  └─ HelloController.java              GET /hello
│  │  ├─ service/
│  │  │  ├─ AuthService.java                  业务规则：校验密码、注册、签发凭证
│  │  │  └─ UserService.java                  业务规则：取当前用户资料
│  │  ├─ repository/
│  │  │  └─ UserRepository.java               数据访问层（Spring Data 自动生成实现，没有 Impl）
│  │  ├─ entity/
│  │  │  └─ User.java                         实体类，对应 users 表
│  │  ├─ exception/
│  │  │  ├─ ErrorCode.java                    错误码字典（枚举）
│  │  │  ├─ BusinessException.java            业务异常（表达"规则不允许"，不是 bug）
│  │  │  └─ GlobalExceptionHandler.java       全局异常 → ApiResponse 翻译
│  │  └─ dto/
│  │     ├─ ApiResponse.java                  统一响应壳
│  │     ├─ LoginRequest.java                 登录请求体（字段带 @NotBlank）
│  │     ├─ LoginResponse.java                登录 / 注册响应体
│  │     ├─ RegisterRequest.java              注册请求体（@NotBlank + @Size）
│  │     └─ UserProfile.java                  用户资料（**特意不含 passwordHash**）
│  └─ resources/
│     ├─ application.properties               数据源 / JPA / Flyway / H2 控制台 / JWT 配置
│     └─ db/migration/
│        ├─ V1__create_users_table.sql        建 users 表
│        └─ V2__insert_demo_user.sql          插入种子用户 admin
└─ test/
   ├─ java/io/github/genjitsutouhijk/demo/
   │  ├─ ApplicationTests.java                上下文能否启动（含 Flyway、JPA 校验）
   │  ├─ controller/AuthControllerTest.java   认证接口全部分支（15 个用例）
   │  ├─ controller/SecurityIntegrationTest.java  安全链与受保护接口（14 个用例）
   │  ├─ security/JwtServiceTest.java         JWT 签发/解析/过期/伪造（12 个用例，纯单元测试）
   │  └─ repository/UserRepositoryTest.java   数据访问层真实数据库验证（5 个用例）
   └─ resources/application-test.properties   测试专用配置：内存数据库 + 独立 JWT 密钥
```

> 关于 `config/` 和 `security/` 的分工：`config/` 只负责"把组件接起来"（谁用什么、规则是什么），
> `security/` 放安全机制的实现本身。所以 `SecurityConfig` 在 `config/`，而它用的过滤器和 JWT 工具在 `security/`。

## 一次登录请求走过的路

```
前端 POST /api/auth/login
      ↓
AuthController.login()          ← HTTP 的事：@RequestBody 反序列化 + @Valid 触发字段校验
      ↓
AuthService.login()             ← 业务规则：查库比对密码；失败就 throw BusinessException
      ↓
UserRepository.findByUsername() ← 数据访问：Spring Data 自动生成 SQL，查不到返回空 Optional
      ↓
密码比对 passwordEncoder.matches(明文, 库里哈希)
      ↓
ApiResponse.success(...)        ← 成功：包成统一响应壳
      ↓
（失败时）GlobalExceptionHandler ← 异常冒泡到这里，翻译成 ApiResponse + 对应错误码
```

## 认证是怎么工作的（里程碑 ③）

### 一次带 token 的请求走过的路

```
前端 GET /api/user/me
  请求头 Authorization: Bearer eyJhbGci...
      ↓
JwtAuthenticationFilter        ← 抠出 token，用密钥验签、检查过期
      ↓ 验过了：把"当前用户是谁"放进 SecurityContextHolder（本次请求的临时口袋）
      ↓ 没过：不抛异常，只把失败原因记在 request 属性上，继续往下走
授权规则（SecurityConfig）      ← /api/user/me 没被 permitAll 匹配 → 要求已认证
      ↓ 口袋里有东西 → 放行
      ↓ 口袋是空的 → 转交给 RestAuthenticationEntryPoint
RestAuthenticationEntryPoint   ← 输出 {"code":1005/1006/1007,...} + HTTP 401
      ↓ 放行的情况
UserController.me(Authentication)  ← Spring 自动把当前身份作为参数递进来
      ↓
UserService.getProfile(username)   ← 拿这个名字去数据库查资料
      ↓
ApiResponse.success(UserProfile)
```

### 为什么要用过滤器 + EntryPoint 两个类，不能合并成一个？

因为它们解决的是**两个不同的时刻**：

- **过滤器**在请求"还没被判断要不要登录"的时候跑。它不能自己返回 401 —— 因为登录、注册这些接口本来就不该带 token，
  如果在过滤器里一律拦下，用户就永远登不进来了。所以它只负责"认人"，认不出来就默默放手。
- **EntryPoint**在请求"被判定需要登录、但口袋是空的"那一刻才触发。

这也解释了为什么过滤器**不能抛异常**、也不能直接写响应 —— 它的职责边界很窄，只做认证，不做授权。

### token 里到底装了什么？

```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc1ODU5MTIzNCwiZXhwIjoxNzU4NTk0ODM0fQ . 3vQ9xK...
└──── header ────┘   └──────────────── payload ────────────────────────────────┘   └─ signature ─┘
     用了哪个算法              sub=用户名、iat=签发时间、exp=过期时间                用密钥算出来的签名
     （HS256）                ↑ 只是 Base64，不是加密，谁都能解开看                  ↑ 改一个字就验不过
```

**记住三句话**：

1. payload **不是加密的** —— 所以里面不能放密码、手机号这类敏感信息；
2. 安全性来自 **signature** —— 它保证内容没被篡改，也能证明"这张票是本服务签发的"；
3. 服务端**什么都不用存** —— 这叫无状态。代价是签发出去的 token 无法单独作废，只能等它过期，所以有效期不能太长。

### 密钥从哪来

`application.properties` 里的 `app.jwt.secret`，由 `config/JwtProperties.java` 读进 `security/JwtService.java`。

⚠️ **仓库里那个值是开发占位值，不是秘密**（已经进版本库了）。真实部署时必须换掉，且**不要写进配置文件**，
用环境变量覆盖即可（Spring Boot 会自动把 `APP_JWT_SECRET` 映射到 `app.jwt.secret`，且优先级高于配置文件）：

```bash
set APP_JWT_SECRET=<用 openssl rand -base64 48 生成的随机串>   # Windows
export APP_JWT_SECRET=<同上>                                    # macOS / Linux
```

密钥短于 32 个字符时，`JwtService` 会在**启动阶段**直接抛 `WeakKeyException` 让应用起不来 ——
这是刻意的"早失败"：弱密钥等于没有签名，宁可启动失败也不能悄悄用弱密钥上线。

### 还没做的部分（有意留白）

- **没有任何角色/权限概念**：所有登录用户拿到的是同一个 `ROLE_USER`，`SecurityConfig` 里没有 `hasRole(...)` 规则。
- **没有 `AccessDeniedHandler`**：它负责"已登录但权限不够"的 403，等有了管理员角色再加。
  401 和 403 的区别记一句：**401 = 不知道你是谁；403 = 知道你是谁，但你没资格**。
- **没有 refresh token**：access token 过期就只能重新登录。业界常见做法是"短 access token + 长 refresh token"。
- **没有登出接口**：JWT 无状态，服务端没记录可注销。前端丢掉 token 就等于登出 ——
  但要清楚**那张 token 本身仍然有效直到过期**。真要立刻作废得维护黑名单（通常放 Redis）。
- **`/h2-console/**` 是放开的**：这是本地开发的便利，不是生产配置。上线前必须关掉控制台并删掉那条规则。

## 分层约定（新增代码请遵守）

- **Controller 只做三件事**：声明 HTTP 契约（路径 / 方法 / `@RequestBody` / `@Valid`）、把数据交给 Service、把返回值包进 `ApiResponse`。
  **业务规则一律放 Service。** 判断标准：把 HTTP 这层整个换掉（换成定时任务、消息队列、命令行），这行代码还要不要？要 → 放 Service。
- **失败就抛 `BusinessException`，不要在 Controller 里 `return ApiResponse.failure(...)`**，Service 里也不写 `try-catch`。业务分支交给 `GlobalExceptionHandler` 统一翻译。
- **不要往代码里写错误码字面量**（如 `1001`），新错误码只往 `ErrorCode` 枚举里加。已占用：`0` 成功、`1001` 登录失败、`1002` 参数不合法、`1003` 请求体格式错误、`1004` 用户名已占用、`1005` 未登录、`1006` 凭证过期、`1007` 凭证无效、`404` 接口不存在、`9999` 兜底。
- **Service 不做「接口 + Impl」拆分**：现在只有一种实现，多写一个接口只增加一次跳转。等真出现第二种实现（短信登录、第三方登录）时再抽。
  但 `PasswordEncoder` 是个例外——它是 Spring Security 提供的接口，抽出来是为了隔离"加密算法"这个选择。
- **过滤器不要标 `@Component`**：Spring Boot 会把容器里所有 `Filter` Bean 自动注册到 Servlet 容器。
  如果它同时又通过 `addFilterBefore` 进了安全链，**每个请求会执行两遍**。
  正确做法是由 `SecurityConfig` 手动 `new`（见 `JwtAuthenticationFilter`）。
- **`security/` 里的过滤器永远不能抛异常**：它跑在 DispatcherServlet 之前，`GlobalExceptionHandler` 管不到它。
  一旦抛出，用户看到的是 Tomcat 的 HTML 错误页。正确做法是把失败原因记在 request 属性上，交给 `RestAuthenticationEntryPoint` 统一输出。
- **不要把实体类直接当接口返回值**：`User` 里有 `passwordHash`，直接返回会把密码哈希发到浏览器。
  对外一律用 `dto/` 里的 record（如 `UserProfile`），从源头上就不包含敏感字段。
  `SecurityIntegrationTest` 里有一条断言专门挡这件事。
- **新增受保护接口不用改 `SecurityConfig`**：它没被任何 `permitAll` 匹配到，会自动落进 `anyRequest().authenticated()`。
  但要**确认**这一点——如果新接口的路径不小心撞上了已有的 `permitAll` 前缀（如 `/api/auth/**`），就会被静默放开。
- **新增表 / 改表只能新加 Flyway 脚本**（`V3__xxx.sql`），**绝不能改已经执行过的 V1 / V2**。
  改了 Flyway 会直接报校验失败，因为它要保证"所有人的数据库都是同一套演变历史"。
- **实体类字段一律显式写 `@Column(name = "...")`**，不要依赖"驼峰自动转下划线"的默认规则——
  默认规则在表名/字段名对不上时报错很难查。
- **`Repository` 只声明方法名，不写实现**：`findByUsername`、`existsByUsername` 这类名字会被 Spring Data 翻译成 SQL。
  方法名写错不会编译失败，而是在启动时报 `No property 'xxx' found`。
- **新增接口要照 `AuthControllerTest` 的样子补分支测试**（正确、业务失败、参数不合法、body 异常……），保证每条分支都被钉住。

## 踩过的坑（Spring Boot 4）

网上绝大多数教程还是 Boot 2/3 的，下面几条照抄会直接编译不过或行为不符：

- **`@AutoConfigureMockMvc` 换包了**：Boot 4 在 `org.springframework.boot.webmvc.test.autoconfigure`，旧教程写的 `...boot.test.autoconfigure.web.servlet` 找不到。
- **Web starter 叫 `spring-boot-starter-webmvc`**，不是 `spring-boot-starter-web`；对应测试包是 `spring-boot-starter-webmvc-test`。
- **Flyway 的 starter 叫 `spring-boot-starter-flyway`**，不再是自己往 pom 里加 `flyway-core`。
- **H2 控制台被拆成了独立模块 `spring-boot-h2console`**。光在配置里写 `spring.h2.console.enabled=true` 是**没用的**，
  不加这个依赖，访问 `/h2-console` 会得到 404 —— 这个坑本项目实际踩到了。
- **启动时 Flyway 会警告** `Using H2 2.4.240 which is newer than the version Flyway has been verified with`。
  这是正常的：Boot 4.1.1 受管的 H2 比 Flyway 官方验证过的版本新一点，实测迁移功能正常，可以忽略。
- **数据库驱动不用写 `<version>`**：H2（2.4.240）、PostgreSQL（42.7.13）、MySQL connector-j（9.7.0）都由 Boot 的 BOM 统一管。
- **`pom.xml` 里的空标签不能删**：`<name/>`、`<licenses><license/></licenses>` 这些看似多余的写法，作用是"用空值覆盖从父 POM 继承来的同名元素"，删掉后父 POM 的信息会继承进来。
- **404 提示要自己补斜杠**：`NoResourceFoundException.getResourcePath()` 返回的路径**不带开头斜杠**（如 `api/nope`），`GlobalExceptionHandler` 里已手工补上。

### 上了 Spring Security 之后新踩的坑

- **JSON 库整体换成了 Jackson 3**。Boot 4 用的是 `tools.jackson.core:jackson-databind`（3.1.5），
  包名不再是 `com.fasterxml.jackson.databind`。写 `RestAuthenticationEntryPoint` 时照抄 Boot 2/3 资料的 import，
  **编译能过**（因为 JJWT 顺手带进来了 Jackson 2），但注入时会因为"容器里没有这个类型的 Bean"而启动失败 —— 很迷惑人。
- **`UserDetailsServiceAutoConfiguration` 换包了**，在 `org.springframework.boot.security.autoconfigure`。
  Boot 4 把自动配置类从 `spring-boot-autoconfigure` 这个大杂烩按模块拆了出去。
  不排除它的话，每次启动控制台都会多一行 `Using generated security password: <随机 UUID>`，
  而且那真的是个能登录的后门账号。本项目在 `Application.java` 上显式 exclude 掉了。
- **自动配置是分模块的 jar**：`spring-boot-security`、`spring-boot-webmvc`、`spring-boot-jackson` ……
  所以找自动配置类的包名，最好直接去 `.m2` 里翻 jar 里的
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，比在网上搜可靠。
- **Spring Security 7 只认 Lambda DSL**：`WebSecurityConfigurerAdapter`（类已删除）、`authorizeRequests()`、`antMatchers()`、
  `.and()` 链式调用全部不可用。搜到的资料里只要出现这几个词，就是至少三个大版本之前的。
- **`authorizeHttpRequests` 默认连 ERROR 派发也一起管**：没登录的请求触发 500 时，
  容器内部转发到 `/error` 会被再判一次"未认证"，于是你查到的是 401，真实原因却是那个 500 的 bug。
  解法是放行 `DispatcherType.ERROR` / `FORWARD`，本项目已在 `SecurityConfig` 里加上。
- **`@Component` 的 Filter 会被注册两次**：Spring Boot 会自动把容器里所有 `Filter` Bean 挂到 Servlet 容器上，
  于是它既在安全链里跑、又作为普通 Servlet 过滤器跑一遍。所以 `JwtAuthenticationFilter` **故意不标 `@Component`**。
- **JJWT 需要自己写版本号**：它不在 Spring Boot 的 BOM 里，`pom.xml` 里用 `<jjwt.version>` 统一管三个包
  （`jjwt-api` / `jjwt-impl` / `jjwt-jackson`）。版本不一致会在运行时抛 `NoSuchMethodError`，很难查。
  另外 `jjwt-jackson` 依赖的是 **Jackson 2**，会和 Boot 4 的 Jackson 3 并存 —— 包名不同，不冲突，只是多几个 jar。

## 测试

```bash
mvn test
```

用 `@SpringBootTest` + `@AutoConfigureMockMvc`，请求走内存里的 `MockMvc`，**不绑定端口**，所以不会有"端口被别的程序占了"这类和代码无关的失败。

测试跑在**独立的内存数据库**上（`@ActiveProfiles("test")` + `src/test/resources/application-test.properties`），
不会弄脏你 `server/data/` 里的开发数据。同时因为测试也要跑 Flyway，所以**每次 `mvn test` 都在验证迁移脚本能跑通**。

当前共 **46 个用例**（全部通过）：

| 测试类 | 用例数 | 覆盖什么 |
|---|---|---|
| `ApplicationTests` | 1 | 上下文启动：数据源、Flyway、JPA 校验、依赖装配、安全链装配 |
| `AuthControllerTest` | 15 | 登录（成功 / 密码错 / 账号不存在 / 字段空 / 纯空格 / 缺字段 / 无 body / 非法 JSON）、注册（成功 / 注册后能登录 / 重名 / 用户名太短 / 密码太短）、未登录访问未知路径、存活检查 |
| `SecurityIntegrationTest` | 14 | 受保护接口的各条分支：无 token(1005) / 坏 token(1007) / 他人密钥签的 token(1007) / 过期 token(1006) / 漏写 `Bearer` 前缀、401 响应体是统一格式且带 `WWW-Authenticate`、登录注册存活接口保持匿名、**资料里不含密码哈希**、未登录 vs 已登录访问未知路径（401 / 404）、默认拒绝 |
| `JwtServiceTest` | 11 | **纯单元测试，不启动 Spring**：签发/解析往返、三段式结构、payload 可被解出（证明未加密）、有效期对外暴露一致、过期被拒、他人密钥签的 token 被拒、payload 被改一个字被拒、垃圾串被拒、密钥太短与缺失都"早失败" |
| `UserRepositoryTest` | 5 | 种子用户可查、**密码存的是 BCrypt 哈希不是明文**、`existsByUsername` 两个方向、`save` 后 id 回填、BCrypt 随机盐 |

> 两种测试各有分工：
> `JwtServiceTest` 是**单元测试** —— 直接 `new` 出被测对象，不启动 Spring，几十毫秒跑完，红了就一定是 `JwtService` 的问题。
> 其余都是**集成测试** —— 真启动容器、真连数据库、真过过滤器链，慢但覆盖面广，红了可能是任何一环。
> 排查问题时先用快的缩小范围，再用慢的确认整体没坏。

## 已知的"假"与下一步

**里程碑 ③ 已完成（原"刻意留成假的"清单已勾掉）**

- ✅ `accessToken` 换成了**真 JWT**：HS256 签名、带 `exp` 过期时间、服务端验签就能认出身份；
- ✅ 接入了 **Spring Security 7**，配置为"默认拒绝"，未登录访问受保护接口得到 401；
- ✅ 自定义了 `RestAuthenticationEntryPoint`，让 401 也走 `ApiResponse` 统一格式；
- ✅ 新增受保护接口 `GET /api/user/me` 作为"整条链路真的通了"的证明；
- ✅ 前端 `request.js` 自动带 `Authorization` 头，凭证失效自动清会话并回登录页。

**还没做的（见上面「认证是怎么工作的」里的"有意留白"）**

- 角色 / 权限（`hasRole`）、`AccessDeniedHandler`(403)、refresh token、登出接口、token 黑名单；
- `/h2-console/**` 目前是放开的，**上线前必须关掉**；
- `CorsConfig` 里的白名单写死了 `http://localhost:5173`（Vite 端口被占会自动改用 5174，那时跨域就失败了）。
  更彻底的做法是开发期用 Vite 的 `server.proxy` 反代 `/api`，前端请求变成同源，CORS 配置就可以删掉。

**下一步（候选）**

- 给用户加 `role` 字段（`V3__add_role_to_users.sql`）+ `hasRole("ADMIN")` 规则 + `AccessDeniedHandler`，
  把 401/403 的区分真正用起来；
- 加"登出"的语义化处理：前端清 token 之外，可选做服务端黑名单；
- 主页加真实业务功能模块。

**其它**

- `pom.xml` 里的 **Lombok** 依赖目前完全没用到（DTO 都是 `record`，实体类手写 getter），属脚手架残留，可删。
- `HELP.md` 是 Spring Initializr 生成的官方文档链接模板，与本项目无关，已被 `.gitignore` 排除。
- `server/data/` 装的是数据库文件，已加进 `.gitignore`，不要提交。
- `server/data/*.trace.db` 是 H2 的诊断日志（出现异常时生成），可以随时删；同目录的 `.mv.db` 才是数据库本体，**不能删**。
