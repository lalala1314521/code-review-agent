package io.github.lalala1314521.codereviewagent.common.api;

import java.util.UUID;

/**
 * Management API 统一响应结构（对齐方案设计 12.1）。
 *
 * <pre>{ "code": 0, "message": "ok", "data": {...}, "traceId": "abc-123" }</pre>
 *
 * <p>错误码约定：0 成功；4xx 客户端错误；5xx 服务端错误。
 * 前端 axios 拦截器按 code === 0 解包 data。
 *
 * @param code    业务码（非 HTTP 状态码；HTTP 层恒 200，语义看 code）
 * @param message 人类可读信息
 * @param data    业务数据
 * @param traceId 链路追踪 ID（排障时与日志对照）
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, newTraceId());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, newTraceId());
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
