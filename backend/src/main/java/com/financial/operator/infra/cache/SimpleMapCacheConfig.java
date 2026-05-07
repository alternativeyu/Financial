package com.financial.operator.infra.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地或单机联调：{@code spring.cache.type=simple} 时使用进程内缓存，不依赖 Redis（行情/字典仍可缓存）。
 */
@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "simple")
public class SimpleMapCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cm = new ConcurrentMapCacheManager(
                "marketQuotesPage",
                "marketQuoteDetail",
                "dictItemsByCode");
        cm.setAllowNullValues(false);
        return cm;
    }
}
