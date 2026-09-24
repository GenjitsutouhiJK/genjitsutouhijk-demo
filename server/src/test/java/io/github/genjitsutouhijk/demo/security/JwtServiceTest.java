package io.github.genjitsutouhijk.demo.security;

import io.github.genjitsutouhijk.demo.config.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtService 的单元测试
 *
 * ★ 注意这个测试类的"形状"和 AuthControllerTest 完全不同 —— 它没有任何 Spring 注解。
 *
 * 为什么不 @SpringBootTest？
 *   因为 JwtService 只依赖一个 JwtProperties（一个普通 record），
 *   直接 new 出来就行。这是**单元测试**：只测这一个类的逻辑，
 *   不启动 Spring、不连数据库、不碰 HTTP，所以几百毫秒就跑完了。
 *
 *   对比一下：
 *     JwtServiceTest     —— 单元测试。快、定位准：红了就一定是 JwtService 的错。
 *     AuthControllerTest —— 集成测试。慢、覆盖广：验证"从 HTTP 进来到 SQL 出去"整条链路，
 *                           但一旦红了，可能是 Controller、Service、SQL、配置任何一环的问题。
 *
 *   两种都要有：出问题时先用快的缩小范围，再用慢的确认整体没坏。
 *   这也是为什么"能不能不启动 Spring"本身就是一个设计信号 ——
 *   一个类越是能脱离框架独立构造，就越容易测，也说明它的依赖越干净。
 */
@DisplayName("JWT 签发与解析")
class JwtServiceTest {

    /** 测试用的固定密钥。必须 ≥32 字符，否则 JwtService 会拒绝构造。 */
    private static final String SECRET = "unit-test-secret-0123456789-0123456789";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 3600));

    // ==================== 正常路径 ====================

    @Test
    @DisplayName("签发再解析，能拿回原来的用户名")
    void issueThenParseReturnsSameUsername() {
        String token = jwtService.issue("admin");

        assertThat(jwtService.parseUsername(token)).isEqualTo("admin");
    }

    @Test
    @DisplayName("token 是三段式结构（header.payload.signature）")
    void tokenHasThreeSegments() {
        // 这条断言看着很浅，其实很有用：
        // 它能一眼把"真的 JWT"和里程碑 ② 那个 "fake-token-for-admin" 区分开。
        // 以后谁要是把签发逻辑改回拼字符串，这条会立刻红。
        String token = jwtService.issue("admin");

        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("payload 只是 Base64 编码，不是加密 —— 所以里面绝不能放敏感信息")
    void payloadIsReadableNotEncrypted() {
        String token = jwtService.issue("admin");

        // 把中间那段解出来，应该能直接看到用户名。
        // 这条测试不是在验证"功能"，而是在**把这个认知钉下来**：
        // JWT 保证的是"没被篡改"，不是"看不见内容"。
        // 所以密码、手机号、身份证号这类东西一律不能往 claims 里塞。
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(payload).contains("admin");
    }

    @Test
    @DisplayName("对外暴露的有效期和配置一致（响应里的 expiresIn 靠它，不能各写各的）")
    void expiresInIsExposedForTheLoginResponse() {
        // 这条锁住一个容易出错的约定：登录响应里告诉前端的 expiresIn，
        // 必须和 token 里 exp 声明用的那个值来自**同一个配置项**。
        // 如果哪天有人在 AuthService 里又硬编码一个 3600，
        // token 实际 10 分钟过期、前端却以为有 1 小时 —— 这条测试就是用来挡这种事的。
        assertThat(jwtService.getExpiresInSeconds()).isEqualTo(3600);
    }

    // ==================== 过期 ====================

    @Test
    @DisplayName("过期的 token 会被拒绝：抛 ExpiredJwtException")
    void expiredTokenIsRejected() {
        // 传入负的 ttl，造一个"出生即过期"的 token。
        // 比去 mock 系统时钟简单得多，意图也更直白：这张票已经过期了。
        String expired = jwtService.issue("admin", Duration.ofSeconds(-10));

        assertThatThrownBy(() -> jwtService.parseUsername(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("ExpiredJwtException 是 JwtException 的子类 —— 这是过滤器里能合并 catch 的依据")
    void expiredExceptionIsSubtypeOfJwtException() {
        // 这条断言解释了 JwtAuthenticationFilter 里那个 catch 顺序：
        // 必须先 catch ExpiredJwtException（更具体），
        // 再 catch JwtException（更宽泛），否则过期会被笼统地当成"凭证无效"，
        // 前端就没法区分"该静默跳登录页"和"该提示异常"了。
        assertThat(ExpiredJwtException.class).isAssignableTo(JwtException.class);
    }

    // ==================== 伪造与篡改 ====================

    @Test
    @DisplayName("用别的密钥签出来的 token 验不过 —— 这是整个方案的安全底线")
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService attacker = new JwtService(
                new JwtProperties("attacker-knows-nothing-0123456789-abcdef", 3600));

        // 攻击者可以造出格式完全正确的 token，用户名也写 "admin"，
        // 但签名用的是他自己的密钥，服务端一验就露馅。
        // ★ 这一条一旦失效，等于任何人都能冒充任何人，所以它是最该被测住的。
        String forged = attacker.issue("admin");

        assertThatThrownBy(() -> jwtService.parseUsername(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("payload 被改过一个字符也会被拒绝（签名对不上）")
    void modifiedPayloadIsRejected() {
        String token = jwtService.issue("admin");
        String[] parts = token.split("\\.");

        // 动 payload 的第 6 个字符。无论结果是被判"格式坏了"还是"签名对不上"，
        // 都属于 JwtException，都应该被拒绝 —— 断言到这一层就够了，
        // 断言具体子类型反而会让测试变得脆（JJWT 换个版本就可能改抛哪个）。
        char original = parts[1].charAt(5);
        char replaced = (original == 'A') ? 'B' : 'A';
        String tamperedPayload = parts[1].substring(0, 5) + replaced + parts[1].substring(6);
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtService.parseUsername(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("随便传个字符串：被拒绝，而且不会抛出难懂的低层异常")
    void garbageTokenIsRejected() {
        // 对应真实场景：前端手抖传了 "Bearer abc"，或者 header 里塞了别的东西。
        assertThatThrownBy(() -> jwtService.parseUsername("not-a-jwt-at-all"))
                .isInstanceOf(JwtException.class);
    }

    // ==================== 配置错误要早失败 ====================

    @Test
    @DisplayName("密钥太短：构造时就抛 WeakKeyException，应用根本起不来")
    void shortSecretFailsFast() {
        // "早失败"是刻意设计的：弱密钥能在几小时内被暴力破解，
        // 与其让它悄悄上线，不如启动就崩，强迫配置的人去改。
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", 3600)))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    @DisplayName("密钥没配：抛 IllegalStateException，且提示能直接指向配置文件")
    void missingSecretFailsWithReadableMessage() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("  ", 3600)))
                .isInstanceOf(IllegalStateException.class)
                // 断言提示里包含配置项名字 —— 让报错信息本身就能指导修复，
                // 而不是让人对着 "NullPointerException" 发呆。
                .hasMessageContaining("app.jwt.secret");
    }
}
