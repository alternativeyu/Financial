package com.financial.mockexchange.web;

public record OrderAcceptRequest(long dispatchId, long orderId, String orderNo) {
}
