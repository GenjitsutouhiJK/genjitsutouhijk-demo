package io.github.genjitsutouhijk.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求体
 *
 * 和 LoginRequest 完全是两回事，所以单独一个类，而不是给 LoginRequest 加字段。
 * 分离的好处是：登录只需要用户名密码，注册多了"长度限制"这类新规则，
 * 两边互不干扰。这就是 DTO 分层的意义 —— 一个场景一个类。
 *
 * 关于两个注解一起用：
 *   @NotBlank 管"有没有内容"（null、空串、纯空格都不行）
 *   @Size     管"内容多长"（只管长度，null 在 @Size 眼里是合法的）
 * 两者职责不同，所以最常见的组合就是它们俩一起上。
 *
 * 为什么密码也要限长度？
 *   - 下限 6：太短的密码太容易被猜出来。
 *   - 上限 32：不是安全考虑，而是防滥用 —— 有人提交一个 10MB 的字符串当密码，
 *     服务器光算哈希就要卡半天。所有对外接口都应该有输入长度上限。
 *
 * ⚠️ 注意这里的长度限制比 BCrypt 弱得多：BCrypt 只取密码的前 72 个字节参与运算。
 *    所以真正的生产系统上限一般就定在 72 以内，这里定 32 已经更严了。
 */
public record RegisterRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 20, message = "用户名长度需要在 3 到 20 个字符之间")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度需要在 6 到 32 个字符之间")
        String password
) {}
