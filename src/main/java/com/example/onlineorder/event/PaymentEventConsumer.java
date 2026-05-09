package com.example.onlineorder.event;

import com.example.onlineorder.model.PaymentResult;
import com.example.onlineorder.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final OrderEventProducer orderEventProducer;
    private final PaymentService paymentService;

    public PaymentEventConsumer(OrderEventProducer orderEventProducer, PaymentService paymentService) {
        this.orderEventProducer = orderEventProducer;
        this.paymentService = paymentService;
    }

    @KafkaListener(
            topics = OrderEventProducer.ORDER_CREATED_TOPIC,
            groupId = "onlineorder-payment-service"
    )
    public void handleOrderCreated(OrderCreatedEvent event) {
        logger.info("Received OrderCreatedEvent for orderId={}", event.orderId());

        PaymentResult paymentResult = paymentService.processPayment(event);
        if (paymentResult.success()) {
            orderEventProducer.publishPaymentSucceeded(PaymentSucceededEvent.from(paymentResult.orderId()));
        } else {
            orderEventProducer.publishPaymentFailed(
                    PaymentFailedEvent.from(paymentResult.orderId(), paymentResult.failureReason())
            );
        }
    }
}
