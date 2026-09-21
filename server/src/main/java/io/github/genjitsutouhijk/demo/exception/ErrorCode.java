package io.github.genjitsutouhijk.demo.exception;

/**
 * 业务错误码字典
 *
 * 为什么要有这个枚举？
 *   改造之前，错误码 1001 是直接写在 AuthController 里的一个"魔法数字"。
 *   一旦错误码变多，就会出现"1001 到底是啥"的困惑，也容易出现两个接口用了同一个码。
 *   把它收进一个枚举之后：
 *     - 每个码只在一处定义，改文案不用满项目搜字符串；
 *     - 想知道系统里一共有哪些错误，打开这个文件就够了；
 *     - 写代码时是 ErrorCode.XXX 而不是 1001，IDE 能自动补全，也不会写错。
 *
 * 约定：
 *   0    表示成功（不在这里定义，成功由 ApiResponse.success() 负责）
 *   1000+ 业务错误，给用户看的提示都写在这里
 *   9999  兜底，表示"没预料到的服务器错误"
 */
public enum ErrorCode {

    /** 登录时用户名或密码对不上 */
    LOGIN_FAILED(1001, "用户名或密码错误"),

    /** @Valid 校验没通过，比如用户名传了空字符串 */
    INVALID_PARAMETER(1002, "请求参数不合法"),

    /** 请求体缺失、或者不是合法 JSON，Spring 根本没能把请求解析成对象 */
    MALFORMED_BODY(1003, "请求体格式错误"),

    /** 注册时想用的用户名已经被别人占了 */
    USERNAME_TAKEN(1004, "用户名已被占用"),

    /** 兜底：代码里没预料到的异常，统一告诉前端"服务端开小差了" */
    INTERNAL_ERROR(9999, "服务器开小差了，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
