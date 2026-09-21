package io.github.genjitsutouhijk.demo.controller;

import io.github.genjitsutouhijk.demo.dto.ApiResponse;
import io.github.genjitsutouhijk.demo.dto.LoginRequest;
import io.github.genjitsutouhijk.demo.dto.LoginResponse;
import io.github.genjitsutouhijk.demo.dto.RegisterRequest;
import io.github.genjitsutouhijk.demo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口
 *
 * @RestController 同时干了 @Controller 和 @ResponseBody 两件事：
 *   @Controller   —— 声明这是个处理 HTTP 请求的类，Spring 启动时会扫描它；
 *   @ResponseBody —— 方法返回值不当作"页面名"去查找模板，而是直接序列化成 JSON 写回响应。
 *
 * @RequestMapping("/api/auth") 给这个类里所有接口加统一前缀，
 *   所以下面的 /login 对外实际是 POST /api/auth/login。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * 构造器注入
     *
     * 为什么不在字段上写 @Autowired？
     *   只有一个构造器时，Spring 会自动用它注入，压根不用写注解。
     *   而且字段能是 final —— 对象一造出来依赖就固定了，谁也改不了，
     *   不会出现"忘了注入导致空指针"，写单元测试时还能直接 new 出来。
     *
     * 依赖方向是单向的：Controller 认识 Service，Service 不认识 Controller。
     * 这样 Service 可以被任何地方复用，也不会互相依赖绕成一团。
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录
     *
     * 对比改造前，这个方法从"校验 + 造 token + 拼响应"三件事，
     * 变成了干干脆脆的一句话。它现在只剩三件"只有它才能做"的事：
     *
     *   1. 声明 HTTP 契约：什么路径、什么方法、请求体长什么样（@RequestBody）、
     *      按什么规则校验（@Valid + LoginRequest 上的注解）；
     *   2. 把请求里的数据交给业务层；
     *   3. 把业务层的返回值装进统一响应体。
     *
     * 至于"密码对不对""token 怎么生成"，全在 AuthService 里，
     * 而且校验失败时它会直接抛 BusinessException —— 这里不用写任何 if/else，
     * 更不用写 try-catch，异常会自己飞到 GlobalExceptionHandler。
     *
     * @param request 请求体。@RequestBody 让 Spring 把 JSON 反序列化成 LoginRequest；
     *                @Valid 触发 LoginRequest 字段上的校验注解，不合法就在进方法前被拦下。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request.username(), request.password()));
    }

    /**
     * 注册
     *
     * 对比一下就能看出这个 Controller 现在有多"薄"：
     *   两个接口加起来，去掉注解和注释，真正干活的只有两行 return。
     *   业务规则的差异（注册要先查用户名占没占用、密码要加密后再存）
     *   全在 AuthService 里，这里只负责告诉 Spring"什么路径、收什么、怎么校验"。
     *
     * 这也是"加接口"应该有的手感：
     *   如果你加一个新接口时，需要往 Controller 里塞业务代码，
     *   那说明有东西本该放在 Service 里。
     *
     * 关于返回值：注册成功后同时把登录凭证发回去，相当于"注册即登录"，
     * 前端拿到就能直接进主页，不用再调一次 /login。
     * 返回的结构和登录完全一样（LoginResponse），前端可以走同一套处理逻辑。
     *
     * @param request 请求体，字段上的 @NotBlank / @Size 由 @Valid 触发校验
     * @throws io.github.genjitsutouhijk.demo.exception.BusinessException 用户名已被占用（code 1004）
     */
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request.username(), request.password()));
    }
}
