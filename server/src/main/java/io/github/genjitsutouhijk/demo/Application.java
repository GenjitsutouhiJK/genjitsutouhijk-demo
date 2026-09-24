package io.github.genjitsutouhijk.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * 启动类
 *
 * ============ 关于下面那个 exclude ============
 *
 * 引入 spring-boot-starter-security 之后，Spring Boot 发现"没有配置任何用户，
 * 但有人在用 Spring Security"，就会**善解人意地**替你造一个默认用户：
 *
 *      Using generated security password: 4f2a1c8e-...（一串随机 UUID）
 *
 * 然后每次启动控制台都会多这么一行，让人困惑："这密码是干嘛的？我什么时候配过用户？"
 * 更糟的是它确实生效 —— 一个名叫 user、密码为这串 UUID 的账号真的能登录，
 * 在真实项目里这就是个后门。
 *
 * 我们确实不需要它：本项目的用户存在数据库里，认证靠 JWT，
 * 全程没有用到 Spring Security 那套 UserDetailsService / AuthenticationManager 机制。
 *
 * 所以这里把这个自动配置**显式排除**掉。exclude 的另一个作用是可读性 ——
 * 打开启动类就知道"我们有一个默认行为被主动关掉了，原因是……"，
 * 比让读者自己去脑补为什么要跳过那行日志要好。
 *
 * ⚠️ 注意包名 org.springframework.boot.security.autoconfigure：
 *    Spring Boot 4 把自动配置类从 spring-boot-autoconfigure 这个大杂烩里拆了出来，
 *    按模块放到独立 jar 里（安全相关在这里，web 相关在 spring-boot-webmvc 等）。
 *    所以 Boot 3 时代的 org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
 *    在 Boot 4 里已经不存在了，照抄会编译不过。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
