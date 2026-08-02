package io.github.lalala1314521.codereviewagent.common.exception;

import io.github.lalala1314521.codereviewagent.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把异常统一翻译成 ApiResponse，HTTP 层恒 200，语义看 code。
 *
 * <p>为什么不让异常直接抛成 HTTP 500？前端拦截器只需判断 body.code，
 * 不用同时处理 HTTP 状态码 + 业务码两套语义，联调简单。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常（主动抛出，预期内） */
    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException e) {
        log.warn("biz exception code={} message={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /** 参数校验/缺失（客户端错误） */
    @ExceptionHandler({MethodArgumentNotValidException.class, MissingServletRequestParameterException.class,
            IllegalArgumentException.class})
    public ApiResponse<Void> handleBadRequest(Exception e) {
        log.warn("bad request: {}", e.getMessage());
        return ApiResponse.error(400, "参数错误: " + e.getMessage());
    }

    /** 兜底（服务端错误，不暴露内部细节给前端） */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnknown(Exception e) {
        log.error("unexpected error", e);
        return ApiResponse.error(500, "服务内部错误");
    }
}
