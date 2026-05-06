package com.example.onlineorder.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    public void sendPaymentSuccessNotification(Long orderId) {
        logger.info("Simulated email notification: payment succeeded for orderId={}", orderId);
        logger.info("Simulated push notification: your order {} has been paid successfully", orderId);
    }

    public void sendPaymentFailureNotification(Long orderId, String reason) {
        logger.info("Simulated email notification: payment failed for orderId={}, reason={}", orderId, reason);
        logger.info("Simulated push notification: payment failed for order {}, reason={}", orderId, reason);
    }
}
