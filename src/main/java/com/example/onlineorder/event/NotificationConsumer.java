package com.example.onlineorder.event;

import com.example.onlineorder.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;

    public NotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = OrderEventProducer.PAYMENT_SUCCEEDED_TOPIC,
            groupId = "onlineorder-notification-service"
    )
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        logger.info("Received PaymentSucceededEvent for notification, orderId={}", event.orderId());
        notificationService.sendPaymentSuccessNotification(event.orderId());
    }

    @KafkaListener(
            topics = OrderEventProducer.PAYMENT_FAILED_TOPIC,
            groupId = "onlineorder-notification-service"
    )
    public void handlePaymentFailed(PaymentFailedEvent event) {
        logger.info("Received PaymentFailedEvent for notification, orderId={}, reason={}", event.orderId(), event.reason());
        notificationService.sendPaymentFailureNotification(event.orderId(), event.reason());
    }
}
