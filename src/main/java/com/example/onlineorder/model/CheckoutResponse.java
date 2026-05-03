package com.example.onlineorder.model;

import com.example.onlineorder.entity.OrderEntity;
import com.example.onlineorder.entity.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record CheckoutResponse(
        Long orderId,
        OrderStatus status,
        Double totalAmount,
        List<OrderLineItemDto> orderItems,
        LocalDateTime createdAt
) {
    public CheckoutResponse(OrderEntity order, List<OrderLineItemDto> orderItems) {
        this(
                order.id(),
                order.status(),
                order.totalAmount(),
                orderItems,
                order.createdAt()
        );
    }
}
