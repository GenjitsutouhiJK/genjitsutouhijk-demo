# server（后端 · Spring Boot 4 + Java 21）

`genjitsutouhijk-demo` 项目的后端部分。目前提供**登录接口**和**存活检查接口**，与 [`../web`](../web)（Vite + Vue 3 前端）配合跑通登录闭环。

整项目说明见根目录 [`../README.md`](../README.md)。

## 开发

需要本地装好 **JDK 21** 和 **Maven**。

```bash
mvn spring-boot:run     # 启动服务，默认 http://localhost:8080
mvn test                # 跑测试（MOCK 模式，不占端口，可反复跑）
mvn -DskipTests package # 打包成 target/*.jar
```

> 换端口：`mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080`
>
> `src/main/resources/application.properties` 里只设了应用名，没写 `server.port`，所以用的就是 Spring Boot 默认的 8080。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/auth/login` | 登录。请求体 `{ "username": "admin", "password": "123456" }`，成功返回 `accessToken` / `tokenType`(Bearer) / `expiresIn`(3600) / `username` |
| `GET` | `/hello` | 返回纯文本 `Hello, Spring Boot!`，用来快速验证服务活着 |

### 响应壳

除 `/hello` 外，接口都返回同一个壳：

```json
{ "code": 0, "message": "成功", "data": { "accessToken": "...", "tokenType": "Bearer", "expiresIn": 3600, "username": "admin" } }
```

失败时 `data` 恒为 `null`：

```json
{ "code": 1001, "message": "用户名或密码错误", "data": null }
```

### 错误码

| `code` | 含义 | HTTP 状态码 | 触发场景 |
|---|---|---|---|
| `0` | 成功 | 200 | — |
| `1001` | 用户名或密码错误 | 200 | 账号密码对不上（`ErrorCode.LOGIN_FAILED`） |
| `1002` | 请求参数不合法 | 200 | 用户名/密码为空或只有空格，`message` 会指出是哪个字段 |
| `1003` | 请求体格式错误 | 200 | 没传 body，或 body 不是合法 JSON |
| `404` | 接口不存在 | **404** | 访问了没有对应接口的路径 |
| `9999` | 服务器开小差了 | **500** | 没预料到的异常，细节只写进日志、不给前端 |

⚠️ **两条 HTTP 状态码约定**（前端 `request.js` 依赖它）：

1. **业务结论一律返回 HTTP 200**，成败看 body 里的 `code`。所以"密码错误"是 `200 + code 1001`，不是 HTTP 401。
2. **只有框架层面没走通才用非 200** —— 只有 404（路径不存在）和 500（未预期异常）两种。

## 目录结构

```
src/
├─ main/
│  ├─ java/io/github/genjitsutouhijk/demo/
│  │  ├─ Application.java                     启动类（@SpringBootApplication）
│  │  ├─ config/
│  │  │  └─ CorsConfig.java                   放行 http://localhost:5173 访问 /api/**
│  │  ├─ controller/                          HTTP 契约层
│  │  │  ├─ AuthController.java               POST /api/auth/login
│  │  │  └─ HelloController.java              GET /hello
│  │  ├─ service/
│  │  │  └─ AuthService.java                  业务规则：校验账号密码、签发凭证
│  │  ├─ exception/
│  │  │  ├─ ErrorCode.java                    错误码字典（枚举）
│  │  │  ├─ BusinessException.java            业务异常（表达"规则不允许"，不是 bug）
│  │  │  └─ GlobalExceptionHandler.java       全局异常 → ApiResponse 翻译
│  │  └─ dto/
│  │     ├─ ApiResponse.java                  统一响应壳
│  │     ├─ LoginRequest.java                 登录请求体（字段带 @NotBlank）
│  │     └─ LoginResponse.java                登录响应体
│  └─ resources/
│     └─ application.properties               仅设置应用名
└─ test/java/io/github/genjitsutouhijk/demo/
   ├─ ApplicationTests.java                   上下文能否正常启动
   └─ controller/AuthControllerTest.java      登录接口全部分支（9 个用例）
```

## 一次登录请求走过的路

```
前端 POST /api/auth/login
      ↓
AuthController.login()          ← 只做 HTTP 的事：@RequestBody 反序列化 + @Valid 触发字段校验
      ↓
