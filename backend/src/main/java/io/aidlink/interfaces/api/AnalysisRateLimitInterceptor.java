package io.aidlink.interfaces.api;

import io.aidlink.control.application.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Component
public class AnalysisRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final int requestsPerMinute;

    public AnalysisRateLimitInterceptor(
            RateLimiter rateLimiter,
            @Value("${aidlink.rate-limit.analysis-per-minute:60}") int requestsPerMinute
    ) {
        this.rateLimiter = rateLimiter;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {
        String clientKey = hash(request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr());
        if (rateLimiter.tryAcquire(clientKey, requestsPerMinute, Duration.ofMinutes(1))) {
            return true;
        }
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}");
        return false;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
