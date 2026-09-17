package io.github.genjitsutouhijk.demo.exception;

import io.github.genjitsutouhijk.demo.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * 为什么要有它？
 *   改造之前，Controller 里到处是 `return ApiResponse.failure(1001, "...")`。
 *   一旦某个地方忘了 catch，Spring 就会吐出一份自己的错误 JSON，
 *   前端拿到的 `code` 是 undefined，只能显示"未知错误"。
 *
 *   @RestControllerAdvice 的作用是：把整个项目里"没人接住的异常"统一收到这里，
 *   由这一处决定返回给前端长什么样。业务代码里就不用再写 try-catch 了。
 *
 * 工作原理（记住这条链路就够了）：
 *   Controller / Service 抛出异常
 *        ↓
 *   Spring 发现有个 @RestControllerAdvice 声明了能处理这个异常类型的方法
 *        ↓
 *   调用那个方法，把它的返回值当作 HTTP 响应体写回去
 *
 * 匹配规则：优先找"最具体"的处理方法。
 *   抛 BusinessException 时，下面 5 个方法里只有 handleBusinessException 能接住；
 *   抛一个没人认识的异常时，才会落到最后的 handleUnexpectedException。
 *
 * 关于 HTTP 状态码的约定（本项目）：
 *   只要请求被我们正常处理、并给出了业务结论，就返回 200，结论写在 body 的 code 里；
 *   只有"框架层面就没走通"才用非 200 —— 也就是下面的 404 和 500。
 *   这样前端只需要判断 json.code 就能知道成败，与改造前的约定保持一致。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 1. 业务异常：我们自己主动抛的，属于"可预期的失败"
     *
     * 这是最常见的一类。密码错了、余额不够了、用户名被占了……都不是 bug，
     * 而是正常的业务分支，所以日志用 warn（不是 error），也不需要打堆栈。
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException ex) {
        log.warn("业务异常：code={}, message={}", ex.getErrorCode().getCode(), ex.getMessage());
        return ApiResponse.failure(ex.getErrorCode(), ex.getMessage());
    }

    /**
     * 2. 参数校验不通过：Controller 参数上的 @Valid 发现字段不合法
     *
     * 一个请求可能有多个字段同时不合法，这里把它们的中文提示用"；"拼成一句话，
     * 前端直接显示就行。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));

        // 理论上不会为空，但万一没有字段级错误，也得给一句兜底文案
        if (detail.isBlank()) {
            detail = ErrorCode.INVALID_PARAMETER.getMessage();
        }

        log.warn("参数校验失败：{}", detail);
        return ApiResponse.failure(ErrorCode.INVALID_PARAMETER.getCode(), detail);
    }

    /**
     * 3. 请求体根本读不出来：没传 body，或者传的不是合法 JSON
     *
     * 这类错误发生在"对象还没造出来"的时候，比字段校验更早一步，所以是单独的异常类型。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("请求体无法解析：{}", ex.getMessage());
        return ApiResponse.failure(ErrorCode.MALFORMED_BODY);
    }

    /**
     * 4. 访问了不存在的路径
     *
     * 这一条看起来多余，其实是必需的 —— 下面的兜底处理器会把所有异常一网打尽，
     * 包括 Spring 自己抛的 404。如果不在这里单独接住并声明 @ResponseStatus(404)，
     * 那么访问一个错地址会得到 "HTTP 200 + code 9999"，让人以为接口是通的。
     *
     * 同理，将来如果需要 405（方法不支持）、415（Content-Type 不支持）保持原生状态码，
     * 照着这个方法再加一个即可。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(NoResourceFoundException ex) {
        // getResourcePath() 返回的是不带开头斜杠的路径（例如 "api/nope"），
        // 直接拼进提示里会变成"接口不存在：api/nope"，看着别扭，这里补上。
        // 先判断一下，免得将来 Spring 改了行为变成 "//api/nope"。
        String path = ex.getResourcePath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        log.warn("路径不存在：{}", path);
        return ApiResponse.failure(404, "接口不存在：" + path);
    }

    /**
     * 5. 兜底：所有没被上面接住的异常
     *
     * 走到这里说明是代码 bug（空指针、数组越界……）或者依赖挂了（数据库连不上）。
     * 用户看不懂也没必要看细节，所以对外只给一句通用文案；
     * 真正的原因通过 log.error 带着完整堆栈写进日志，留给开发排查。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnexpectedException(Exception ex) {
        log.error("未预期的异常", ex);
        return ApiResponse.failure(ErrorCode.INTERNAL_ERROR);
    }
}
