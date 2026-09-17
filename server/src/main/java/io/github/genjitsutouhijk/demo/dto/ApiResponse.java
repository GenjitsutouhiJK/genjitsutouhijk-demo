package io.github.genjitsutouhijk.demo.dto;

import io.github.genjitsutouhijk.demo.exception.ErrorCode;

/**
 * 统一响应体
 *
 * 前端约定的规矩：先看 code，等于 0 才是成功，非 0 就把 message 显示给用户。
 *
 * 它是 record，不是普通类：
 *   record 是 Java 16 引入的"数据载体"，编译器会自动生成构造器、getter、
 *   equals/hashCode/toString。字段写在括号里，天生不可变（final），
 *   非常适合"只用来传数据"的 DTO。
 *
 * 泛型 <T> 表示 data 的类型由调用方决定：
 *   ApiResponse<LoginResponse> 的 data 就是 LoginResponse，
 *   ApiResponse<Void> 则没有 data（失败时 data 恒为 null）。
 */
public record ApiResponse<T>(
        int code,
        String message,
        T data
) {
    // 成功：code 固定 0，data 带上
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "成功", data);
    }

    // 成功但想自定义 message
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(0, message, data);
    }

    // 失败：code 由调用方给，data 永远是 null
    public static <T> ApiResponse<T> failure(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    // 失败：直接拿错误码字典里的码和文案。
    // 有这个重载之后，抛异常和处理异常两处都不用再重复写 code 和 message。
    public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
        return failure(errorCode.getCode(), errorCode.getMessage());
    }

    // 失败：用错误码字典里的码，但临时换一句更具体的文案，
    // 例如 ErrorCode.INVALID_PARAMETER + "用户名不能为空"
    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message) {
        return failure(errorCode.getCode(), message);
    }
}