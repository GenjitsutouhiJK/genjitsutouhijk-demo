package io.github.genjitsutouhijk.demo.controller;

import com.jayway.jsonpath.JsonPath;
import io.github.genjitsutouhijk.demo.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 受保护接口 + 安全过滤器链的整体验证
 *
 * 这个测试类回答的问题只有一个：**里程碑 ③ 加的那条认证链真的在工作吗？**
 *
 * 验证手段是"同一组请求，带 token 和不带 token 的结果必须不同"。
 * 如果只测"带 token 能成功"，其实什么都没测到 —— 一个完全没配安全的项目也会通过。
 * 真正有价值的是那些**应该被拒绝**的分支。
 *
 * 这里用的是集成测试（@SpringBootTest），因为要验证的正是"过滤器链 + 授权规则 +
 * 异常处理器"这几样一起工作的效果，单测一个类看不出来。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("安全过滤器链与受保护接口")
class SecurityIntegrationTest {

    private static final String ME_URL = "/api/user/me";
    private static final String LOGIN_URL = "/api/auth/login";
    private static final String ADMIN_JSON = "{\"username\":\"admin\",\"password\":\"123456\"}";

    @Autowired
    private MockMvc mockMvc;

    /**
     * 直接注入 JwtService 来"造"token，而不是每次都去走一遍登录接口。
     *
     * 为什么可以这样？因为 JwtService 用的是和运行时**同一个**密钥
     * （都来自 application-test.properties 的 app.jwt.secret）。
     * 这也顺带证明了一件事：token 不是某个接口内部的私货，
     * 而是由整个应用共享的密钥签发的 —— 将来多实例部署时，只要密钥一致就能互相认。
     */
    @Autowired
    private JwtService jwtService;

    // ==================== 应该被拒绝的分支 ====================

