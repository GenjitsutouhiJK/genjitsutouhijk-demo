package io.github.genjitsutouhijk.demo.controller;

import io.github.genjitsutouhijk.demo.dto.ApiResponse;
import io.github.genjitsutouhijk.demo.dto.UserProfile;
import io.github.genjitsutouhijk.demo.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户资料接口
 *
 * ============ 这个接口存在的意义 ============
 *
 * 里程碑 ③ 加的是一条"认证链"。怎么证明它真的在工作？
 * 光看登录返回的 token 变成三段式还不够 —— 那只能说明**签发**是对的。
 *
 * 必须有一个**受保护的接口**，用"带 token 能进、不带 token 进不来"来证明
 * 整条链路（签发 → 前端保存 → 带上 → 过滤器解析 → 授权通过 → 业务拿得到用户）是通的。
 * 这个接口就是干这个用的，顺便还能给主页提供"当前登录用户"的真实数据。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 取当前登录用户的资料
     *
     * ============ Authentication 参数是从哪来的？ ============
     *
     * 我们没有在前端看到任何地方传它，也没有在方法体里 new 它。它是 Spring MVC 的
     * "参数解析器"自动塞进来的：
     *
     *   请求头 Authorization: Bearer xxx.yyy.zzz
     *        ↓  JwtAuthenticationFilter 验签通过
     *   SecurityContextHolder.getContext().setAuthentication(...)   ← 身份被放进"本次请求的口袋"
     *        ↓  授权规则 anyRequest().authenticated() 发现口袋里有东西，放行
     *   Controller 方法被调用，Spring 检测到参数类型是 Authentication，
     *   就从 SecurityContextHolder 里把当前身份取出来递给你
     *
     * 于是 authentication.getName() 拿到的就是 token 里的用户名。
     *
     * ★ 这个设计的好处：Controller 完全不需要知道"认证是怎么做的"，
     *   它只声明"我要当前用户"。将来把 JWT 换成 OAuth2 或短信验证码登录，
     *   这个方法一个字都不用改。
     *
     * ⚠️ 那能不能不用参数，直接在方法体里写 SecurityContextHolder.getContext().getAuthentication()？
     *    可以，效果完全一样。但写成参数有两个好处：
     *      1. 方法的依赖一目了然 —— 光看签名就知道"这个接口需要当前用户"；
     *      2. 更容易测试 —— 单元测试里可以直接传一个 Authentication 进去，不用去摆弄静态的 Holder。
     *
     * 这个接口不需要在 SecurityConfig 里单独配规则：它没被任何 permitAll 匹配到，
     * 所以自动落进 anyRequest().authenticated() 那条 —— 这正是"默认拒绝"的好处。
     */
    @GetMapping("/me")
    public ApiResponse<UserProfile> me(Authentication authentication) {
        return ApiResponse.success(userService.getProfile(authentication.getName()));
    }
}
