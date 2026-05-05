package com.financial.operator.infra.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调用独立「模拟交易所」进程完成报盘受理（{@code rpt_order_reply} / 委托已报）。未配置 {@code base-url} 时由本进程内 SQL 完成。
 */
@Component
public class MockExchangeClient {

    private final String baseUrl;
    private final String sharedSecret;
    private final RestClient restClient;

    public MockExchangeClient(
            @Value("${financial.mock-exchange.base-url:}") String baseUrl,
            @Value("${financial.mock-exchange.shared-secret:}") String sharedSecret
    ) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret.trim();
        if (isRemoteEnabled()) {
            String root = this.baseUrl.endsWith("/") ? this.baseUrl.substring(0, this.baseUrl.length() - 1) : this.baseUrl;
            this.restClient = RestClient.builder().baseUrl(root).build();
        } else {
            this.restClient = null;
        }
    }

    public boolean isRemoteEnabled() {
        return StringUtils.hasText(baseUrl);
    }

    /**
     * 远程受理报盘；失败抛出 {@link IllegalArgumentException} 以便事务外层处理。
     */
    public void acceptOrderReport(long dispatchId, long orderId, String orderNo) {
        if (restClient == null) {
            throw new IllegalStateException("模拟交易所远程模式未启用");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dispatchId", dispatchId);
        body.put("orderId", orderId);
        body.put("orderNo", orderNo);
        try {
            var spec = restClient.post()
                    .uri("/exchange/v1/order-accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
            if (StringUtils.hasText(sharedSecret)) {
                spec.header("X-Mock-Exchange-Token", sharedSecret);
            }
            spec.retrieve().toBodilessEntity();
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("模拟交易所不可用: " + ex.getMessage());
        }
    }
}
