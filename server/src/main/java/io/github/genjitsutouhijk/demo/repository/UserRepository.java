package io.github.genjitsutouhijk.demo.repository;

import io.github.genjitsutouhijk.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户表的"取数接口"
 *
 * ┌─ 这个接口最反直觉的地方：没有实现类 ────────────────────────────────┐
 * │ 你找不到 UserRepositoryImpl，也没人会去写。                          │
 * │ 启动时 Spring Data 会**动态生成**一个实现类，规则是两句话：          │
 * │     ① 接口继承谁  → 就自动获得谁的全部现成方法；                     │
 * │     ② 方法叫什么名字 → 就拼出对应的 SQL。                            │
 * └────────────────────────────────────────────────────────────────────┘
 *
 * ① 继承 JpaRepository<User, Long> 白拿到的常用方法（不用写一行代码）：
 *      save(user)            新增或更新（id 为空就是插入）
 *      findById(id)          按主键查
 *      findAll()             查全部
 *      count()               总数
 *      deleteById(id)        按主键删
 *      existsById(id)        主键是否存在
 *   尖括号里：User 是操作哪个实体，Long 是它的主键类型。
 *
 * ② 名字拼 SQL —— 这是 Spring Data 最"魔法"也最实用的功能：
 *      findByUsername       →   WHERE username = ?
 *      findByAgeGreaterThan →   WHERE age > ?
 *      findByUsernameAndAge →   WHERE username = ? AND age = ?
 *   规则就是：findBy 后面跟字段名，多个条件用 And / Or 连接，
 *   驼峰会被翻译成下划线（username 原样，passwordHash → password_hash）。
 *   方法名写错（比如写成 findByUsrename）不会报错，而是在启动时报
 *   "No property 'usrename' found" —— 所以拼写要靠 IDE 的补全。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 按用户名查用户 → SELECT * FROM users WHERE username = ?
     *
     * 为什么返回 Optional<User> 而不是直接返回 User？
     *   因为"查不到"是完全正常的（有人用不存在的账号登录）。
     *   返回 Optional 等于在类型上就写明了"这里可能什么都没有"，
     *   强迫调用方去处理"查不到"这种情况：
     *       User u = repo.findByUsername("x");   // ❌ 你得自己判断 u 是不是 null，忘了就空指针
     *       Optional<User> u = repo.findByUsername("x");  // ✅ 编译器盯着你处理
     *   对比一下你自己写 JdbcClient 的写法，就能看出这一层帮了什么：
     *       // 手写：SELECT username, password_hash, created_at FROM users WHERE username = ?
     *       //       然后还要自己写 RowMapper 一行行塞进 User 对象
     */
    Optional<User> findByUsername(String username);

    /**
     * 判断用户名是否已被占用 → SELECT count(*) > 0 FROM users WHERE username = ?
     *
     * 为什么不用 findByUsername(...).isPresent() 判断？
     *   结果一样，但 exists 只让数据库回答"有还是没有"这一个问题，
     *   不用把整行数据（含密码哈希）读出来再丢掉。
     *   existsBy 的命名规则和 findBy 一样：existsBy + 字段名。
     */
    boolean existsByUsername(String username);
}
