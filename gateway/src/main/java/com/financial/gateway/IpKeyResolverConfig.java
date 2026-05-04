package com.financial.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@Configuration
public class IpKeyResolverConfig {

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            SocketAddress addr = exchange.getRequest().getRemoteAddress();
            if (addr == null) {
                return Mono.just("unknown");
            }
            if (addr instanceof InetSocketAddress) {
                InetSocketAddress inet = (InetSocketAddress) addr;
                if (inet.getAddress() != null) {
                    return Mono.just(inet.getAddress().getHostAddress());
                }
            }
            return Mono.just(addr.toString());
        };
    }
}
