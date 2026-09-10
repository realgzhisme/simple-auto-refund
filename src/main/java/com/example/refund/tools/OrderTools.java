package com.example.refund.tools;

import com.example.refund.service.OrderManageService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class OrderTools {
    private final OrderManageService orderManageService;

    public OrderTools(OrderManageService orderManageService) {
        this.orderManageService = orderManageService;
    }

    @Tool(name = "apply_refund", description = "根据用户传入的订单信息发起退款")
    public String refund(
            @ToolParam(description = "订单编号，为数字类型") String orderId,
            @ToolParam(description = "商品名称") String name,
            @ToolParam(description = "退款原因") String reason) {
        // 保留原文流程：服务返回模拟编号，工具仍返回自然语言申请结果。
        this.orderManageService.refund(orderId, reason);
        return "已为商品：" + name + ",订单号：" + orderId + "申请退款，退款原因：" + reason;
    }
}
