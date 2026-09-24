package io.github.genjitsutouhijk.demo.security;

import io.github.genjitsutouhijk.demo.dto.ApiResponse;
import io.github.genjitsutouhijk.demo.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * "未登录"的统一出口 —— 自定义 AuthenticationEntryPoint
 *
 * ============ 为什么必须有这个类 ============
 *
 * 里程碑 ① 立下的规矩是：**所有响应体都长一个样** —— {code, message, data}，
 * 前端只要判断 code 就够了。GlobalExceptionHandler 负责把 Controller 抛出的异常
 * 翻译成这个格式。
 *
 * 但是！GlobalExceptionHandler 管不到这里。原因是执行顺序：
 *
 *     请求 → [ 安全过滤器链 ] → DispatcherServlet → Controller → GlobalExceptionHandler
 *                    ↑
 *              401 是在这里产生的
 *
 * @RestControllerAdvice 是 Spring MVC 的机制，而 MVC 在过滤器链的**后面**。
 * 请求还没走到 MVC 就被安全链拦下了，自然没有任何 Controller 异常可处理。
 *
 * 如果不自定义，Spring Security 默认会返回：一个空的响应体 + WWW-Authenticate 头，
 * 或者一张 Tomcat 的 HTML 错误页。前端的 `json.code` 拿到的是 undefined，
 * 界面上就会出现"未知错误" —— 正是里程碑 ① 花力气消灭掉的那种体验。
 *
 * 这个接口的名字有点绕，记住它的语义就行：
 *   EntryPoint = "入口点"，即**匿名用户想进受保护资源时，被挡在门口的那一刻**。
 *   它只在一种情况下触发：请求没通过认证（没带 token / token 无效 / token 过期）。
 *
 *   ⚠️ 与之成对的是 AccessDeniedHandler，负责"**已经登录了，但权限不够**"（403）。
 *      本项目还没有角色概念，所以暂时没写；等加了管理员角色再补。
 *      两者的区别记一句话：401 = 你是谁我不知道；403 = 知道你是谁，但你没资格。
 *
 * ============ 关于 HTTP 状态码 ============
 *
 * 里程碑 ① 的约定是"业务结论一律 HTTP 200，只看 body 里的 code"。
 * 这里**故意破例**用 HTTP 401，理由如下：
 *
 *   200 那个约定针对的是"请求被正常处理、并得出了业务结论"。
 *   而"没通过认证"根本没进业务逻辑 —— 它是**框架层面**就没走通，
 *   和 404（路径不存在）、500（未预期异常）属于同一类。
 *   所以这里和它们保持一致，用真实的 401。
 *
 *   前端不用担心：request.js 依然是"不看状态码、只解析 JSON"，
 *   我们的响应体照样是标准格式，所以取 json.code 一样能拿到 1005/1006/1007。
 *   401 只是让**日志、网关、浏览器调试工具**能一眼看出这是认证失败，
 *   比"200 但 body 说没登录"更符合 HTTP 的语义。
 *
 * 另外还会带上 WWW-Authenticate: Bearer 响应头，这是 RFC 6750 的要求，
 * 用于告诉客户端"这个资源需要 Bearer 凭证"。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    /**
     * 注入的 ObjectMapper 是 Spring Boot 自动配置好的那一个。
     *
     * ⚠️ 注意这个类的包名是 tools.jackson.databind，**不是** com.fasterxml.jackson.databind。
     *    Spring Boot 4 把底层 JSON 库从 Jackson 2 升到了 Jackson 3，包名整体换了前缀。
     *    照着 Boot 2/3 的资料写 import com.fasterxml.jackson.databind.ObjectMapper，
     *    编译能过（因为 JJWT 顺手带进来了 Jackson 2），但注入时会因为
     *    "容器里没有这个类型的 Bean"而启动失败 —— 这是个很迷惑人的坑。
     *
     * 为什么不自己 new 一个 ObjectMapper？因为自动配置那个已经调好了 Java 8 时间类型的支持
     * 等等一堆设置，自己 new 出来的是"裸"的，序列化 LocalDateTime 之类会报错。
     * 统一用容器里那个，才能保证和业务接口输出的 JSON 完全一致。
     */
    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // 失败原因由 JwtAuthenticationFilter 提前记在 request 属性上。
        // 没记（比如压根没带 token）就用默认的"请先登录"。
        Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        ErrorCode errorCode = (attribute instanceof ErrorCode code) ? code : ErrorCode.UNAUTHORIZED;

        log.warn("认证失败：{} {} → {}", request.getMethod(), request.getRequestURI(), errorCode.getMessage());

        // ★ 先定状态码和响应头，最后才写 body。
        //   万一序列化那一步出了意外，客户端收到的至少还是一个干净的 401，
        //   而不是"状态码 200 + 半截 JSON"。
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // 显式指定 charset=UTF-8。不写的话中文提示在某些浏览器/工具里会变成乱码 ——
        // 因为 MediaType.APPLICATION_JSON 默认是 UTF-8，但 HTTP 头里不带 charset 时，
        // 有些客户端会按本地编码猜。
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // RFC 6750 规定的头，告诉客户端"要用 Bearer 方式带凭证"。
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");

        // 复用统一的响应体，前端的处理逻辑不用为 401 单独写一套。
        // data 为 null，和 GlobalExceptionHandler 里失败分支的行为完全一致。
        ApiResponse<Void> body = ApiResponse.failure(errorCode);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
