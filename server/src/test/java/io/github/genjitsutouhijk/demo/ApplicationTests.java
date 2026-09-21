package io.github.genjitsutouhijk.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 冒烟测试：Spring 容器能不能正常启动
 *
 * 别看这个测试空的，它其实在替你检查一大堆东西：
 *   数据源能不能连上、Flyway 的迁移脚本能不能跑通、
 *   JPA 实体和真实表结构对不对得上（ddl-auto=validate）、
 *   每个 @Service / @Repository / @Bean 之间的依赖能不能装配起来……
 *   这些任何一项出问题，这个方法都会失败。
 *
 * 它是"最便宜的一道防线"：改完配置先跑它，
 * 就不用在启动整个应用时对着一长串堆栈找问题了。
 *
 * @ActiveProfiles("test") 必须加 —— 不加就会去连 application.properties 里的
 * **文件**数据库（server/data/ 下的那个），测试跑几遍就在你的开发数据里留下垃圾。
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationTests {

	@Test
	@DisplayName("上下文能正常启动（数据源 + Flyway + JPA + 依赖装配）")
	void contextLoads() {
	}

}
