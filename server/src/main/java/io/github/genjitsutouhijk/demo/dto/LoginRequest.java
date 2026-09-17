package io.github.genjitsutouhijk.demo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求体
 *
 * 字段名必须和前端发来的 JSON 的 key 一模一样，Spring 靠名字对应：
 *   { "username": "admin", "password": "123456" }
 *
 * 关于 @NotBlank：
 *   它是 Jakarta Bean Validation 提供的校验注解，会拒绝三种情况 ——
 *     null         完全不传这个字段
 *     ""           传了空字符串
 *     "   "        只传空格
 *   它和另外两个容易混的注解的区别，别选错：
 *     @NotNull  → 只要不是 null 就行，空字符串能通过
 *     @NotEmpty → 不能是 null，长度也不能为 0，但"只含空格"能通过
 *     @NotBlank → 最严格，去空格后必须有内容（只对字符串有效）
 *   对用户名密码这类文本，"看起来有内容"才是我们要的，所以选 @NotBlank。
 *
 * message 是校验失败时给用户看的文案。
 * 它不会在这里被打印到任何地方 —— GlobalExceptionHandler 会把它们收集起来，
 * 拼进 ApiResponse 的 message 字段，最后显示在登录页那行红字上。
 *
 * 校验什么时候执行？
 *   在 Controller 方法被调用**之前**。Spring 看到参数上的 @Valid，
 *   就先跑一遍校验；不通过就抛 MethodArgumentNotValidException，
 *   方法体一行都不会执行。所以 Service 里可以放心地认为"username 一定非空"。
 */
public record LoginRequest(

        @NotBlank(message = "用户名不能为空")
        String username,

        @NotBlank(message = "密码不能为空")
        String password
) {}
