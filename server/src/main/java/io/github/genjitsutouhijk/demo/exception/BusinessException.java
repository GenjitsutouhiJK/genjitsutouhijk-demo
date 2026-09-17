package io.github.genjitsutouhijk.demo.exception;

/**
 * 业务异常
 *
 * 用来表达"业务规则不允许这么做"，而不是"程序写错了"。
 *
 * 举个对比：
 *   - 密码错误   → 业务异常。这是正常会发生的情况，我们要给用户一句友好提示。
 *   - 空指针     → 程序 bug。用户看不懂，也不该看到细节，得记日志给开发看。
 *
 * 为什么继承 RuntimeException 而不是 Exception？
 *   RuntimeException 属于"非受检异常"（unchecked），抛它的时候方法签名不用写 throws，
 *   调用方也不被迫 try-catch。Spring 遇到它会自动回滚事务。
 *   业务失败在我们这里是"常态"，到处都是 try-catch 会把业务代码淹掉，所以选非受检。
 *
 * 为什么要把 ErrorCode 存进来？
 *   抛出点只知道"失败了"，处理点（GlobalExceptionHandler）需要知道"失败成什么样"——
 *   靠这个字段把错误码从里层传出去。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 用枚举自带的默认文案 */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 想临时覆盖文案时用这个，比如"用户名 alice 已被占用" */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
