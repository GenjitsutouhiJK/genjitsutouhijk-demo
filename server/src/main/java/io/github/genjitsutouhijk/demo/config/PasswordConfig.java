package io.github.genjitsutouhijk.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密器的装配
 *
 * 为什么需要加密？—— 因为密码**永远不能明文存数据库**。
 *   一是哪天数据库被拖走了，攻击者拿到的不是一堆能直接登录的账号；
 *   二是很多人多个网站用同一个密码，泄露会波及用户在别处的账号。
 *
 * 为什么用 BCrypt 而不是 MD5 / SHA-256？
 *   后面两个是"快"的哈希算法，快正好是缺点：攻击者每秒能试几十亿个密码组合。
 *   BCrypt 是**故意设计得慢**的（默认约 100 毫秒算一次），
 *   攻击者想穷举就得付出几千倍的代价，而对正常登录只慢 0.1 秒，无感。
 *   另外 BCrypt 会给每个密码加一段随机"盐"，
 *   所以同样的密码 "123456" 存进数据库，两个人算出来的哈希值是不一样的，
 *   攻击者没法用一张预计算好的"密码→哈希"大表（彩虹表）批量还原。
 *
 * 为什么把算法返回类型写成接口 PasswordEncoder，而不是 BCryptPasswordEncoder？
 *   加密算法的选择应该只在这一个文件里出现。
 *   将来想换成 Argon2，只改这一行的 new，AuthService 一个字都不用动 ——
 *   它只知道"有个能加密、能比对的工具"，不关心具体是哪种算法。
 *   这跟"Service 不做接口+Impl 拆分"不矛盾：那里只有一种实现所以不抽接口，
 *   这里抽出来的接口是 Spring Security 提供的、用来隔离算法选择的。
 *
 * 为什么这个类单独放，不塞进启动类或 AuthService 里？
 *   做成 @Bean 之后，Spring 容器里只有**一个** PasswordEncoder 实例，
 *   谁需要谁注入，不会出现"注册和登录用了两个不同配置的加密器"这种要命的 bug。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
