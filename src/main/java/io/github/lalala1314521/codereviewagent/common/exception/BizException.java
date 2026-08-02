package io.github.lalala1314521.codereviewagent.common.exception;

/**
 * 业务异常：携带业务错误码，由 GlobalExceptionHandler 转成统一响应。
 *
 * <p>用法示例：规则不存在 throw new BizException(404, "rule not found")；
 * 删除 BUILTIN 规则 throw new BizException(400, "builtin rule cannot be deleted")。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
