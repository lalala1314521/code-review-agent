package io.github.lalala1314521.codereviewagent.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lalala1314521.codereviewagent.common.api.ApiResponse;
import io.github.lalala1314521.codereviewagent.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 管理令牌鉴权拦截器（轻量鉴权，保护"写操作 + 烧钱端点"）。
 *
 * <p>保护范围（非 GET 且路径匹配）：providers/**（改 key 有 SSRF 风险）、rules/**、
 * agents/**、demo/**、reviews/local、reviews/*​/chat（每次调用烧 LLM token）、history/stats/rebuild。
 * GET（查询/SSE）一律放行——读操作不烧钱不改状态。
 *
 * <p>两种模式：ADMIN_TOKEN 未配置 → 开发模式放行 + WARN；已配置 → 强制校验 X-Admin-Token，不匹配返回 401。
 * 属"内网/个人部署"量级鉴权；多用户场景应升级 Spring Security + JWT（保护清单可复用）。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);
    public static final String TOKEN_HEADER = "X-Admin-Token";

    /** 受保护的路径前缀（非 GET 请求） */
    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/api/v1/providers",
            "/api/v1/rules",
            "/api/v1/agents",
            "/api/v1/demo",
            "/api/v1/reviews/local",
            "/api/v1/history/stats/rebuild"
    );

    private final String adminToken;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean warned = new AtomicBoolean(false);

    public AdminAuthInterceptor(@Value("${ADMIN_TOKEN:}") String adminToken, ObjectMapper objectMapper) {
        this.adminToken = adminToken;
        this.objectMapper = objectMapper;
        if (!StringUtils.hasText(adminToken)) {
            log.warn("ADMIN_TOKEN 未配置：管理写接口处于开发模式（全部放行）。生产环境必须通过环境变量配置 ADMIN_TOKEN！");
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!needsAuth(request)) {
            return true;
        }
        // 开发模式：未配置 token 全放行
        if (!StringUtils.hasText(adminToken)) {
            if (warned.compareAndSet(false, true)) {
                log.debug("admin auth skipped (dev mode, ADMIN_TOKEN not set): {} {}", request.getMethod(), request.getRequestURI());
            }
            return true;
        }
        // 强制校验
        String provided = request.getHeader(TOKEN_HEADER);
        if (adminToken.equals(provided)) {
            return true;
        }
        log.warn("admin auth rejected: {} {} from {}", request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
        writeUnauthorized(response);
        return false;
    }

    /**
     * 是否需要鉴权：非 GET 且路径命中保护清单（含 /reviews/*​/chat 特例）。
     */
    private boolean needsAuth(HttpServletRequest request) {
        if ("GET".equalsIgnoreCase(request.getMethod()) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        for (String prefix : PROTECTED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        // /api/v1/reviews/{id}/chat：烧钱端点
        return path.startsWith("/api/v1/reviews/") && path.endsWith("/chat");
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        BizException biz = new BizException(401, "需要管理员令牌（请求头 " + TOKEN_HEADER + "）");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(biz.getCode(), biz.getMessage())));
    }
}
