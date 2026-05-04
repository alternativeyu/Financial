package com.financial.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * 将客户端 IP 写入 X-Forwarded-For，便于后端 {@code ApiOrderRateLimitFilter} 按真实用户限流。
 */
@Component
public class PreserveClientIpGatewayFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = resolveIp(exchange);
        ServerHttpRequest modified = exchange.getRequest().mutate()
                .header("X-Forwarded-For", ip)
                .build();
        return chain.filter(exchange.mutate().request(modified).build());
    }

    private static String resolveIp(ServerWebExchange exchange) {
        SocketAddress addr = exchange.getRequest().getRemoteAddress();
        if (addr instanceof InetSocketAddress) {
            InetSocketAddress inet = (InetSocketAddress) addr;
            if (inet.getAddress() != null) {
                return inet.getAddress().getHostAddress();
            }
        }
        return addr != null ? addr.toString() : "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
