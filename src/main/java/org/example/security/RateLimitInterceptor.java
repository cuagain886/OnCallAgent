package org.example.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 限流拦截器：在 Controller 之前检查限流
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 各接口的预估 Token 消耗
    private static final Map<String, Integer> ENDPOINT_TOKEN_ESTIMATE = Map.of(
            "/api/chat", 2000,
            "/api/chat_stream", 2000,
            "/api/ai_ops", 8000,
            "/api/knowledge/maintain", 16000
    );

    @Autowired
    private TokenRateLimiter rateLimiter;

    @Autowired
    private SecurityMonitor securityMonitor;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // 仅对 LLM 相关接口限流
        Integer estimatedTokens = ENDPOINT_TOKEN_ESTIMATE.get(path);
        if (estimatedTokens == null) {
            return true;  // 非 LLM 接口不限流
        }

        String clientIp = getClientIp(request);
        String username = (String) request.getAttribute("currentUser");

        // 检查 IP 是否被封禁
        if (securityMonitor.isBanned(clientIp)) {
            sendError(response, 403, "您的 IP 已被临时封禁，请稍后再试");
            return false;
        }

        // 记录调用（用于异常检测）
        securityMonitor.recordCall(clientIp);

        // 限流检查
        TokenRateLimiter.RateLimitResult result = rateLimiter.tryAcquire(clientIp, username, estimatedTokens);
        if (!result.success()) {
            logger.warn("限流拒绝: ip={}, user={}, path={}, reason={}", clientIp, username, path, result.reason());
            sendError(response, 429, result.reason());
            return false;
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    private void sendError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(Map.of(
                "code", status,
                "message", message
        )));
    }
}
