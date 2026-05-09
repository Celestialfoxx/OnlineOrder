package com.example.onlineorder.event;

import com.example.onlineorder.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPaymentConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderPaymentConsumer.class);

    private final OrderService orderService;

    public OrderPaymentConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(
            topics = OrderEventProducer.PAYMENT_SUCCEEDED_TOPIC,
            groupId = "onlineorder-order-service"
    )
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        logger.info("Received PaymentSucceededEvent for orderId={}", event.orderId());
        orderService.markOrderPaid(event.orderId());
        logger.info("Updated order status to PAID for orderId={}", event.orderId());
    }

    @KafkaListener(
            topics = OrderEventProducer.PAYMENT_FAILED_TOPIC,
            groupId = "onlineorder-order-service"
    )
    public void handlePaymentFailed(PaymentFailedEvent event) {
        logger.info("Received PaymentFailedEvent for orderId={}, reason={}", event.orderId(), event.reason());
        orderService.markOrderFailed(event.orderId());
        logger.info("Updated order status to FAILED for orderId={}", event.orderId());
    }
}
