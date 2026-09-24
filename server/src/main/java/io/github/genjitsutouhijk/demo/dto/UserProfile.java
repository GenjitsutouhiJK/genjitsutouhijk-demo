package io.github.genjitsutouhijk.demo.dto;

import java.time.LocalDateTime;

/**
 * 当前登录用户的资料
 *
 * ⚠️ 为什么不让接口直接返回 User 实体，而要再包一层？
 *
 *   User 里有个字段叫 passwordHash。如果直接把实体返回给前端，
 *   Jackson 会把它一起序列化成 JSON —— 也就是说**密码的哈希值会被发到浏览器**。
 *
 *   哈希本身不能直接当密码用，但它依然是个敏感数据：
 *   攻击者拿到之后可以离线暴力破解，一旦你用的是弱密码就直接失守了。
 *   这是新手项目最容易犯的安全错误之一。
 *
 *   所以正确做法是：**数据库实体和"给前端看的数据"用两个不同的类**。
 *   这个 record 只挑该给前端看的字段，从源头上就没有泄露的可能 ——
 *   比"记得在字段上加 @JsonIgnore"要靠得住，因为后者是"需要记得做某事"，
 *   而前者是"默认就不包含"。
 *
 * 这也是为什么它是 dto（数据传输对象）而不是 entity 的原因。
 */
public record UserProfile(
        Long id,
        String username,
        LocalDateTime createdAt
) {
}
