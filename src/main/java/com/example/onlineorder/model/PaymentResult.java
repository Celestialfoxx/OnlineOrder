package com.example.onlineorder.model;

public record PaymentResult(
        Long orderId,
        boolean success,
        String failureReason
) {
    public static PaymentResult succeeded(Long orderId) {
        return new PaymentResult(orderId, true, null);
    }

    public static PaymentResult failed(Long orderId, String failureReason) {
        return new PaymentResult(orderId, false, failureReason);
    }
}
