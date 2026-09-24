package io.github.genjitsutouhijk.demo.security;

import io.github.genjitsutouhijk.demo.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT（JSON Web Token）的签发与解析
 *
 * ============ 先搞清楚它解决了什么问题 ============
 *
 * 里程碑 ② 的登录接口返回的是 "fake-token-for-admin" 这种拼出来的字符串。
 * 那个东西有两个致命问题：
 *   1. 后端**没有记录它**，也没有任何地方会去校验它 ——
 *      前端拿着它回来，后端不知道你是谁，等于一张没人验的废纸；
 *   2. 任何人都能自己拼一个 "fake-token-for-admin"，冒充管理员。
 *
 * 那"让后端记住每个 token"行不行？行，但那就得在服务端存一张表 ——
 * 多台服务器时还得共享这张表（或都去问 Redis），部署和扩容都变麻烦。
 *
 * JWT 的思路是**把状态签在 token 自己身上**：
 *       ┌─────────┬───────────────────┬──────────┐
 *       │ header  │     payload       │signature │
 *       │  算法   │  用户名 / 过期时间  │  签名    │
 *       └─────────┴───────────────────┴──────────┘
 *            三部分各自 Base64 后，用 "." 连起来 —— 所以 JWT 长这样：xxx.yyy.zzz
 *
 * 关键点（也是新手最容易误解的地方）：
 *   ★ header 和 payload 只是 Base64 编码，**不是加密**。
 *     随便找个网站把中间那段贴进去就能看到明文内容。
 *     所以 JWT 里**绝对不能放密码、手机号这类敏感信息**。
 *   ★ 真正的安全保证来自第三段"签名"：它是用**只有服务器知道的密钥**，
 *     对前两段算出来的。别人改一个字，签名就对不上，服务器一验就知道被篡改了。
 *     所以 JWT 保证的是"没被篡改"，不是"看不见内容"。
 *
 * 于是服务端不需要存任何东西：收到 token → 用密钥验签 → 取出用户名。
 * 这叫"无状态"（stateless）。代价是**签发出去的 token 没法单独作废**，
 * 只能等它过期 —— 这就是为什么有效期不能设得太长（本项目默认 1 小时）。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expiresInSeconds;

    /**
     * 构造器里就把密钥转换好，好处是**配置写错在启动时就会炸**，而不是等到第一次登录。
     * 这种"早失败"比"线上跑一半才发现"要好得多。
     */
    public JwtService(JwtProperties properties) {
        String secret = properties.secret();

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "缺少配置 app.jwt.secret，无法签发 JWT。请检查 application.properties。");
        }

        // Keys.hmacShaKeyFor(byte[]) 会把字符串当成密钥字节，并按长度决定算法：
        //   32 字节 → HmacSHA256，48 → HmacSHA384，64 → HmacSHA512。
        // 长度不够 32 字节时它会抛 WeakKeyException，直接让应用起不来 —— 这正是我们要的：
        // 密钥太短意味着别人可以暴力猜出来，宁可启动失败也不能悄悄用弱密钥上线。
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiresInSeconds = properties.expiresInSeconds();
    }

    /** 用配置里的有效期签发一个 token（登录、注册成功时用这个）。 */
    public String issue(String username) {
        return issue(username, Duration.ofSeconds(expiresInSeconds));
    }

    /**
     * 指定有效期签发一个 token。
     *
     * 为什么要有这个重载？—— 主要是给测试用的：
     * 想验证"过期 token 会被拒绝"这个分支，就得造一个**出生即过期**的 token。
     * 传一个负数的 Duration 进去就能做到，比去 mock 系统时钟简单得多，也更好懂。
     *
     * ⚠️ 一个值得知道的事实：JWT 规范里 iat / exp 用的是**秒**级精度的 NumericDate，
     *    而且这张 token 里没有随机数（没有 jti 之类的字段）。
     *    所以**同一秒内、为同一个用户、用同样有效期签发两次，得到的字符串是完全一样的**。
     *    这不是 bug，是设计使然；但如果哪天你需要"每张票都能唯一标识"
     *    （比如做"登出后把这张票拉黑"），就必须自己加一个随机 id 进去：
     *        .id(UUID.randomUUID().toString())
     *    现在没有这个需求，所以不加 —— 少一个字段就少一份"要不要存它"的纠结。
     *
     * @param username 写进 payload 的 subject 声明，也就是"这张票是谁的"
     * @param ttl      有效期，从此刻算起
     */
    public String issue(String username, Duration ttl) {
        Instant now = Instant.now();

        return Jwts.builder()
                // subject 是 JWT 规范里预定义的标准字段，语义就是"这个 token 属于谁"。
                .subject(username)
                // iat（issued at）：签发时间。将来排查"这票是什么时候发的"要看它。
                .issuedAt(Date.from(now))
                // exp（expiration）：过期时间。JJWT 在解析时会**自动**校验它，
                // 过期了会抛 ExpiredJwtException —— 不用我们自己写 if。
                .expiration(Date.from(now.plus(ttl)))
                // ★ 用密钥签名。这一步是整段代码里唯一提供安全性的地方。
                //   显式指定 HS256（而不是让它按密钥长度自动选），是为了让算法固定下来：
                //   万一有人把密钥换成 64 字节，token 悄悄变成 HS512，
                //   将来排查问题时会很困惑。固定住算法，行为就永远可预期。
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析 token，取出用户名
     *
     * ★ 这个方法**不抛自己的异常**，而是原样把 JJWT 的异常抛出去，让调用方决定怎么处理。
     *   可能的异常（都是运行时异常，方法签名不用写 throws）：
     *     ExpiredJwtException      —— 过期了。这是正常现象（用户放太久没操作），
     *                                 应该提示"请重新登录"，而不是"出错了"。
     *     SignatureException       —— 签名对不上，说明 token 被改过，或者根本不是我们发的。
     *     MalformedJwtException    —— 格式都不对，比如前端手抖传了个 "abc"。
     *     UnsupportedJwtException  —— 算法不支持。
     *     IllegalArgumentException—— token 是 null 或空串。
     *   后四种业务上都是同一件事："这票是假的"，所以调用方那边会合并处理。
     *
     * 注意：这里**只做密码学层面的校验**（签名 + 过期）。
     * "这个用户是否还存在"要查库，那是 Service 层的活，不属于 JWT 的职责。
     *
     * @param token 前端通过 Authorization 头带回来的那段字符串
     * @return token 里记录的登录用户名
     */
    public String parseUsername(String token) {
        // verifyWith(key)：声明"只接受用这把密钥签出来的 token"。
        // 少了它，JJWT 会抛异常拒绝工作 —— 库的设计者强制你必须显式指定密钥，
        // 免得有人糊里糊涂做出一个"谁签的都收"的漏洞。
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    /** 对外暴露有效期，登录响应里的 expiresIn 用它，保证"告诉前端的"和"真的签的"是同一个数。 */
    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }
}
