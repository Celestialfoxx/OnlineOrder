package com.example.onlineorder.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentSucceededEvent(
        String eventId,
        Long orderId,
        LocalDateTime paidAt
) {
    public static PaymentSucceededEvent from(Long orderId) {
        return new PaymentSucceededEvent(
                UUID.randomUUID().toString(),
                orderId,
                LocalDateTime.now()
        );
    }
}
