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

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/auth/login` | 登录。请求体 `{ "username": "admin", "password": "123456" }` |
| `POST` | `/api/auth/register` | 注册。请求体 `{ "username": "alice", "password": "alice-pass" }`（用户名 3–20 位、密码 6–32 位）。成功后**直接返回登录凭证**（注册即登录） |
| `GET` | `/hello` | 返回纯文本 `Hello, Spring Boot!`，用来快速验证服务活着 |

登录和注册都返回同一个结构（前端可以走同一套处理逻辑）：

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "accessToken": "fake-token-for-admin",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "username": "admin"
  }
}
```

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
| `404` | 接口不存在 | **404** | 访问了没有对应接口的路径 |
| `9999` | 服务器开小差了 | **500** | 没预料到的异常，细节只写进日志、不给前端 |

⚠️ **两条 HTTP 状态码约定**（前端 `request.js` 依赖它）：

1. **业务结论一律返回 HTTP 200**，成败看 body 里的 `code`。所以"密码错误"是 `200 + code 1001`，不是 HTTP 401。
2. **只有框架层面没走通才用非 200** —— 只有 404（路径不存在）和 500（未预期异常）两种。

⚠️ **为什么"账号不存在"和"密码错误"是同一个错误码？**
如果分开提示，攻击者就能拿一堆用户名去批量试探，根据返回值区分出"这个账号存在"和"不存在"，
免费把系统里有哪些用户名列出来。这叫**用户名枚举**。统一提示"用户名或密码错误"就什么都推断不出来。
`AuthControllerTest` 里有一条断言专门钉住这一点——哪天有人"好心"改成更友好的提示，测试会立刻变红。

## 目录结构

```
src/
├─ main/
│  ├─ java/io/github/genjitsutouhijk/demo/
│  │  ├─ Application.java                     启动类（@SpringBootApplication）
│  │  ├─ config/
│  │  │  ├─ CorsConfig.java                   放行 http://localhost:5173 访问 /api/**
│  │  │  └─ PasswordConfig.java               密码加密器（BCrypt）
│  │  ├─ controller/                          HTTP 契约层
│  │  │  ├─ AuthController.java               POST /api/auth/login、/register
│  │  │  └─ HelloController.java              GET /hello
│  │  ├─ service/
│  │  │  └─ AuthService.java                  业务规则：校验密码、注册、签发凭证
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
│  │     └─ RegisterRequest.java              注册请求体（@NotBlank + @Size）
│  └─ resources/
│     ├─ application.properties               数据源 / JPA / Flyway / H2 控制台配置
│     └─ db/migration/
│        ├─ V1__create_users_table.sql        建 users 表
│        └─ V2__insert_demo_user.sql          插入种子用户 admin
└─ test/
   ├─ java/io/github/genjitsutouhijk/demo/
   │  ├─ ApplicationTests.java                上下文能否启动（含 Flyway、JPA 校验）
   │  ├─ controller/AuthControllerTest.java   认证接口全部分支（15 个用例）
   │  └─ repository/UserRepositoryTest.java   数据访问层真实数据库验证（5 个用例）
   └─ resources/application-test.properties   测试专用配置：换成内存数据库
```

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

## 分层约定（新增代码请遵守）

- **Controller 只做三件事**：声明 HTTP 契约（路径 / 方法 / `@RequestBody` / `@Valid`）、把数据交给 Service、把返回值包进 `ApiResponse`。
  **业务规则一律放 Service。** 判断标准：把 HTTP 这层整个换掉（换成定时任务、消息队列、命令行），这行代码还要不要？要 → 放 Service。
- **失败就抛 `BusinessException`，不要在 Controller 里 `return ApiResponse.failure(...)`**，Service 里也不写 `try-catch`。业务分支交给 `GlobalExceptionHandler` 统一翻译。
- **不要往代码里写错误码字面量**（如 `1001`），新错误码只往 `ErrorCode` 枚举里加。已占用：`0` 成功、`1001` 登录失败、`1002` 参数不合法、`1003` 请求体格式错误、`1004` 用户名已占用、`404` 接口不存在、`9999` 兜底。
- **Service 不做「接口 + Impl」拆分**：现在只有一种实现，多写一个接口只增加一次跳转。等真出现第二种实现（短信登录、第三方登录）时再抽。
  但 `PasswordEncoder` 是个例外——它是 Spring Security 提供的接口，抽出来是为了隔离"加密算法"这个选择。
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

## 测试

```bash
mvn test
```

用 `@SpringBootTest` + `@AutoConfigureMockMvc`，请求走内存里的 `MockMvc`，**不绑定端口**，所以不会有"端口被别的程序占了"这类和代码无关的失败。

测试跑在**独立的内存数据库**上（`@ActiveProfiles("test")` + `src/test/resources/application-test.properties`），
不会弄脏你 `server/data/` 里的开发数据。同时因为测试也要跑 Flyway，所以**每次 `mvn test` 都在验证迁移脚本能跑通**。

当前共 **21 个用例**：

| 测试类 | 用例数 | 覆盖什么 |
|---|---|---|
| `ApplicationTests` | 1 | 上下文启动：数据源、Flyway、JPA 校验、依赖装配 |
| `AuthControllerTest` | 15 | 登录（成功 / 密码错 / 账号不存在 / 字段空 / 纯空格 / 缺字段 / 无 body / 非法 JSON）、注册（成功 / 注册后能登录 / 重名 / 用户名太短 / 密码太短）、404、存活检查 |
| `UserRepositoryTest` | 5 | 种子用户可查、**密码存的是 BCrypt 哈希不是明文**、`existsByUsername` 两个方向、`save` 后 id 回填、BCrypt 随机盐 |

## 已知的"假"与下一步

**刻意留成假的（练手时逐步替换）**

- `accessToken` 还是拼出来的假字符串（`fake-token-for-admin`），**服务端既不记录也不校验它** ——
  前端拿着它回来，后端依然不知道你是谁。这是"假登录"的根源；
- 没有引入 Spring Security，所有接口都是匿名可访问的；
- `CorsConfig` 里的白名单写死了 `http://localhost:5173`（Vite 端口被占会自动改用 5174，那时跨域就失败了）。
  更彻底的做法是开发期用 Vite 的 `server.proxy` 反代 `/api`，前端请求变成同源，CORS 配置就可以删掉。

**下一步**

- **里程碑 ③**：真 JWT + Spring Security，前端带上 token 并做鉴权拦截。
  ⚠️ 上 Security 时记得自定义 `AuthenticationEntryPoint` 和 `AccessDeniedHandler`，
  否则 Security 拦下的 401/403 不走 `ApiResponse`，前端 `response.json()` 会直接抛异常。
  另外 Boot 4 对应的是 **Spring Security 7**，`WebSecurityConfigurerAdapter` 早已移除，配置必须用 Lambda DSL。

**其它**

- `pom.xml` 里的 **Lombok** 依赖目前完全没用到（DTO 都是 `record`，实体类手写 getter），属脚手架残留，可删。
- `HELP.md` 是 Spring Initializr 生成的官方文档链接模板，与本项目无关，已被 `.gitignore` 排除。
- `server/data/` 装的是数据库文件，已加进 `.gitignore`，不要提交。
