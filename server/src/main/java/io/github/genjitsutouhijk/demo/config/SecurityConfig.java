package io.github.genjitsutouhijk.demo.config;

import io.github.genjitsutouhijk.demo.security.JwtAuthenticationFilter;
import io.github.genjitsutouhijk.demo.security.JwtService;
import io.github.genjitsutouhijk.demo.security.RestAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.DispatcherType;

/**
 * 安全规则总表 —— 整个项目"谁可以访问什么"只在这一个文件里写
 *
 * ============ 里程碑 ③ 到底加了多少东西 ============
 *
 * 里程碑 ② 时 pom 里只有 spring-security-crypto，那是个**纯工具包** ——
 * 里面只有一个 BCryptPasswordEncoder，没有任何"拦人"的能力，
 * 所以那时候所有接口都是敞开的，谁都能调。
 *
 * 换成 spring-boot-starter-security 之后，多的是一条**完整的过滤器链**：
 * 请求进入 Controller 之前要经过十几个过滤器，检查跨域、CSRF、登录状态、权限……
 * 而且默认行为是"所有请求都要登录"。
 *
 * 所以这个文件的作用是：把默认行为改写成我们想要的样子。
 *
 * ============ ⚠️ Spring Security 7 的写法跟老教程完全不同 ============
 *
 * 如果你搜到的资料里出现下面任何一个，那都是至少三个大版本之前的写法，抄下来编译不过：
 *   - extends WebSecurityConfigurerAdapter   （类已被删除）
 *   - http.authorizeRequests()                （换成 authorizeHttpRequests()）
 *   - .antMatchers("/xxx")                    （换成 requestMatchers("/xxx")）
 *   - http.csrf().disable().and().xxx()       （.and() 链式已被删除）
 *
 * Spring Security 7 只有一种写法：**每个配置项收一个 Lambda**，
 * 由框架把配置对象递给你，你在 Lambda 里改它。就是下面这种层层嵌套的样子。
 */
