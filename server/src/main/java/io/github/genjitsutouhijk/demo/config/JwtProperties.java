package io.github.genjitsutouhijk.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 的配置项
 *
 * 它把 application.properties 里这两行"接"成一个 Java 对象：
 *   app.jwt.secret=...
 *   app.jwt.expires-in-seconds=3600
 *
 * 命名是怎么对上的？
 *   prefix = "app.jwt"  → 匹配 app.jwt.xxx
 *   属性名 expires-in-seconds（中划线）→ 字段 expiresInSeconds（驼峰）
 *   这叫"宽松绑定"（relaxed binding）。中划线、下划线、全小写都能绑上，
 *   所以配置文件里写 expires-in-seconds 或 expiresInSeconds 效果一样。
 *   约定：配置文件用中划线（Spring 官方风格），Java 字段用驼峰。
 *
 * 为什么用 record？
 *   配置是"启动时读一次、之后只读"的东西，天然适合不可变对象 ——
 *   正好是 record 的强项，省掉一堆 getter 和 setter。
 *
 * 为什么单独一个文件，不写 @Value("${app.jwt.secret}") 直接塞进 JwtService？
 *   两个原因：
 *     1. 密钥和有效期是"同一件事的两个参数"，放在一起才不会以后一个改了一个忘了；
 *     2. @ConfigurationProperties 支持在 IDE 里点进配置文件跳转、也能做启动时的格式校验，
 *        而散落的 @Value 字符串一旦写错，要到运行时才发现。
 *
 * ⚠️ 它不会自己变成 Bean，必须在某个 @Configuration 上用 @EnableConfigurationProperties 注册。
 *    本项目在 SecurityConfig 上注册（见那个类的注释）。
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        /** 签名密钥。HS256 要求至少 32 个字节，短了 JJWT 会直接拒绝启动。 */
        String secret,
        /** 签发出去的 token 有效期，单位秒。 */
        long expiresInSeconds
) {
}