    @Test
    @DisplayName("不带任何凭证：401 + code 1005")
    void meWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("请先登录"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("401 的响应体也是统一格式 —— 不是 Spring Security 默认的空 body")
    void unauthorizedResponseUsesUnifiedBody() throws Exception {
        String body = mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // RFC 6750 要求认证失败时带这个头
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andReturn().getResponse().getContentAsString();

        // ★ 这条是本里程碑存在的意义所在。
        //   不写自定义 AuthenticationEntryPoint 的话，这里会是空字符串，
        //   前端 response.json() 直接抛错，只能显示"网络错误"。
        //   现在前端只要照旧判断 json.code 就行，不用为 401 写特殊逻辑。
        assertThat(body).contains("\"code\"").contains("\"message\"");
    }

    @Test
    @DisplayName("带了伪造的 token：401 + code 1007")
    void meWithGarbageTokenIsRejected() throws Exception {
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1007))
                .andExpect(jsonPath("$.message").value("登录凭证无效，请重新登录"));
    }

    @Test
    @DisplayName("带了用别的密钥签的 token：也是 401 + code 1007")
    void meWithForeignTokenIsRejected() throws Exception {
        // 模拟"攻击者自己造了一张票"。格式完全正确、用户名写着 admin，
        // 唯一的区别是签名用的不是我们的密钥 —— 必须被挡下来。
        JwtService attacker = new JwtService(
                new io.github.genjitsutouhijk.demo.config.JwtProperties(
                        "attacker-secret-0123456789-0123456789-xyz", 3600));

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + attacker.issue("admin")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1007));
    }

    @Test
    @DisplayName("token 过期：401 + code 1006（和 1007 区分开，前端才能静默跳登录页）")
    void meWithExpiredTokenIsRejected() throws Exception {
        String expired = jwtService.issue("admin", Duration.ofSeconds(-10));

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006))
                .andExpect(jsonPath("$.message").value("登录已过期，请重新登录"));
    }

    @Test
    @DisplayName("Bearer 前缀写错（只写 token 不写 Bearer）：当作没带凭证，401 + 1005")
    void meWithoutBearerPrefixIsTreatedAsAnonymous() throws Exception {
        // 这是真实会遇到的：前端拼 header 时漏了 "Bearer "。
        // 因为格式不符合约定，过滤器选择"当成没带"，交给入口点回 1005。
        // 关键是**不能**因此抛异常变成 500 —— 那是把用户的手误当成服务器 bug。
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, jwtService.issue("admin")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1005));
    }

    // ==================== 应该通过的分支 ====================

    @Test
    @DisplayName("走完整流程：登录拿 token → 带上去访问 /api/user/me → 拿到自己的资料")
    void fullLoginThenAccessProtectedEndpoint() throws Exception {
        // 1. 真的调一次登录接口，从响应体里把 token 抠出来。
        //    不用 jwtService 造，是为了验证"登录接口发出去的票"确实被安全链认。
        //    这两件事如果用了不同密钥，只有这条测试能发现。
        String loginBody = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADMIN_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(loginBody, "$.data.accessToken");

        // 2. 带上它访问受保护接口。
        String meBody = mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andReturn().getResponse().getContentAsString();

        // 3. id 和 createdAt 都应该有值 —— 说明数据是真的从数据库查出来的，
        //    而不是把 token 里的用户名照抄回去。
        //    用 Number 而不是 Integer 接收：JSON 数字被解析成什么类型由 JsonPath 决定，
        //    写死 Integer 一旦它给了 Long 就会 ClassCastException，属于自找麻烦。
        Number id = JsonPath.read(meBody, "$.data.id");
        assertThat(id).isNotNull();
        assertThat(id.longValue()).isPositive();

        // createdAt 只断言"字段在"，不硬转成 String。
        // LocalDateTime 被序列化成 ISO 字符串还是时间戳，由 Jackson 的配置决定，
        // 写死转型会让测试和"序列化配置"耦合 —— 那种失败没意义，只是噪音。
        assertThat(meBody).contains("createdAt");
    }

    @Test
    @DisplayName("资料里绝对不含密码字段 —— 不能直接把实体返回给前端")
    void profileNeverExposesPasswordHash() throws Exception {
        String body = mockMvc.perform(get(ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue("admin")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 这条是"防回归"的：将来有人图省事把 UserProfile 换成 User 实体，
        // 密码哈希就会被发到浏览器，这条测试会立刻红。
        assertThat(body)
                .doesNotContain("passwordHash")
                .doesNotContain("password_hash")
                .doesNotContain("$2a$");
    }

    // ==================== 公开接口不受影响 ====================

    @Test
    @DisplayName("登录接口保持匿名可访问 —— 否则会陷入『想登录但必须先登录』的死循环")
    void loginStaysPublic() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADMIN_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("注册接口保持匿名可访问")
    void registerStaysPublic() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newcomer\",\"password\":\"newcomer-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("存活检查接口保持匿名可访问")
    void helloStaysPublic() throws Exception {
        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, Spring Boot!"));
    }

    // ==================== 安全链和 404 的先后顺序 ====================

    @Test
    @DisplayName("未登录访问不存在的路径：先被安全链拦成 401，而不是 404")
    void unauthenticatedUnknownPathIsUnauthorizedNotNotFound() throws Exception {
        // 这条测试记录的是**执行顺序**，很值得记住：
        //   过滤器链 → DispatcherServlet → 路由匹配
        // 安全链在路由之前。所以"路径不存在"这件事，在没登录时根本轮不到被发现。
        //
        // 对排查问题的影响：如果你没登录去试一个写错的地址，
        // 看到的是 401 而不是 404，很容易误以为"接口存在但没权限"。
        // 先登录再试，才会看到真正的 404。
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1005));
    }

    @Test
    @DisplayName("已登录访问不存在的路径：这才轮到 GlobalExceptionHandler 报 404")
    void authenticatedUnknownPathStillReturnsNotFound() throws Exception {
        // 和上一条配成一对，正好说明"401 还是 404 取决于你有没有登录"。
        String body = mockMvc.perform(get("/api/does-not-exist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue("admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("/api/does-not-exist");
    }

    // ==================== 默认拒绝 ====================

    @Test
    @DisplayName("任何没在 SecurityConfig 里明确放开的路径，默认都要求登录")
    void unlistedPathsRequireAuthenticationByDefault() throws Exception {
        // anyRequest().authenticated() 的价值在这条测试里体现：
        // 下面这些路径没人专门配过规则，但它们的默认状态是"关着"的。
        // 如果哪天有人把 anyRequest() 改成 permitAll()，这条会立刻红。
        for (String path : new String[]{"/api/user/me", "/api/anything/new", "/api/admin/secret", "/actuator/env"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
}
