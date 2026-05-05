package com.financial.mockexchange.web;

import com.financial.mockexchange.service.OrderAcceptanceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ExchangeApiController {

    private final OrderAcceptanceService orderAcceptanceService;
    private final String sharedSecret;

    public ExchangeApiController(
            OrderAcceptanceService orderAcceptanceService,
            @Value("${financial.mock-exchange.shared-secret:}") String sharedSecret
    ) {
        this.orderAcceptanceService = orderAcceptanceService;
        this.sharedSecret = sharedSecret;
    }

    @PostMapping("/exchange/v1/order-accept")
    public Map<String, Object> orderAccept(
            @RequestHeader(value = "X-Mock-Exchange-Token", required = false) String token,
            @RequestBody OrderAcceptRequest body
    ) {
        if (StringUtils.hasText(sharedSecret)) {
            if (!sharedSecret.equals(token)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无效令牌");
            }
        }
        if (body.orderId() <= 0 || body.dispatchId() <= 0 || body.orderNo() == null || body.orderNo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数错误");
        }
        try {
            orderAcceptanceService.acceptOrder(body.dispatchId(), body.orderId(), body.orderNo().trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("code", 0);
        ok.put("message", "accepted");
        return ok;
    }
}