AuthService.login()             ← 业务规则：核对账号密码；不通过就 throw BusinessException
      ↓
ApiResponse.success(...)        ← 成功：包成统一响应壳
      ↓
（失败时）GlobalExceptionHandler ← 异常冒泡到这里，翻译成 ApiResponse + 对应错误码
```

## 分层约定（新增代码请遵守）

- **Controller 只做三件事**：声明 HTTP 契约（路径 / 方法 / `@RequestBody` / `@Valid`）、把数据交给 Service、把返回值包进 `ApiResponse`。
  **业务规则一律放 Service。** 判断标准：把 HTTP 这层整个换掉（换成定时任务、消息队列、命令行），这行代码还要不要？要 → 放 Service。
- **失败就抛 `BusinessException`，不要在 Controller 里 `return ApiResponse.failure(...)`**，Service 里也不写 `try-catch`。业务分支交给 `GlobalExceptionHandler` 统一翻译。
- **不要往代码里写错误码字面量**（如 `1001`），新错误码只往 `ErrorCode` 枚举里加。已占用：`0` 成功、`1001` 登录失败、`1002` 参数不合法、`1003` 请求体格式错误、`404` 接口不存在、`9999` 兜底。
- **Service 不做「接口 + Impl」拆分**：现在只有一种实现，多写一个接口只增加一次跳转。等真出现第二种实现（短信登录、第三方登录）时再抽。
- **新增接口要照 `AuthControllerTest` 的样子补分支测试**（正确、业务失败、参数不合法、body 异常……），保证每条分支都被钉住。

## 踩过的坑（Spring Boot 4）

网上绝大多数教程还是 Boot 2/3 的，下面几条照抄会直接编译不过或行为不符：

- **`@AutoConfigureMockMvc` 换包了**：Boot 4 在
  `org.springframework.boot.webmvc.test.autoconfigure`，旧教程写的 `...boot.test.autoconfigure.web.servlet` 找不到。
- **Web starter 叫 `spring-boot-starter-webmvc`**，不是 `spring-boot-starter-web`；对应测试包是 `spring-boot-starter-webmvc-test`。
- **`pom.xml` 里的空标签不能删**：`<name/>`、`<licenses><license/></licenses>` 这些看似多余的写法，作用是"用空值覆盖从父 POM 继承来的同名元素"，删掉后父 POM 的信息会继承进来。
- **404 提示要自己补斜杠**：`NoResourceFoundException.getResourcePath()` 返回的路径**不带开头斜杠**（如 `api/nope`），`GlobalExceptionHandler` 里已手工补上。

## 测试

```bash
mvn test
```

用 `@SpringBootTest` + `@AutoConfigureMockMvc`，请求走内存里的 `MockMvc`，**不绑定端口**，所以不会有"端口被别的程序占了"这类和代码无关的失败。

当前共 **10 个用例**（`ApplicationTests` 1 + `AuthControllerTest` 9），覆盖：正确登录、密码错误、字段为空/只有空格、缺字段、无 body、非法 JSON、路径不存在、`/hello` 存活检查。

## 已知的"假"与下一步

**刻意留成假的（练手时逐步替换）**

- 账号 `admin / 123456` 写死在 `AuthService` 的常量里，没有数据库；
- `accessToken` 是拼出来的假字符串（`fake-token-for-admin`），不是真 JWT；
- 没有引入 Spring Security，所有接口都是匿名可访问的；前端拿到 token 后也还没有发回给后端。

**下一步**

- **里程碑 ②**：接数据库（用户表 + 密码加密），把写死的假用户换成真实查询；
- **里程碑 ③**：真 JWT + Spring Security，前端带上 token 并做鉴权拦截；
  ⚠️ 上 Security 时记得自定义 `AuthenticationEntryPoint` 和 `AccessDeniedHandler`，
  否则 Security 拦下的 401/403 不走 `ApiResponse`，前端 `response.json()` 会直接抛异常。

**其它**

- `src/main/resources/` 下没有 `spring-boot-devtools` 相关配置文件；`pom.xml` 里的 **Lombok** 依赖目前完全没用到（DTO 都是 `record`，不需要生成 getter），属脚手架残留，可删。
- `HELP.md` 是 Spring Initializr 生成的官方文档链接模板，与本项目无关，已被 `.gitignore` 排除。
