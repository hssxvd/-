package com.project.groupbuy.common.exception;

import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器（约束文档 9）
 * - 业务异常 → 返回业务错误码
 * - 参数校验异常 → 返回校验失败信息
 * - 权限异常 → 返回 1003
 * - 系统异常 → 只返回 traceId，堆栈不泄露（约束文档 9）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<?> handleBusinessException(BusinessException e) {
        log.warn("[业务异常] code={}, msg={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage()).traceId(MDC.get("traceId"));
    }

    /**
     * 参数校验异常（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<?> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数校验] {}", msg);
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg).traceId(MDC.get("traceId"));
    }

    /**
     * 参数校验异常（表单绑定）
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<?> handleBindException(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[参数绑定] {}", msg);
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg).traceId(MDC.get("traceId"));
    }

    /**
     * 非法参数
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<?> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[非法参数] {}", e.getMessage());
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), e.getMessage()).traceId(MDC.get("traceId"));
    }

    /**
     * 系统异常（约束文档 9：不返回堆栈信息，只返回 traceId）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<?> handleException(Exception e) {
        log.error("[系统异常]", e);
        return R.fail(ErrorCode.SYSTEM_ERROR.getCode(), "系统异常").traceId(MDC.get("traceId"));
    }
}
