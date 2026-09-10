package com.example.refund.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderManageService {
    private static final Logger log = LoggerFactory.getLogger(OrderManageService.class);

    public String getOrderById(String orderId) {
        return "订单号：" + orderId;
    }

    public String refund(String orderId, String reason) {
        String requestId = UUID.randomUUID().toString();
        log.info("[模拟退款成功] orderId={}, reason={}, requestId={}", orderId, reason, requestId);
        return requestId;
    }
}
