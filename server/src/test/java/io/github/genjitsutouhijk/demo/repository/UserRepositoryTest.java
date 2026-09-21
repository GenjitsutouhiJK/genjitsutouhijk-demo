package io.github.genjitsutouhijk.demo.repository;

import io.github.genjitsutouhijk.demo.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据访问层验证：直接对着真实数据库问问题
 *
 * 和 AuthControllerTest 的分工：
 *   AuthControllerTest 走 HTTP，验证"从接口进来到 JSON 出去"这条链路对不对；
 *   这里不经过 HTTP，直接调 Repository，验证"和数据库的约定"对不对 ——
 *   比如字段到底存成了什么样、方法名拼出来的 SQL 是不是我们想的那句。
 *
 * 为什么要专门测"密码存成了哈希"这件事？
 *   因为这是整个持久层里**唯一一处错了会出大事**的地方：
 *   功能上它会照常工作（注册能成功、登录可能也能成功，如果你两边都存明文的话），
 *   但数据一旦泄漏就是灾难。这种"功能正常但有安全风险"的问题，
 *   只有靠一条专门的断言才能钉住，靠点页面是点不出来的。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("UserRepository：真实数据库上的行为")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("findByUsername 能查到 Flyway 插入的种子用户 admin")
    void findsSeededUser() {
        Optional<User> found = userRepository.findByUsername("admin");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("admin");
        // id 应该是数据库生成后回填的，不是 null
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("库里存的密码是 BCrypt 哈希，不是明文「123456」")
    void passwordIsStoredAsHashNotPlainText() {
        String stored = userRepository.findByUsername("admin")
                .orElseThrow()
                .getPasswordHash();

        // 断言一：明文绝不能出现在库里
        assertThat(stored).isNotEqualTo("123456");
        // 断言二：样子得是 BCrypt 的格式。
        //   $2a$10$ 分别表示"BCrypt 版本"和"计算强度 2^10 轮"，开头这两个标记不对就说明换了算法或配置。
        assertThat(stored).startsWith("$2a$10$");
        // BCrypt 的结果固定 60 个字符
        assertThat(stored).hasSize(60);
        // 断言三：用官方工具比一下，确认这个哈希真的对应「123456」
        // （光看格式对是不够的 —— 哈希算错、盐用错，格式一样是对的）
        assertThat(passwordEncoder.matches("123456", stored)).isTrue();
        // 断言四：错误密码当然要比不上
        assertThat(passwordEncoder.matches("654321", stored)).isFalse();
    }

    @Test
    @DisplayName("existsByUsername：存在的返回 true，不存在的返回 false")
    void existsByUsernameWorksBothWays() {
        assertThat(userRepository.existsByUsername("admin")).isTrue();
        assertThat(userRepository.existsByUsername("definitely-not-here")).isFalse();
    }

    @Test
    @DisplayName("save 新用户后，id 会被数据库回填，并且能再查出来")
    void saveAssignsIdAndPersists() {
        User saved = userRepository.save(new User(
                "repo-test-user",
                passwordEncoder.encode("some-password"),
                java.time.LocalDateTime.now()
        ));

        assertThat(saved.getId()).isNotNull();

        // 关键点：这里重新查一次，而不是相信 saved 这个内存里的对象。
        // 因为 saved 身上的 id 也可能是 Hibernate 自己编的 ——
        // 只有"从数据库重新读一遍还能读到"才证明真的落库了。
        assertThat(userRepository.findByUsername("repo-test-user")).isPresent();
    }

    @Test
    @DisplayName("同一个密码加密两次，结果不一样（因为 BCrypt 每次都用随机盐）")
    void bcryptUsesRandomSalt() {
        String first = passwordEncoder.encode("same-password");
        String second = passwordEncoder.encode("same-password");

        // 两个哈希不相等，但都能比对成功 —— 这正是随机盐的效果：
        // 攻击者没法拿一张预计算好的"密码→哈希"大表来批量还原。
        assertThat(first).isNotEqualTo(second);
        assertThat(passwordEncoder.matches("same-password", first)).isTrue();
        assertThat(passwordEncoder.matches("same-password", second)).isTrue();
    }
}