@Configuration
@EnableWebSecurity
/*
 * 注册上面的 JwtProperties，让 @ConfigurationProperties 那个 record 真正变成 Bean。
 * 不写这一行，JwtService 构造器要 JwtProperties 时会报"找不到 Bean"。
 * （也可以用 @ConfigurationPropertiesScan 扫包，但显式注册更好读：
 *   打开这个文件就知道配置从哪来的。）
 */
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /**
     * 安全过滤器链
     *
     * ★ 最重要的一句话：下面这些规则**从上往下匹配，第一条命中就结束**。
     *   所以"具体路径"必须写在"宽泛路径"前面。
     *   顺序写反了不会报错，只会静默地放行本该保护的接口 —— 安全配置最危险的地方就在这里。
     *   下面每一条都配了"为什么放开"，因为一个没有理由的 permitAll 看起来就像疏忽。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint) throws Exception {

        // 手动 new，而不是把 JwtAuthenticationFilter 做成 @Component。
        // 原因见那个类的注释：@Component 的 Filter 会被 Spring Boot 自动注册到 Servlet 容器，
        // 结果就是每个请求执行两遍。这样写，容器只知道它存在于安全链里。
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService);

        http
                // ---------- CSRF ----------
                // CSRF（跨站请求伪造）攻击的前提是"浏览器会自动带上 Cookie"。
                // 我们的 token 是前端手动放在 Authorization 头里的，浏览器不会自动带，
                // 攻击者的站点拿不到，所以这个攻击不成立，可以关掉。
                // ⚠️ 这是**因为无状态才敢关**。如果哪天改成用 Cookie 存 session，
                //    这一行必须改回来，否则就会留下 CSRF 漏洞。
                .csrf(csrf -> csrf.disable())

                // ---------- CORS ----------
                // 前后端不同源（Vue 在 5173，后端在 8080），必须处理跨域。
                // withDefaults() 表示"沿用项目里已有的跨域配置" ——
                // 也就是 CorsConfig 里那份（allowedOrigins 只放 localhost:5173）。
                // 安全链自己也要认得这份配置，否则浏览器发 OPTIONS 预检请求时，
                // 请求会在到达 CorsConfig 之前就被安全链拦下，前端看到的是跨域错误，
                // 实际原因却是"没登录" —— 这种错最难查。
                .cors(Customizer.withDefaults())

                // ---------- 会话 ----------
                // STATELESS = 服务端不创建、不使用 HttpSession。
                // 这正是 JWT 的意义所在：状态都在 token 里，服务端换个实例照样能验。
                // 副作用：登录接口不会再给你种 JSESSIONID 那个 Cookie 了。
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ---------- 响应头 ----------
                // 默认会加 X-Frame-Options: DENY，禁止页面被嵌进 iframe。
                // 而 H2 控制台本身就是个 iframe 页面，不放开会白屏。
                // 仅为了本地开发方便；生产环境不该开 H2 控制台，这一行也应跟着去掉。
                .headers(headers -> headers.frameOptions(frame -> frame.disable()))

                // ---------- 授权规则（本文件的核心）----------
                .authorizeHttpRequests(auth -> auth
                        /*
                         * 放行"错误派发"。
                         *
                         * 这是一个非常隐蔽的坑，值得说清楚：
                         * 请求在 Servlet 容器里有多种"派发类型"（DispatcherType）：
                         *   REQUEST —— 浏览器直接发来的正常请求
                         *   ERROR   —— 出了异常后，容器**再内部转发**到 /error 处理
                         *   FORWARD —— 内部 forward
                         *
                         * Spring Security 6 起，authorizeHttpRequests 默认**连 ERROR 派发也一起管**。
                         * 结果就是：一个没登录的请求触发了 500，容器转去 /error，
                         * 而 /error 不匹配下面的任何 permitAll → 又被判"未认证" → 401。
                         * 于是你查到的是 401，真实原因却是一个 500 的 bug，方向完全被带偏。
                         * 放行 ERROR 和 FORWARD，就等于告诉安全链"内部转发不算新请求"。
                         */
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()

                        // 登录 / 注册：必须匿名可访问。
                        // 反过来说，如果忘了这一条，用户会陷入"想登录但必须先登录"的死循环。
                        .requestMatchers("/api/auth/**").permitAll()

                        // 存活检查接口：给部署脚本、监控探针用的，不带业务数据，放开。
                        // 如果哪天它开始返回敏感信息，这条必须删掉。
                        .requestMatchers(HttpMethod.GET, "/hello").permitAll()

                        // H2 网页控制台：本地开发看数据用。
                        // ⚠️ 它没有任何认证，是本地开发的便利，不是生产配置。
                        //    真要上线，必须把 spring.h2.console.enabled 关掉并删掉这一行。
                        .requestMatchers("/h2-console/**").permitAll()

                        /*
                         * ★ 默认拒绝：没在上面对上号的路径，一律要求登录。
                         *
                         * 这一条是整个配置里最重要的设计决定 —— 注意它写的是 authenticated()
                         * 而不是 permitAll()。差别在几个月后才显现：
                         *   新同事加了一个 /api/user/delete 接口，忘了在这里配规则。
                         *   默认拒绝 → 未登录访问得到 401，问题立刻暴露，他会去补一条规则；
                         *   默认放开 → 接口直接对全世界开放，而且没有任何报错，谁都不会发现。
                         *
                         * 所以永远以 .authenticated() 结尾，别用 .permitAll()。
                         */
                        .anyRequest().authenticated()
                )

                // ---------- 未登录时怎么办 ----------
                // 换成我们自己的"出口"，让它输出统一格式的 JSON（见那个类的注释）。
                // 不配这一行，前端拿到的是空 body，json.code 是 undefined。
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))

                // ---------- 把 JWT 过滤器插进链里 ----------
                // addFilterBefore(自己的过滤器, 某个标准过滤器) = "插在它前面"。
                // 为什么插在 UsernamePasswordAuthenticationFilter 前面？
                //   因为那个过滤器负责处理表单登录（username/password 参数），
                //   而我们要在它之前先把 JWT 认出来 ——
                //   否则等它跑完发现"没有表单参数"，上下文里还是空的，就白跑一趟了。
                // 位置只是"就近放"，真正决定顺序的是过滤器内部声明的 order。
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
