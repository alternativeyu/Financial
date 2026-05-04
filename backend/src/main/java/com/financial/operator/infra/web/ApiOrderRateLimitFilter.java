package com.financial.operator.infra.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 简单 Redis 限流：对「下单」POST 按客户端 IP 计数，防止恶意刷接口。
 * Redis 不可用时放行（fail-open），避免本地无 Redis 时阻断联调。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiOrderRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiOrderRateLimitFilter.class);

    private final StringRedisTemplate redis;
    private final int ordersPerMinutePerIp;
    private final String clientIpHeader;

    public ApiOrderRateLimitFilter(
            StringRedisTemplate redis,
            @Value("${financial.ratelimit.orders-per-minute-per-ip:120}") int ordersPerMinutePerIp,
            @Value("${financial.gateway.client-ip-header:X-Forwarded-For}") String clientIpHeader
    ) {
        this.redis = redis;
        this.ordersPerMinutePerIp = Math.max(1, ordersPerMinutePerIp);
        this.clientIpHeader = clientIpHeader;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return !"/api/app/trade/orders".equals(uri) && !"/api/operator/trade/orders".equals(uri);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String ip = resolveClientIp(request);
        String key = "rl:trade:order:1m:" + ip;
        try {
            Long n = redis.opsForValue().increment(key);
            if (n != null && n == 1L) {
                redis.expire(key, Duration.ofMinutes(1));
            }
            if (n != null && n > ordersPerMinutePerIp) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
                return;
            }
        } catch (Exception e) {
            log.warn("Redis 限流跳过: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(clientIpHeader);
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}
