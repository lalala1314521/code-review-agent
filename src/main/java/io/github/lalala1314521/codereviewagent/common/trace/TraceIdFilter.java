package io.github.lalala1314521.codereviewagent.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求级 traceId 注入：X-Request-Id 透传或生成，写入 MDC 贯穿本请求日志。
 *
 * <p>与 DB 记录打通：createPendingRecord 优先取 MDC traceId——
 * webhook 请求的 traceId 即审查记录的 traceId，审查异步链路再取回放 MDC，
 * 实现"webhook → 审查 → LLM → 落库"全链路日志可按同一 traceId 聚合。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = request.getHeader(HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
