package com.example.onlineorder.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderCreatedEvent(
        String eventId,
        Long orderId,
        Long customerId,
        Double totalAmount,
        LocalDateTime createdAt
) {
    public static OrderCreatedEvent from(Long orderId, Long customerId, Double totalAmount) {
        return new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                orderId,
                customerId,
                totalAmount,
                LocalDateTime.now()
        );
    }
}
