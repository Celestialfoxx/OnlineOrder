package com.example.onlineorder.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentFailedEvent(
        String eventId,
        Long orderId,
        String reason,
        LocalDateTime failedAt
) {
    public static PaymentFailedEvent from(Long orderId, String reason) {
        return new PaymentFailedEvent(
                UUID.randomUUID().toString(),
                orderId,
                reason,
                LocalDateTime.now()
        );
    }
}
