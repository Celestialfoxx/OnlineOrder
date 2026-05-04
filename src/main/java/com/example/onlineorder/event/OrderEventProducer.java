package com.example.onlineorder.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventProducer {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventProducer.class);

    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String PAYMENT_SUCCEEDED_TOPIC = "payment.succeeded";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        kafkaTemplate.send(ORDER_CREATED_TOPIC, event.orderId().toString(), event);
        logger.info("Published OrderCreatedEvent for orderId={}", event.orderId());
    }

    public void publishPaymentSucceeded(PaymentSucceededEvent event) {
        kafkaTemplate.send(PAYMENT_SUCCEEDED_TOPIC, event.orderId().toString(), event);
        logger.info("Published PaymentSucceededEvent for orderId={}", event.orderId());
    }
}
