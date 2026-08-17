package com.n4monitor.config;

import com.n4monitor.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.error("JSON解析错误: ", e);
        return ApiResponse.error(400, "请求参数解析失败，请检查数据格式");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.error("参数类型不匹配: ", e);
        return ApiResponse.error(400, String.format("请求参数类型不匹配: %s", e.getName()));
    }

    /**
     * Services throw IllegalArgumentException for user-input problems
     * (missing names, duplicate codes, unknown ids). Surfaces them as 400
     * instead of the generic 500 so callers can tell bad input from failures.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("服务器内部错误: ", e);
        return ApiResponse.error(500, "服务器异常: " + e.getMessage());
    }
}
