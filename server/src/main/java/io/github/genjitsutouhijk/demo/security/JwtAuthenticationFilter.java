package io.github.genjitsutouhijk.demo.security;

import io.github.genjitsutouhijk.demo.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 把请求头里的 JWT 翻译成 Spring Security 认识的"已登录身份"
 *
 * ============ 它在整条链路里的位置 ============
 *
 *   浏览器请求
 *      ↓
 *   [ JwtAuthenticationFilter ]   ← 就是这个类。看 Authorization 头，验 token，填上下文
 *      ↓
 *   [ 授权过滤器 ]                 ← 看你 SecurityConfig 里写的规则：这个路径要不要登录
 *      ↓
 *   [ Controller ]                ← 到这里时，"当前用户是谁"已经确定了
 *
 * 打个比方：这个过滤器是门口的保安，负责看证件、确认"你是张三"；
 * SecurityConfig 里的规则则是各道门上的牌子，写着"此门仅员工可入"。
 * 两件事分开：**认证**（你是谁）和**授权**（你能不能进）—— 这是安全领域的两个基本概念。
 *
 * ============ ⚠️ 为什么这个类没有 @Component ============
 *
 * 这是 Spring Boot 上一个非常经典的坑，务必记住：
 *
 *   Spring Boot 会把容器里**所有 Filter 类型的 Bean**自动注册到 Servlet 容器上。
 *   如果这个类既写了 @Component、又通过 addFilterBefore 加进了安全链，
 *   那么每个请求它都会**执行两遍** —— 一次在安全链里，一次作为普通 Servlet 过滤器。
 *
 *   现在这个过滤器只读 token、幂等地设置上下文，跑两遍看起来"也没出问题"，
 *   但等哪天你在这里面加了"记录登录日志""扣减次数"之类的副作用，就会莫名其妙翻倍。
 *
 *   所以正确做法是：**不给它 @Component**，由 SecurityConfig 手动 new 出来，
 *   这样容器不知道它的存在，只有安全链知道。见 SecurityConfig。
 *
 * ============ 另一个原则：这个类永远不能抛异常 ============
 *
 * 过滤器跑在 DispatcherServlet **之前**，所以 @RestControllerAdvice（GlobalExceptionHandler）
 * 根本管不到它 —— 那套统一异常处理只对 Controller 抛出的异常生效。
 * 一旦这里抛出异常，用户看到的是 Tomcat 那张丑陋的 HTML 错误页，而不是我们的 JSON。
 *
 * 因此这里的策略是：**发现问题只做标记，不中断请求**。
 * 把"为什么失败"记在 request 属性上，继续往下走；等授权过滤器发现"没有身份"时，
 * 再把请求交给 RestAuthenticationEntryPoint，由它统一输出 JSON。
 * 这样"没带 token"和"带了坏 token"就能给出不同的提示，而代码路径只有一条。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 用来在 request 上传递"这次认证为什么失败"的属性名。
     * 用 public static final 暴露出来，是为了让 RestAuthenticationEntryPoint 不用手抄字符串 ——
     * 两边写死同一个字面量，改一处忘一处就会变成"永远是默认提示"这种难查的问题。
     */
    public static final String AUTH_ERROR_ATTRIBUTE = "genjitsutouhijk.auth.error";

    /** 约定的请求头名称。Bearer 是行业惯例，意思是"持票人"。 */
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * OncePerRequestFilter 保证"一次请求里只跑一遍"，即使 forward/include 也不会重复执行。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        // 没带 token 就什么都不做，直接放行。
        // ★ 注意这里**不能**直接返回 401 —— 因为登录、注册这些接口本来就不该带 token，
        //   如果在这里拦下来，用户就永远登不进来了。
        //   "这个接口要不要登录"是授权阶段的事，由 SecurityConfig 里的规则决定。
        if (token != null) {
            authenticate(token, request);
        }

        // 无论上面成没成功，都继续往下走。
        filterChain.doFilter(request, response);
    }

    /**
     * 从 Authorization 头里把 token 抠出来
     *
     * 期望的格式是 RFC 6750 规定的：Authorization: Bearer xxx.yyy.zzz
     * 格式不对就当作"没带"，返回 null，交给后面的授权规则去拒绝。
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }

        // 注意用 substring 而不是 replace —— 万一把 "Bearer" 出现在 token 中间就不对了。
        // trim() 是为了容忍 "Bearer  xxx"（多敲了一个空格）这种手抖。
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 验 token，成功就把身份放进 SecurityContext
     *
     * SecurityContext 是什么？可以理解成"这次请求的临时口袋"，
     * 里面装着"当前登录的是谁 + 有什么权限"。Controller 想要当前用户可以随时从里面拿。
     * 它的生命周期只有一次请求 —— 请求结束就被清空，所以**天然不会串号**，
     * 这和"用成员变量存当前用户"有本质区别（后者在并发下会把别人的身份发给用户）。
     */
    private void authenticate(String token, HttpServletRequest request) {
        try {
            String username = jwtService.parseUsername(token);

            // UsernamePasswordAuthenticationToken 的三个参数：
            //   principal   —— 身份主体。这里放用户名；真实项目里可能放一个完整的 User 对象。
            //   credentials —— 密码。走 token 认证时用不到，固定 null。
            //                  ★ 更重要的是：认证通过后 Spring 会主动把它擦掉，
            //                    避免密码在内存里被别处读到。
            //   authorities —— 权限清单。本项目还没有角色概念，先一律给 ROLE_USER。
            //                  等以后要区分管理员，就改成从数据库读真实角色。
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    username,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (ExpiredJwtException ex) {
            // 过期是**最常见**的情况：用户开了页面去泡杯咖啡，回来点一下就这样了。
            // 所以单独标记，前端可以据此静默跳回登录页，而不是弹一个"系统错误"吓唬人。
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.TOKEN_EXPIRED);

        } catch (JwtException | IllegalArgumentException ex) {
            // 签名不对、格式不对、加密算法不对、token 是空串……统统归到"凭证无效"。
            // 这里**故意不细分**：对客户端来说补救动作都是"重新登录"，
            // 而把"签名错了"还是"格式错了"告诉外部，只会泄露实现细节。
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.TOKEN_INVALID);
        }

        // ★ 这里不再写 catch (Exception)，也不重新抛出 —— 见类注释里"永远不能抛异常"那一节。
    }
}
